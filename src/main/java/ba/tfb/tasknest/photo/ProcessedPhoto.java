package ba.tfb.tasknest.photo;

public record ProcessedPhoto(
        byte[] bytes,
        PhotoFormat format,
        int width,
        int height
) {
}
