package com.taxi.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Payme va Click to'lov tizimlari bilan ishlash servisi.
 *
 * Payme: JSON-RPC 2.0 callback (Basic auth: Paycom:{secretKey})
 * Click: Form-data prepare/complete callback (MD5 sign)
 *
 * Barcha to'lov orderlari Redis da 24 soat saqlanadi.
 * Muvaffaqiyatli to'lovda haydovchi balansi to'ldiriladi.
 */
@Service
public class PaymentService {

    private static final Logger log = Logger.getLogger(PaymentService.class.getName());

    @Value("${payme.merchant-id}")
    private String paymeMerchantId;

    @Value("${payme.secret-key}")
    private String paymeSecretKey;

    @Value("${payme.checkout-url}")
    private String paymeCheckoutUrl;

    @Value("${click.merchant-id}")
    private String clickMerchantId;

    @Value("${click.service-id}")
    private String clickServiceId;

    @Value("${click.secret-key}")
    private String clickSecretKey;

    @Value("${click.checkout-url}")
    private String clickCheckoutUrl;

    @Value("${app.payment.return-url}")
    private String returnUrl;

    private final StringRedisTemplate redis;
    private final DriverRepository driverRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Redis ishlamasa, orderlarni in-memory saqlash (24 soat emas, restart gacha)
    private final java.util.concurrent.ConcurrentHashMap<String, String> orderCache
            = new java.util.concurrent.ConcurrentHashMap<>();

    public PaymentService(StringRedisTemplate redis,
                          DriverRepository driverRepository,
                          TransactionRepository transactionRepository) {
        this.redis = redis;
        this.driverRepository = driverRepository;
        this.transactionRepository = transactionRepository;
    }

    // ─────────────────────────────────────────────
    // ORDER YARATISH
    // ─────────────────────────────────────────────

