package com.meridian.confirmiq.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the data directory before the DataSource opens the SQLite file,
 * so a fresh checkout or an empty container volume boots without manual steps.
 */
public class DataDirectoryInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String dataDir = event.getEnvironment().resolvePlaceholders("${app.data-dir:./data}");
        try {
            Files.createDirectories(Path.of(dataDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + dataDir, e);
        }
    }
}
