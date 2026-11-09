package com.meridian.confirmiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String dataDir,
        String storageDir,
        Inbox inbox,
        Jwt jwt,
        Cors cors,
        Seed seed,
        Chaser chaser,
        Bank bank) {

    public record Inbox(String dir, long pollIntervalMs) {}

    public record Jwt(String secret, long ttlMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Seed(boolean enabled) {}

    public record Chaser(long pollIntervalMs, int firstChaseAfterHours, int intervalHours, int escalateAfter) {}

    public record Bank(String name, String shortName, String address, String lei, String bic, String operationsEmail) {}
}
