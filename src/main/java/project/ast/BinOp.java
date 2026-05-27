package project.ast;

import java.util.Objects;

/**
 * A binary arithmetic expression ({@code left op right}).
 *
 * @param operator the arithmetic operator
 * @param left the left-hand operand
 * @param right the right-hand operand
 */
public record BinOp(Op operator, Expr left, Expr right) implements Expr {
  public BinOp {
    Objects.requireNonNull(operator);
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
  }

  /** The set of arithmetic operators supported in the current fragment. */
  public enum Op {
    ADD,
    SUB,
    MUL,
    DIV
  }
}
