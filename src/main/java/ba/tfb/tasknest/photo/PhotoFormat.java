package ba.tfb.tasknest.photo;

import java.util.Optional;

public enum PhotoFormat {
    JPEG("jpeg", "image/jpeg", "jpg"),
    PNG("png", "image/png", "png");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String imageIoName;
    private final String contentType;
    private final String extension;

    PhotoFormat(String imageIoName, String contentType, String extension) {
        this.imageIoName = imageIoName;
        this.contentType = contentType;
        this.extension = extension;
    }

    public static Optional<PhotoFormat> detect(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    public String imageIoName() {
        return imageIoName;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
