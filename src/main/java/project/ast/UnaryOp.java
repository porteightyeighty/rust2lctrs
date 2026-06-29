package project.ast;

/**
 * A unary expression: an operator applied to a single operand ({@link UnaryNot}, {@link
 * UnaryMinus}).
 */
public sealed interface UnaryOp extends Expression permits UnaryNot, UnaryMinus {

  /**
   * Returns the single operand the operator is applied to.
   *
   * @return the operand expression
   */
  Expression operand();

  /**
   * Returns a copy of this expression with its operand replaced, preserving the operator.
   *
   * @param operand the operand for the copy
   * @return a unary of the same operator over {@code operand}
   */
  UnaryOp withOperand(Expression operand);
}
