package project.ast;

/**
 * A boolean literal expression.
 *
 * @param value the literal value
 */
public record Boolean(boolean value) implements Literal {
}
