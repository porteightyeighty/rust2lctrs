package project.ast;

import java.util.Objects;

/**
 * A boolean negation expression ({@code !operand}).
 *
 * <p>Lowered to the theory's boolean negation (¬). A negated boolean literal is folded in {@link
 * ExpressionBuilder}, so only non-literal operands reach this node.
 *
 * @param operand the boolean expression being negated
 */
public record UnaryNot(Expression operand) implements UnaryOp {
  public UnaryNot {
    Objects.requireNonNull(operand);
  }

  @Override
  public UnaryNot withOperand(Expression operand) {
    return new UnaryNot(operand);
  }
}
