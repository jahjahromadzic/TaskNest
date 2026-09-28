package ba.tfb.tasknest.repository.projection;

public record ClientTaskStats(
        Long posted,
        Long hires,
        Long completed,
        Long cancelled
) {
}
