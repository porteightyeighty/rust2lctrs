package project.lctrs;

/** A theory value: a constant of the underlying theory, either an integer or a boolean. */
public sealed interface Value extends Term permits IntValue, BoolValue {}
