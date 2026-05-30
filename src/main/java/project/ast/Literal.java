package project.ast;

/** A literal constant: either an {@link Integer} or a {@link Boolean} value. */
public sealed interface Literal extends Expression permits Integer, Boolean {}
