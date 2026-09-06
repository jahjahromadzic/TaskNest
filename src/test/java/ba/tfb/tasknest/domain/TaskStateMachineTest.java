package ba.tfb.tasknest.domain;

import ba.tfb.tasknest.entity.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static ba.tfb.tasknest.entity.enums.TaskStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskStateMachineTest {

    @Test
    void allowsHappyPathTransitions() {
        assertTrue(TaskStateMachine.canTransition(DRAFT, PUBLISHED));
        assertTrue(TaskStateMachine.canTransition(PUBLISHED, ASSIGNED));
        assertTrue(TaskStateMachine.canTransition(ASSIGNED, IN_PROGRESS));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, COMPLETED));
        assertTrue(TaskStateMachine.canTransition(COMPLETED, CLOSED));
    }

    @Test
    void rejectsSkippingTheAssignmentStep() {
        assertFalse(TaskStateMachine.canTransition(PUBLISHED, COMPLETED));
        assertFalse(TaskStateMachine.canTransition(PUBLISHED, IN_PROGRESS));
        assertFalse(TaskStateMachine.canTransition(DRAFT, ASSIGNED));
    }

    @Test
    void rejectsAnyTransitionOutOfTerminalStates() {
        Set<TaskStatus> terminals = EnumSet.of(CLOSED, CANCELLED, EXPIRED, REMOVED);

        for (TaskStatus terminal : terminals) {
            for (TaskStatus target : TaskStatus.values()) {
                assertFalse(
                        TaskStateMachine.canTransition(terminal, target),
                        "Expected no transition from terminal state " + terminal + " to " + target
                );
            }
        }
    }

    @Test
    void rejectsBackwardTransitions() {
        assertFalse(TaskStateMachine.canTransition(ASSIGNED, PUBLISHED));
        assertFalse(TaskStateMachine.canTransition(COMPLETED, IN_PROGRESS));
        assertFalse(TaskStateMachine.canTransition(IN_PROGRESS, ASSIGNED));
    }

    @Test
    void rejectsSelfTransitions() {
        for (TaskStatus status : TaskStatus.values()) {
            assertFalse(
                    TaskStateMachine.canTransition(status, status),
                    "Expected no self-transition for " + status
            );
        }
    }

    @Test
    void validateThrowsOnInvalidTransition() {
        assertThrows(
                InvalidTaskTransitionException.class,
                () -> TaskStateMachine.validateTransition(PUBLISHED, COMPLETED)
        );
    }

    @Test
    void validatePassesOnValidTransition() {
        assertDoesNotThrow(
                () -> TaskStateMachine.validateTransition(PUBLISHED, ASSIGNED)
        );
    }
}