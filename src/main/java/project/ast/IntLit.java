package project.ast;

/**
 * An integer literal expression.
 *
 * @param value the literal value
 */
public record IntLit(long value) implements Expr {}
