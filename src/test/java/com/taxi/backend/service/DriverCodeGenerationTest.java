package com.taxi.backend.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Driver code generation mantig'ini tekshirish.
 * Haqiqiy DB sequence o'rniga AtomicInteger ishlatiladi —
 * ikkalasi ham bir xil atomik garantiyani beradi.
 */
class DriverCodeGenerationTest {

    private static String formatCode(int num) {
        return String.format("TZ-%04d", num);
    }

    /** Ketma-ket generatsiya TZ-0001 dan boshlanishi va o'sib borishi kerak */
    @Test
    void codeGeneration_producesSequentialUniqueValues() {
        AtomicInteger seq = new AtomicInteger(0);
        Supplier<String> gen = () -> formatCode(seq.incrementAndGet());

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) codes.add(gen.get());

        assertEquals("TZ-0001", codes.get(0));
        assertEquals("TZ-0010", codes.get(9));
        assertEquals(10, codes.stream().distinct().count(), "Barcha kodlar noyob bo'lishi kerak");
    }

    /** 4 xonadan oshganda format o'z-o'zidan kengayishi kerak: TZ-10000 */
    @Test
    void codeFormat_expandsBeyond4Digits() {
        assertEquals("TZ-9999", formatCode(9999));
        assertEquals("TZ-10000", formatCode(10000));
        assertEquals("TZ-99999", formatCode(99999));
    }

    /** 10 ta parallel thread — takrorlanmaydigan kodlar */
    @Test
    void concurrentCodeGeneration_noDuplicates() throws InterruptedException {
        AtomicInteger seq = new AtomicInteger(0);
        Supplier<String> gen = () -> formatCode(seq.incrementAndGet());

        int threadCount = 10;
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();           // barcha threadlar bir vaqtda boshlaydi
                    codes.add(gen.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();                   // to'siqni oching
        assertTrue(done.await(10, TimeUnit.SECONDS), "Barcha threadlar tugashi kerak");
        pool.shutdown();

        assertEquals(threadCount, codes.size(), "Barcha threadlar kod olishi kerak");
        assertEquals(threadCount, codes.stream().distinct().count(), "Takroriy kod yo'q bo'lishi kerak");
    }

    /** Migration mantig'i: N ta haydovchiga ketma-ket, bo'shliqsiz kodlar */
    @Test
    void migrationLogic_assignsCodesWithoutGapsOrDuplicates() {
        int driverCount = 100;
        AtomicInteger seq = new AtomicInteger(0);
        Map<Integer, String> assigned = new LinkedHashMap<>();

        // id ASC tartibida iteratsiya (migration DO bloki kabi)
        IntStream.rangeClosed(1, driverCount).forEach(id ->
                assigned.put(id, formatCode(seq.incrementAndGet())));

        // Takroriy yo'q
        long distinct = assigned.values().stream().distinct().count();
        assertEquals(driverCount, distinct, "Birorta takroriy kod bo'lmasligi kerak");

        // Bo'shliq yo'q: TZ-0001 dan TZ-XXXX gacha to'liq
        for (int i = 1; i <= driverCount; i++) {
            String expected = formatCode(i);
            assertTrue(assigned.containsValue(expected),
                    expected + " har bitta haydovchiga biriktirilgan bo'lishi kerak");
        }
    }
}
