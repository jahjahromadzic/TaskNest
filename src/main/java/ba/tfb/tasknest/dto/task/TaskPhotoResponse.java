package ba.tfb.tasknest.dto.task;

import ba.tfb.tasknest.entity.TaskPhoto;

import java.util.UUID;

public record TaskPhotoResponse(
        UUID id,
        String url,
        int width,
        int height
) {
    public static String urlOf(UUID photoId) {
        return "/api/photos/" + photoId;
    }

    public static TaskPhotoResponse from(TaskPhoto photo) {
        return new TaskPhotoResponse(photo.getId(), urlOf(photo.getId()), photo.getWidth(), photo.getHeight());
    }
}
