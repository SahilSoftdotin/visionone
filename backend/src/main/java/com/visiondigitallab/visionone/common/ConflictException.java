package com.visiondigitallab.visionone.common;

/**
 * The request is well-formed but the resource is in a state that refuses it - a shared report that
 * someone tries to regenerate, for instance.
 *
 * <p>Its own type rather than {@link IllegalStateException}, because the JDK and Spring throw that
 * for genuine bugs too. Mapping every IllegalStateException to 409 would turn a programming error
 * into a polite "conflict" that nobody investigates.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
