package project.ast;

/**
 * An integer literal expression.
 *
 * @param value the literal value
 */
public record IntegerLiteral(long value) implements Expression {}
