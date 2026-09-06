package ba.tfb.tasknest.domain;

import ba.tfb.tasknest.entity.enums.TaskStatus;


public class InvalidTaskTransitionException extends RuntimeException {

    public InvalidTaskTransitionException(TaskStatus from, TaskStatus to) {
        super("Invalid task transition: " + from + " -> " + to);
    }
}