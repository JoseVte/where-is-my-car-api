package io.josrom.whereismycar.dto;

/** Body of {"error": {...}} responses. `className` is where the error was raised, to help debugging. */
public record ErrorMessage(String message, String className) {
}
