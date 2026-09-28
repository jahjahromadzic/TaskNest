package ba.tfb.tasknest.dto.admin;

public record AdminStatsResponse(
        long users,
        long suspendedUsers,
        long taskers,
        long unverifiedTaskers,
        long openTasks,
        long removedTasks
) {
}
