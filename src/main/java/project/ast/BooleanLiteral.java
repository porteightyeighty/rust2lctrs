package project.ast;

/**
 * A boolean literal expression.
 *
 * @param value the literal value
 */
public record BooleanLiteral(boolean value) implements Literal {}
