package ba.tfb.tasknest.domain;

import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static ba.tfb.tasknest.entity.enums.TaskStatus.*;

public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED =
            new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED.put(DRAFT,       EnumSet.of(PUBLISHED, CANCELLED));
        ALLOWED.put(PUBLISHED,   EnumSet.of(ASSIGNED, EXPIRED, CANCELLED, REMOVED));
        ALLOWED.put(ASSIGNED,    EnumSet.of(IN_PROGRESS, CANCELLED, REMOVED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED,   EnumSet.of(CLOSED));
        ALLOWED.put(CLOSED,      EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(CANCELLED,   EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(EXPIRED,     EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(REMOVED,     EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStateMachine() {
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TaskStatus.class))
                .contains(to);
    }

    public static void validateTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTaskTransitionException(from, to);
        }
    }
}