package project.ast;

import java.util.Objects;

/**
 * A binary expression ({@code left op right}): arithmetic, comparison, or lazy boolean.
 *
 * @param operator the operator
 * @param left the left-hand operand
 * @param right the right-hand operand
 */
public record BinaryOp(Op operator, Expression left, Expression right) implements Expression {
  public BinaryOp {
    Objects.requireNonNull(operator);
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
  }

  /** The set of binary operators supported in the current fragment. */
  public enum Op {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    GT,
    GE,
    LT,
    LE,
    EQ,
    NE,
    AND,
    OR
  }
}
