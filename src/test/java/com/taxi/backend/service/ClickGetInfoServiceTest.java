package com.taxi.backend.service;

import com.taxi.backend.dto.ClickGetInfoRequest;
import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ClickGetInfoServiceTest {

    private DriverRepository driverRepository;
    private ClickGetInfoProperties properties;
    private ClickCatalogModePolicy catalogModePolicy;
    private ClickGetInfoService service;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        properties = new ClickGetInfoProperties();
        ReflectionTestUtils.setField(properties, "enabled", true);
        ReflectionTestUtils.setField(properties, "authReady", true);
        ReflectionTestUtils.setField(properties, "basicAuthEnabled", true);
        ReflectionTestUtils.setField(properties, "basicAuthUsername", "click-readonly");
        ReflectionTestUtils.setField(properties, "basicAuthPassword", "test-password");
        ReflectionTestUtils.setField(properties, "rateLimitMaxRequests", 10);
        ReflectionTestUtils.setField(properties, "rateLimitMaxSourceRequests", 240);
        ReflectionTestUtils.setField(properties, "rateLimitWindowSeconds", 60L);
        ReflectionTestUtils.setField(properties, "rateLimitMaxEntries", 4096);
        catalogModePolicy = new ClickCatalogModePolicy();
        ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", "ON");
        service = new ClickGetInfoService(driverRepository, properties, catalogModePolicy);
    }

    @Test
    void validAccountNormalizesAndReturnsOnlyFullNameWithoutMutation() {
        when(driverRepository.findByDriverCodeWithUser("TZ-0005"))
                .thenReturn(Optional.of(activeDriver("Aliyev Akmal")));

        Map<String, Object> response = service.handle(request(" tz-0005 "), basicAuth(), "10.10.10.10");

        assertEquals(0, response.get("error"));
        assertEquals("Success", response.get("error_note"));
        Map<?, ?> params = (Map<?, ?>) response.get("params");
        assertEquals(Map.of("full_name", "Aliyev Akmal"), params);
        assertEquals(3, response.size(), "no phone, balance, ID, or status may escape");
        verify(driverRepository).findByDriverCodeWithUser("TZ-0005");
        verifyNoMoreInteractions(driverRepository);
    }

    @Test
    void malformedUnknownInactiveWrongServiceAndWrongActionAreRejected() {
        assertEquals(-8, service.handle(request("TZ-X"), basicAuth(), "10.0.0.1").get("error"));

        when(driverRepository.findByDriverCodeWithUser("TZ-9999")).thenReturn(Optional.empty());
        assertEquals(-5, service.handle(request("TZ-9999"), basicAuth(), "10.0.0.2").get("error"));

        when(driverRepository.findByDriverCodeWithUser("TZ-0006"))
                .thenReturn(Optional.of(inactiveDriver()));
        assertEquals(-5, service.handle(request("TZ-0006"), basicAuth(), "10.0.0.3").get("error"));

        ClickGetInfoRequest wrongService = request("TZ-0005");
        wrongService.setServiceId("999");
        assertEquals(-8, service.handle(wrongService, basicAuth(), "10.0.0.4").get("error"));

        ClickGetInfoRequest wrongAction = request("TZ-0005");
        wrongAction.setAction(1);
        assertEquals(-3, service.handle(wrongAction, basicAuth(), "10.0.0.5").get("error"));
    }

    @Test
    void missingParamsAndAccountAreRejectedBeforeLookup() {
        ClickGetInfoRequest noParams = new ClickGetInfoRequest();
        noParams.setAction(0);
        noParams.setServiceId("105926");
        assertEquals(-8, service.handle(noParams, basicAuth(), "10.0.0.1").get("error"));

        ClickGetInfoRequest missingAccount = request(null);
        assertEquals(-8, service.handle(missingAccount, basicAuth(), "10.0.0.1").get("error"));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void basicAuthFailsClosedWhenMissingInvalidOrNotConfigured() {
        assertEquals(-1, service.handle(request("TZ-0005"), null, "10.0.0.1").get("error"));
        assertEquals(-1, service.handle(request("TZ-0005"), "Basic bm90OnRoZQ==", "10.0.0.1").get("error"));

        ReflectionTestUtils.setField(properties, "basicAuthPassword", "");
        Map<String, Object> response = service.handle(request("TZ-0005"), basicAuth(), "10.0.0.1");
        assertEquals("GETINFO_AUTH_NOT_CONFIGURED", response.get("error_note"));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void endpointGateAndRateLimitRemainFailClosed() {
        ReflectionTestUtils.setField(properties, "enabled", false);
        assertEquals("GETINFO_NOT_ENABLED",
                service.handle(request("TZ-0005"), basicAuth(), "10.0.0.1").get("error_note"));

        ReflectionTestUtils.setField(properties, "enabled", true);
        ReflectionTestUtils.setField(properties, "rateLimitMaxRequests", 1);
        when(driverRepository.findByDriverCodeWithUser("TZ-0005"))
                .thenReturn(Optional.of(activeDriver("A")));
        assertEquals(0, service.handle(request("TZ-0005"), basicAuth(), "10.0.0.9").get("error"));
        assertEquals("RATE_LIMITED",
                service.handle(request("TZ-0005"), basicAuth(), "10.0.0.9").get("error_note"));
        verify(driverRepository, times(1)).findByDriverCodeWithUser("TZ-0005");
    }

    @Test
    void drainOffAndUnknownModesFailClosedBeforeAnyDriverLookup() {
        for (String mode : new String[]{"DRAIN", "OFF", "unexpected"}) {
            ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", mode);
            Map<String, Object> response = service.handle(request("TZ-0005"), basicAuth(), "10.0.0.1");
            assertEquals(-1, response.get("error"));
            assertEquals("GETINFO_NOT_ENABLED", response.get("error_note"));
        }
        verifyNoInteractions(driverRepository);
    }

    @Test
    void unconfirmedAuthContractFailsClosedBeforeAnyDriverLookup() {
        ReflectionTestUtils.setField(properties, "authReady", false);

        Map<String, Object> response = service.handle(request("TZ-0005"), basicAuth(), "10.0.0.1");

        assertEquals(-1, response.get("error"));
        assertEquals("GETINFO_AUTH_NOT_CONFIGURED", response.get("error_note"));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void boundedRateWindowEvictsOldSourcesInsteadOfBecomingAGlobalBan() {
        ReflectionTestUtils.setField(properties, "rateLimitMaxEntries", 1);
        when(driverRepository.findByDriverCodeWithUser("TZ-0005"))
                .thenReturn(Optional.of(activeDriver("A")));

        assertEquals(0, service.handle(request("TZ-0005"), basicAuth(), "10.0.0.1").get("error"));
        assertEquals(0, service.handle(request("TZ-0005"), basicAuth(), "10.0.0.2").get("error"));
        verify(driverRepository, times(2)).findByDriverCodeWithUser("TZ-0005");
    }

    @Test
    void oneClickPeerCanResolveDifferentAccountsWithoutUsingThePerAccountBudget() {
        ReflectionTestUtils.setField(properties, "rateLimitMaxRequests", 1);
        ReflectionTestUtils.setField(properties, "rateLimitMaxSourceRequests", 10);
        when(driverRepository.findByDriverCodeWithUser("TZ-0005"))
                .thenReturn(Optional.of(activeDriver("A")));
        when(driverRepository.findByDriverCodeWithUser("TZ-0006"))
                .thenReturn(Optional.of(activeDriver("B")));

        assertEquals(0, service.handle(request("TZ-0005"), basicAuth(), "10.0.0.7").get("error"));
        assertEquals(0, service.handle(request("TZ-0006"), basicAuth(), "10.0.0.7").get("error"));
        assertEquals("RATE_LIMITED",
                service.handle(request("TZ-0005"), basicAuth(), "10.0.0.7").get("error_note"));
    }

    private static ClickGetInfoRequest request(String account) {
        ClickGetInfoRequest request = new ClickGetInfoRequest();
        request.setAction(0);
        request.setServiceId("105926");
        ClickGetInfoRequest.Params params = new ClickGetInfoRequest.Params();
        params.setAccount(account);
        request.setParams(params);
        return request;
    }

    private static String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(
                "click-readonly:test-password".getBytes(StandardCharsets.UTF_8));
    }

    private static Driver activeDriver(String fullName) {
        Driver driver = new Driver();
        driver.setStatus(DriverStatus.ACTIVE);
        User user = new User();
        user.setName(fullName);
        driver.setUser(user);
        return driver;
    }

    private static Driver inactiveDriver() {
        Driver driver = activeDriver("Blocked");
        driver.setStatus(DriverStatus.BLOCKED);
        return driver;
    }
}
