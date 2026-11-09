package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.document.Document.Source;
import com.meridian.confirmiq.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Polls a drop folder (simulating an email/SFTP gateway) for client responses.
 * Processed files move to {@code processed/}; files that fail move to {@code failed/}.
 */
@Slf4j
@Component
public class InboxWatcher {

    private final InboundService inbound;
    private final Path inbox;

    public InboxWatcher(InboundService inbound, AppProperties props) throws IOException {
        this.inbound = inbound;
        this.inbox = Path.of(props.inbox().dir()).toAbsolutePath().normalize();
        Files.createDirectories(inbox.resolve("processed"));
        Files.createDirectories(inbox.resolve("failed"));
    }

    @Scheduled(initialDelay = 15_000, fixedDelayString = "${app.inbox.poll-interval-ms}")
    public void poll() {
        try (Stream<Path> files = Files.list(inbox)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .forEach(this::handle);
        } catch (IOException e) {
            log.warn("Inbox scan failed: {}", e.getMessage());
        }
    }

    private void handle(Path file) {
        String name = file.getFileName().toString();
        try {
            var result = inbound.process(Files.readAllBytes(file), name, Source.INBOX, CurrentUser.SYSTEM, null);
            move(file, "processed");
            log.info("Inbox file {} processed: {}", name, result.status());
        } catch (Exception e) {
            log.warn("Inbox file {} failed: {}", name, e.getMessage());
            move(file, "failed");
        }
    }

    private void move(Path file, String folder) {
        try {
            Files.move(file, inbox.resolve(folder).resolve(System.currentTimeMillis() + "-" + file.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not move {}: {}", file, e.getMessage());
        }
    }

    public Path directory() {
        return inbox;
    }
}
