package com.lms.backend.delivery.attempts;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.missing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("delivery-full")
class DeliveryProofStore {
    static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private static final Pattern REFERENCE = Pattern.compile(
        "delivery/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.(png|jpg)");
    private static final Logger LOG = LoggerFactory.getLogger(DeliveryProofStore.class);
    private final Path directory;

    DeliveryProofStore(@Value("${lms.delivery.proof-directory:./data/delivery-proofs}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    String save(byte[] bytes, String type) {
        String extension = extension(bytes, type);
        String reference = "delivery/" + UUID.randomUUID() + "." + extension;
        Path file = path(reference);
        try {
            Files.createDirectories(directory);
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            remove(file);
            throw new UncheckedIOException("Could not save a private Delivery proof.", ex);
        }
        // If the database change fails, remove the file written for it.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) remove(file);
            }
        });
        return reference;
    }

    ProofContent read(String reference) {
        Path file = path(reference);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) {
                throw missing("Proof not found.");
            }
            String contentType = reference.endsWith(".png") ? "image/png" : "image/jpeg";
            return new ProofContent(Files.readAllBytes(file), contentType);
        } catch (IOException ex) {
            throw missing("Proof not found.");
        }
    }

    boolean owns(String reference) {
        return reference != null && REFERENCE.matcher(reference).matches();
    }

    boolean exists(String reference) {
        return owns(reference) && Files.isRegularFile(path(reference), LinkOption.NOFOLLOW_LINKS);
    }

    void deleteAfterCommit(List<String> references) {
        List<Path> files = references.stream().filter(this::owns).map(this::path).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                files.forEach(DeliveryProofStore.this::remove);
            }
        });
    }

    void removeOrphans(Predicate<String> isSaved) {
        if (!Files.isDirectory(directory)) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String reference = "delivery/" + entry.getFileName();
                if (owns(reference) && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)
                        && !isSaved.test(reference)) {
                    remove(entry);
                }
            }
        } catch (IOException ex) {
            LOG.warn("Could not check for orphaned Delivery proofs.", ex);
        }
    }

    private Path path(String reference) {
        if (!owns(reference)) throw missing("Proof not found.");
        return directory.resolve(reference.substring("delivery/".length()));
    }

    private void remove(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            LOG.warn("Could not remove a private Delivery proof.", ex);
        }
    }

    private String extension(byte[] bytes, String type) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("Choose a valid PNG or JPEG image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (format) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    default -> throw bad("Choose a valid PNG or JPEG image.");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) throw bad("Choose a smaller image.");
                var image = reader.read(0);
                if ("SIGNATURE".equals(type)) {
                    boolean ink = false;
                    boolean background = false;
                    for (int y = 0; y < height && !(ink && background); y++) {
                        for (int x = 0; x < width && !(ink && background); x++) {
                            int pixel = image.getRGB(x, y);
                            int brightness = (pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255);
                            if ((pixel >>> 24) < 32 || brightness >= 660) background = true;
                            else if (brightness <= 540) ink = true;
                        }
                    }
                    if (!ink || !background) throw bad("Draw a signature before saving.");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw bad("Choose a valid PNG or JPEG image.");
        }
    }

    record ProofContent(byte[] bytes, String contentType) {}
}
