package project.ast;

/** A literal constant: either an {@link IntegerLiteral} or a {@link BooleanLiteral} value. */
public sealed interface Literal extends Expression permits IntegerLiteral, BooleanLiteral {}
