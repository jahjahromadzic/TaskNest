package ba.tfb.tasknest.photo;

import java.util.Optional;

public interface PhotoStorage {

    void store(String key, byte[] bytes);

    Optional<byte[]> load(String key);

    void delete(String key);
}
