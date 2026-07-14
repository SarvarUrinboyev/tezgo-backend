package com.taxi.backend.service;

import com.taxi.backend.dto.ClickGetInfoRequest;
import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strictly read-only Click SuperApp account lookup.  It deliberately does not
 * depend on a Click secret, Redis payment state, or either payment ledger.
 */
@Service
public class ClickGetInfoService {

    private final DriverRepository driverRepository;
    private final ClickGetInfoProperties properties;
    private final ClickCatalogModePolicy catalogModePolicy;
    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public ClickGetInfoService(DriverRepository driverRepository, ClickGetInfoProperties properties,
                               ClickCatalogModePolicy catalogModePolicy) {
        this.driverRepository = driverRepository;
        this.properties = properties;
        this.catalogModePolicy = catalogModePolicy;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> handle(ClickGetInfoRequest request, String authorization, String remoteAddress) {
        if (!catalogModePolicy.allowsGetInfo()) {
            return response(ClickGetInfoError.CATALOG_DISABLED);
        }
        if (!properties.isEnabled()) {
            return response(ClickGetInfoError.CATALOG_DISABLED);
        }
        if (!properties.isAuthReady()) {
            return response(ClickGetInfoError.AUTH_NOT_CONFIGURED);
        }
        if (request == null || request.getAction() == null || request.getAction() != 0) {
            return response(ClickGetInfoError.INVALID_ACTION);
        }
        if (!"105926".equals(request.getServiceId())) {
            return response(ClickGetInfoError.INVALID_SERVICE_ID);
        }
        String account = normalizeAccount(request.getParams() == null ? null : request.getParams().getAccount());
        if (account == null) {
            return response(ClickGetInfoError.MALFORMED_ACCOUNT);
        }
        if (!basicAuthAccepted(authorization)) {
            return basicAuthConfigured()
                    ? response(ClickGetInfoError.UNAUTHORIZED)
                    : response(ClickGetInfoError.AUTH_NOT_CONFIGURED);
        }
        if (!allowed(remoteAddress, account)) {
            return response(ClickGetInfoError.RATE_LIMITED);
        }

        try {
            Optional<Driver> result = driverRepository.findByDriverCodeWithUser(account);
            if (result.isEmpty()) {
                return response(ClickGetInfoError.ACCOUNT_NOT_FOUND);
            }
            Driver driver = result.get();
            if (driver.getStatus() != DriverStatus.ACTIVE) {
                return response(ClickGetInfoError.ACCOUNT_INACTIVE);
            }
            String fullName = driver.getUser() == null ? null : driver.getUser().getName();
            if (fullName == null || fullName.isBlank()) {
                return response(ClickGetInfoError.ACCOUNT_NOT_FOUND);
            }
            Map<String, Object> response = response(ClickGetInfoError.SUCCESS);
            response.put("params", Map.of("full_name", fullName));
            return response;
        } catch (RuntimeException ex) {
            // Do not log the Authorization value, account, or driver PII.
            return response(ClickGetInfoError.TEMPORARY_ERROR);
        }
    }

    private String normalizeAccount(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return ClickMerchantTransIdType.isCatalogAccount(normalized) ? normalized : null;
    }

    private boolean basicAuthConfigured() {
        return properties.isBasicAuthEnabled()
                && !isBlank(properties.getBasicAuthUsername())
                && !isBlank(properties.getBasicAuthPassword());
    }

    private boolean basicAuthAccepted(String authorization) {
        if (!properties.isBasicAuthEnabled()) {
            return true;
        }
        if (!basicAuthConfigured() || authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(6));
            byte[] expected = (properties.getBasicAuthUsername() + ":" + properties.getBasicAuthPassword())
                    .getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(decoded, expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean allowed(String remoteAddress, String account) {
        int accountLimit = Math.max(1, properties.getRateLimitMaxRequests());
        int sourceLimit = Math.max(accountLimit, properties.getRateLimitMaxSourceRequests());
        long windowMillis = Math.max(1, properties.getRateLimitWindowSeconds()) * 1_000L;
        String source = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        // Click can legitimately use one egress IP for many drivers.  Keep the
        // narrow limit per source+account and a larger source ceiling for broad
        // enumeration control. Both bounded maps evict rather than globally ban.
        long now = Instant.now().toEpochMilli();
        if (!consume("source:" + sha256(source), sourceLimit, windowMillis, now)) {
            return false;
        }
        return consume("account:" + sha256(source + "\u0000" + account), accountLimit, windowMillis, now);
    }

    private boolean consume(String key, int limit, long windowMillis, long now) {
        if (!rateWindows.containsKey(key) && rateWindows.size() >= Math.max(1, properties.getRateLimitMaxEntries())) {
            rateWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMillis);
            if (rateWindows.size() >= Math.max(1, properties.getRateLimitMaxEntries())) {
                rateWindows.entrySet().stream()
                        .min(java.util.Comparator.comparingLong(entry -> entry.getValue().startedAt()))
                        .ifPresent(entry -> rateWindows.remove(entry.getKey(), entry.getValue()));
            }
        }
        RateWindow window = rateWindows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= windowMillis) {
                return new RateWindow(now, 1);
            }
            return new RateWindow(current.startedAt, current.count + 1);
        });
        return window.count <= limit;
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                output.append(String.format(java.util.Locale.ROOT, "%02x", value));
            }
            return output.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, Object> response(ClickGetInfoError error) {
        return ClickGetInfoResponse.error(error);
    }

    private record RateWindow(long startedAt, int count) { }
}
