package ba.tfb.tasknest.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LocalPhotoStorage implements PhotoStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("[a-f0-9-]{36}\\.(jpg|png)");

    private final Path directory;

    public LocalPhotoStorage(@Value("${app.photos.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the photo directory " + this.directory, e);
        }
    }

    @Override
    public void store(String key, byte[] bytes) {
        try {
            Files.write(resolve(key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Storing photo " + key + " failed", e);
        }
    }

    @Override
    public Optional<byte[]> load(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Reading photo " + key + " failed", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Deleting photo {} failed: {}", key, e.getMessage());
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid photo key: " + key);
        }
        return directory.resolve(key);
    }
}
