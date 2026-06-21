package com.taxi.backend.service;

import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Commit 2 — vaznli matching scoring (proximity 60% + activity 40%) + free_since tie-break.
 */
@ExtendWith(MockitoExtension.class)
class MatchingScoreTest {

    @Mock private DriverLocationCache locationCache;
    @Mock private DriverRepository driverRepository;

    private MatchingService matching;

    @BeforeEach
    void setup() {
        matching = new MatchingService(locationCache, driverRepository);
        ReflectionTestUtils.setField(matching, "weightProximity", 0.60);
        ReflectionTestUtils.setField(matching, "weightActivity", 0.40);
        ReflectionTestUtils.setField(matching, "tieEpsilon", 0.5);
    }

    private MatchingService.MatchedDriver d(long id, double distanceKm, double activity, LocalDateTime freeSince) {
        return new MatchingService.MatchedDriver(id, "D" + id, "Model", "0" + id,
                41.3, 69.6, distanceKm, 3.0, 5.0, "EKONOM,DAMAS,BIZNES", null, activity, freeSince);
    }

    @Test
    @DisplayName("Yaqin past-aktivlikli haydovchi uzoq yuqori-aktivlikli haydovchini yutadi (proximity hal qiladi)")
    void closeLowActivity_beatsFarHighActivity() {
        // A: yaqin (0.5km), aktivlik 0 -> prox 90, score 0.6*90 = 54
        // B: uzoq (4.5km), aktivlik 10 -> prox 10, actNorm 100, score 0.6*10+0.4*100 = 46
        var a = d(1L, 0.5, 0.0, null);
        var b = d(2L, 4.5, 10.0, null);

        List<MatchingService.MatchedDriver> ranked = matching.rankByScore(List.of(b, a), 5.0);

        assertEquals(1L, ranked.get(0).driverId(), "yaqin past-aktivlikli A g'olib");
    }

    @Test
    @DisplayName("Uzoq yuqori-aktivlikli haydovchi yaqinroq past-aktivlikli haydovchini yutadi (activity hal qiladi)")
    void farHighActivity_beatsCloserLowActivity() {
        // A: 2km, aktivlik 0 -> prox 60, score 36
        // B: 4km, aktivlik 10 -> prox 20, actNorm 100, score 0.6*20+0.4*100 = 52
        var a = d(1L, 2.0, 0.0, null);
        var b = d(2L, 4.0, 10.0, null);

        List<MatchingService.MatchedDriver> ranked = matching.rankByScore(List.of(a, b), 5.0);

        assertEquals(2L, ranked.get(0).driverId(), "yuqori-aktivlikli B g'olib");
    }

    @Test
    @DisplayName("Epsilon ichida tenglik -> eng erta free_since (uzoq kutgan) g'olib")
    void tieWithinEpsilon_earliestFreeSinceWins() {
        // Bir xil masofa + bir xil aktivlik -> bir xil score -> tenglik. free_since hal qiladi.
        var early = d(1L, 2.0, 5.0, LocalDateTime.now().minusSeconds(120)); // uzoqroq kutgan
        var late = d(2L, 2.0, 5.0, LocalDateTime.now().minusSeconds(10));

        List<MatchingService.MatchedDriver> ranked = matching.rankByScore(List.of(late, early), 5.0);

        assertEquals(1L, ranked.get(0).driverId(), "eng erta bo'shagani (early) g'olib");
    }
}
