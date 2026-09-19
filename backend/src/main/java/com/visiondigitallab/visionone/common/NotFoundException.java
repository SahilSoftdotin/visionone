package com.visiondigitallab.visionone.common;

/** The resource does not exist, or does not exist within the caller's organization. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " " + id + " was not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