    /**
     * To'lov orderi yaratish va Payme/Click URL'larini qaytarish.
     * @param driverId — balansi to'ldiriladigan haydovchi
     * @param amount   — tiyinda (UZS * 100)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(Long driverId, Long amount) throws Exception {
        String orderId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("driverId", driverId);
        order.put("amount", amount);
        order.put("status", "CREATED");
        order.put("createdAt", System.currentTimeMillis());

        // Redis da 24 soat saqlash (agar Redis yo'q bo'lsa — in-memory)
        String orderJson = objectMapper.writeValueAsString(order);
        orderCache.put("payment:order:" + orderId, orderJson);
        try {
            redis.opsForValue().set("payment:order:" + orderId, orderJson, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warning("Redis order save xato (in-memory fallback): " + e.getMessage());
        }

        // ── Payme URL ──
        // Format: m={merchantId};ac.order_id={orderId};a={amount_tiyin};c={returnUrl}
        String paymeParam = String.format("m=%s;ac.order_id=%s;a=%d;c=%s",
                paymeMerchantId, orderId, amount, returnUrl);
        String paymeEncoded = Base64.getEncoder()
                .encodeToString(paymeParam.getBytes(StandardCharsets.UTF_8));
        String paymeUrl = paymeCheckoutUrl + "/" + paymeEncoded;

        // ── Click URL ──
        // Click UZS qabul qiladi (tiyin emas)
        long amountUzs = amount / 100;
        String clickUrl = String.format(
                "%s?service_id=%s&merchant_id=%s&amount=%d&transaction_param=%s&return_url=%s",
                clickCheckoutUrl, clickServiceId, clickMerchantId, amountUzs, orderId, returnUrl);

        return Map.of(
                "orderId", orderId,
                "amount", amount,
                "amountUzs", amountUzs,
                "paymeUrl", paymeUrl,
                "clickUrl", clickUrl
        );
    }

    // ─────────────────────────────────────────────
    // PAYME JSON-RPC CALLBACK
    // ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> handlePayme(String authHeader, Map<String, Object> body) {
        if (!verifyPaymeAuth(authHeader)) {
            return paymeError(-32504, "Avtorizatsiya xatosi", body.get("id"));
        }

        String method = (String) body.get("method");
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", new HashMap<>());
        Object reqId = body.get("id");

        return switch (method != null ? method : "") {
            case "CheckPerformTransaction"  -> paymeCheckPerform(params, reqId);
            case "CreateTransaction"        -> paymeCreateTransaction(params, reqId);
            case "PerformTransaction"       -> paymePerformTransaction(params, reqId);
            case "CancelTransaction"        -> paymeCancelTransaction(params, reqId);
            case "CheckTransaction"         -> paymeCheckTransaction(params, reqId);
            default -> paymeError(-32601, "Method not found", reqId);
        };
    }

    private boolean verifyPaymeAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String expected = "Paycom:" + paymeSecretKey;
            // Timing-safe comparison — timing attack himoyasi
            return MessageDigest.isEqual(
                decoded.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> paymeCheckPerform(Map<String, Object> params, Object reqId) {
        String orderId = extractOrderId(params);
        if (orderId == null) return paymeError(-31050, "Order topilmadi", reqId);
        Map<String, Object> order = getOrder(orderId);
        if (order == null) return paymeError(-31050, "Order topilmadi", reqId);

        // XAVFSIZLIK: Payme yuborgan summa order summasi bilan mos kelishini tekshirish
        Object amountObj = params.get("amount");
        if (amountObj instanceof Number) {
            long paymeAmount = ((Number) amountObj).longValue();
            long orderAmount = toLong(order.get("amount"));
            if (paymeAmount != orderAmount) {
                return paymeError(-31001, "Summa mos kelmaydi", reqId);
            }
        }

        return Map.of("id", reqId, "result", Map.of("allow", true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paymeCreateTransaction(Map<String, Object> params, Object reqId) {
        String orderId = extractOrderId(params);
        if (orderId == null) return paymeError(-31050, "Order topilmadi", reqId);
        Map<String, Object> order = getOrder(orderId);
        if (order == null) return paymeError(-31050, "Order topilmadi", reqId);

        String txId = (String) params.get("id");
        long now = System.currentTimeMillis();

        try {
            order.put("paymeTransactionId", txId);
            order.put("paymeCreateTime", now);
            order.put("paymeState", 1);
            saveOrder(orderId, order);
        } catch (Exception e) {
            log.warning("Payme createTx error: " + e.getMessage());
        }

        return Map.of("id", reqId, "result", Map.of(
                "create_time", now,
                "transaction", orderId,
                "state", 1
        ));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> paymePerformTransaction(Map<String, Object> params, Object reqId) {
        String paymeId = (String) params.get("id");
        String orderId = findOrderByPaymeId(paymeId);
        if (orderId == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        Map<String, Object> order = getOrder(orderId);
        if (order == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        long performTime = System.currentTimeMillis();
        String currentStatus = (String) order.get("status");

        if ("PAID".equals(currentStatus)) {
            // Allaqachon to'langan — duplicate callback
            performTime = toLong(order.getOrDefault("paymePerformTime", System.currentTimeMillis()));
        } else {
            // Atomik status o'zgartirish — Redis SETNX bilan race condition himoyasi
            String lockKey = "payment:lock:" + orderId;
            Boolean acquired = false;
            try {
                acquired = redis.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warning("Redis lock xato: " + e.getMessage());
            }

            if (acquired == null || !acquired) {
                // Boshqa callback allaqachon qayta ishlamoqda
                // Order'ni qayta o'qib, natijani qaytaramiz
                order = getOrder(orderId);
                if (order != null && "PAID".equals(order.get("status"))) {
                    performTime = toLong(order.getOrDefault("paymePerformTime", System.currentTimeMillis()));
                    return Map.of("id", reqId, "result", Map.of(
                        "perform_time", performTime, "transaction", orderId, "state", 2));
                }
                return paymeError(-31008, "Transaction qayta ishlanmoqda", reqId);
            }

            try {
                performTime = System.currentTimeMillis();
                order.put("status", "PAID");
                order.put("paymePerformTime", performTime);
                order.put("paymeState", 2);
                saveOrder(orderId, order);

                Long driverId = toLong(order.get("driverId"));
                Long amount = toLong(order.get("amount"));

                // Summa validatsiyasi
                if (amount <= 0 || amount > 100_000_000_00L) { // max 1 mlrd so'm
                    log.warning("Payment: noto'g'ri summa: " + amount);
                    return paymeError(-31001, "Noto'g'ri summa", reqId);
                }

                creditDriverBalance(driverId, amount, "Payme to'lovi #" + orderId);
            } catch (Exception e) {
                log.warning("Payme performTx error: " + e.getMessage());
            } finally {
                try { redis.delete(lockKey); } catch (Exception ignored) {}
            }
        }

        return Map.of("id", reqId, "result", Map.of(
                "perform_time", performTime,
                "transaction", orderId,
                "state", 2
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paymeCancelTransaction(Map<String, Object> params, Object reqId) {
        String paymeId = (String) params.get("id");
        String orderId = findOrderByPaymeId(paymeId);
        if (orderId == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        Map<String, Object> order = getOrder(orderId);
        if (order == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        long cancelTime = System.currentTimeMillis();
        int reason = params.get("reason") instanceof Number ? ((Number) params.get("reason")).intValue() : 1;
        int state = "PAID".equals(order.get("status")) ? -2 : -1;

        try {
            order.put("status", "CANCELLED");
            order.put("paymeCancelTime", cancelTime);
            order.put("paymeCancelReason", reason);
            order.put("paymeState", state);
            saveOrder(orderId, order);
        } catch (Exception e) {
            log.warning("Payme cancelTx error: " + e.getMessage());
        }

        return Map.of("id", reqId, "result", Map.of(
                "cancel_time", cancelTime,
                "transaction", orderId,
                "state", state
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paymeCheckTransaction(Map<String, Object> params, Object reqId) {
        String paymeId = (String) params.get("id");
        String orderId = findOrderByPaymeId(paymeId);
        if (orderId == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        Map<String, Object> order = getOrder(orderId);
        if (order == null) return paymeError(-31003, "Transaction topilmadi", reqId);

        int state = ((Number) order.getOrDefault("paymeState", 1)).intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("create_time",  order.getOrDefault("paymeCreateTime", 0L));
        result.put("perform_time", order.getOrDefault("paymePerformTime", 0L));
        result.put("cancel_time",  order.getOrDefault("paymeCancelTime", 0L));
        result.put("transaction",  orderId);
        result.put("state",        state);
        result.put("reason",       order.get("paymeCancelReason"));
        return Map.of("id", reqId, "result", result);
    }

    // ─────────────────────────────────────────────
    // CLICK CALLBACKS
    // ─────────────────────────────────────────────

    public Map<String, Object> handleClickPrepare(Map<String, String> params) {
        String orderId      = params.get("merchant_trans_id");
        String clickTransId = params.get("click_trans_id");
        String signTime     = params.get("sign_time");
        String signString   = params.get("sign_string");

        // MD5 sign: click_trans_id + service_id + secret + merchant_trans_id + error + sign_time
        String mySign = md5(clickTransId + clickServiceId + clickSecretKey + orderId + "0" + signTime);
        if (!MessageDigest.isEqual(mySign.getBytes(StandardCharsets.UTF_8),
                                   signString.getBytes(StandardCharsets.UTF_8))) {
            return Map.of("error", -1, "error_note", "Sign tekshiruvi xato");
        }

        if (getOrder(orderId) == null) {
            return Map.of("error", -5, "error_note", "Order topilmadi");
        }

        return Map.of(
                "click_trans_id",      clickTransId,
                "merchant_trans_id",   orderId,
                "merchant_prepare_id", orderId,
                "error",               0,
                "error_note",          "Success"
        );
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> handleClickComplete(Map<String, String> params) {
        String orderId      = params.get("merchant_trans_id");
        String clickTransId = params.get("click_trans_id");
        String signTime     = params.get("sign_time");
        String signString   = params.get("sign_string");
        String error        = params.getOrDefault("error", "0");

        // MD5 sign: click_trans_id + service_id + secret + merchant_trans_id + merchant_prepare_id + error + sign_time
        String mySign = md5(clickTransId + clickServiceId + clickSecretKey + orderId + orderId + error + signTime);
        if (!MessageDigest.isEqual(mySign.getBytes(StandardCharsets.UTF_8),
                                   signString.getBytes(StandardCharsets.UTF_8))) {
            return Map.of("error", -1, "error_note", "Sign tekshiruvi xato");
        }

        if (!"0".equals(error)) {
            return Map.of("error", 0, "error_note", "Cancelled by user");
        }

        Map<String, Object> order = getOrder(orderId);
        if (order == null) {
            return Map.of("error", -5, "error_note", "Order topilmadi");
        }

        if (!"PAID".equals(order.get("status"))) {
            // Atomik lock — race condition himoyasi
            String lockKey = "payment:lock:" + orderId;
            Boolean acquired = false;
            try {
                acquired = redis.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warning("Redis lock xato: " + e.getMessage());
            }

            if (acquired == null || !acquired) {
                return Map.of("error", 0, "error_note", "Already processing");
            }

            try {
                Long driverId = toLong(order.get("driverId"));
                Long amount = toLong(order.get("amount"));

                // Summa validatsiyasi
                if (amount <= 0 || amount > 100_000_000_00L) {
                    return Map.of("error", -3, "error_note", "Noto'g'ri summa");
                }

                creditDriverBalance(driverId, amount, "Click to'lovi");

                order.put("status", "PAID");
                order.put("clickTransId", clickTransId);
                saveOrder(orderId, order);
            } catch (Exception e) {
                log.warning("Click complete error: " + e.getMessage());
            } finally {
                try { redis.delete(lockKey); } catch (Exception ignored) {}
            }
        }

        return Map.of(
                "click_trans_id",     clickTransId,
                "merchant_trans_id",  orderId,
                "merchant_confirm_id", orderId,
                "error",              0,
                "error_note",         "Success"
        );
    }

    // ─────────────────────────────────────────────
    // BALANCE CREDITING
    // ─────────────────────────────────────────────

    /** Atomic balance credit — race condition himoyasi */
    @Transactional
    public void creditDriverBalance(Long driverId, Long amount, String description) {
        driverRepository.findById(driverId).ifPresent(driver -> {
            // Atomic DB update — concurrent callback'lar xavfsiz
            driverRepository.addToBalance(driver.getId(), amount);

            // Atomik update'dan keyin yangilangan balansni o'qish
            driverRepository.flush();
            Driver updated = driverRepository.findById(driverId).orElse(driver);
            long balanceAfter = updated.getBalance();
            long balanceBefore = balanceAfter - amount;

            Transaction tx = new Transaction();
            tx.setDriver(driver);
            tx.setType(TransactionType.TOPUP);
            tx.setAmount(amount);
            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(balanceAfter);
            tx.setDescription(description);
            transactionRepository.save(tx);
            log.info("Balance credited: driverId=" + driverId + " +" + amount + " tiyin");
        });
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrder(String orderId) {
        String key = "payment:order:" + orderId;
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) return objectMapper.readValue(json, Map.class);
        } catch (Exception ignored) { }
        // In-memory fallback
        String cached = orderCache.get(key);
        if (cached == null) return null;
        try { return objectMapper.readValue(cached, Map.class); } catch (Exception e) { return null; }
    }

    private void saveOrder(String orderId, Map<String, Object> order) throws Exception {
        String key = "payment:order:" + orderId;
        String json = objectMapper.writeValueAsString(order);
        orderCache.put(key, json);
        try {
            redis.opsForValue().set(key, json, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warning("Redis saveOrder xato (in-memory fallback): " + e.getMessage());
        }
    }

    /** Payme transaction ID bo'yicha orderId topish */
    @SuppressWarnings("unchecked")
    private String findOrderByPaymeId(String paymeId) {
        if (paymeId == null) return null;
        String txKey = "payment:payme-tx:" + paymeId;

        // Redis dan qidirish — SCAN ishlatamiz (keys() emas, Redis bloklanmaydi)
        try {
            String cached = redis.opsForValue().get(txKey);
            if (cached != null) return cached;
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match("payment:order:*").count(100).build();
            try (var cursor = redis.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String oid = key.replace("payment:order:", "");
                    Map<String, Object> order = getOrder(oid);
                    if (order != null && paymeId.equals(order.get("paymeTransactionId"))) {
                        redis.opsForValue().set(txKey, oid, 24, TimeUnit.HOURS);
                        return oid;
                    }
                }
            }
        } catch (Exception ignored) { }

        // In-memory fallback
        for (String key : orderCache.keySet()) {
            if (!key.startsWith("payment:order:")) continue;
            String oid = key.replace("payment:order:", "");
            Map<String, Object> order = getOrder(oid);
            if (order != null && paymeId.equals(order.get("paymeTransactionId"))) {
                orderCache.put(txKey, oid);
                return oid;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractOrderId(Map<String, Object> params) {
        Object account = params.get("account");
        if (account instanceof Map) {
            Object orderId = ((Map<?, ?>) account).get("order_id");
            return orderId != null ? orderId.toString() : null;
        }
        return null;
    }

    private Map<String, Object> paymeError(int code, String message, Object id) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", Map.of("ru", message, "uz", message, "en", message));
        return Map.of("id", id != null ? id : 0, "error", error);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }
}
