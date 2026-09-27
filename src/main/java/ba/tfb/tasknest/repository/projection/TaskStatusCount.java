package ba.tfb.tasknest.repository.projection;

import ba.tfb.tasknest.entity.enums.TaskStatus;

public record TaskStatusCount(
        TaskStatus status,
        long count
) {
}
