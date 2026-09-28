package ba.tfb.tasknest.repository.projection;

public record RatingSummary(
        Double average,
        Long count
) {
}
