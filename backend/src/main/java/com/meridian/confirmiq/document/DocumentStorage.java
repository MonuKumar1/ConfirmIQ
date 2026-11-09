package com.meridian.confirmiq.document;

import com.meridian.confirmiq.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Content-addressed-ish filesystem storage. Keys are relative paths under the storage root,
 * so the store can be swapped for S3/Blob storage behind the same interface.
 */
@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(AppProperties props) throws IOException {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String store(byte[] content, String fileName) {
        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s-%s".formatted(today.getYear(), today.getMonthValue(),
                UUID.randomUUID().toString().substring(0, 8), sanitize(fileName));
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + key, e);
        }
        return key;
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Document content missing for " + key, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
