package com.taxi.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deployment-time gate and protected configuration for the public GetInfo endpoint. */
@Component
public class ClickGetInfoProperties {

    @Value("${click.getinfo.enabled:false}")
    private boolean enabled;

    /** Explicit owner acknowledgement of Click's still-external auth contract. */
    @Value("${click.getinfo.auth-ready:false}")
    private boolean authReady;

    @Value("${click.getinfo.basic-auth.enabled:false}")
    private boolean basicAuthEnabled;

    @Value("${click.getinfo.basic-auth.username:}")
    private String basicAuthUsername;

    @Value("${click.getinfo.basic-auth.password:}")
    private String basicAuthPassword;

    @Value("${click.getinfo.rate-limit.max-requests:10}")
    private int rateLimitMaxRequests;

    @Value("${click.getinfo.rate-limit.max-source-requests:240}")
    private int rateLimitMaxSourceRequests;

    @Value("${click.getinfo.rate-limit.window-seconds:60}")
    private long rateLimitWindowSeconds;

    @Value("${click.getinfo.rate-limit.max-entries:4096}")
    private int rateLimitMaxEntries;

    public boolean isEnabled() { return enabled; }
    public boolean isAuthReady() { return authReady; }
    public boolean isBasicAuthEnabled() { return basicAuthEnabled; }
    public String getBasicAuthUsername() { return basicAuthUsername; }
    public String getBasicAuthPassword() { return basicAuthPassword; }
    public int getRateLimitMaxRequests() { return rateLimitMaxRequests; }
    public int getRateLimitMaxSourceRequests() { return rateLimitMaxSourceRequests; }
    public long getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public int getRateLimitMaxEntries() { return rateLimitMaxEntries; }
}
