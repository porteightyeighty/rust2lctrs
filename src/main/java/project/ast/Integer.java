package project.ast;

/**
 * An integer literal expression.
 *
 * @param value the literal value
 */
public record Integer(long value) implements Literal {
}
