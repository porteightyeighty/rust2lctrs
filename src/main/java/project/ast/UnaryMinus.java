package project.ast;

import java.util.Objects;

/**
 * An arithmetic negation expression ({@code -operand}).
 *
 * <p>A negated integer literal is folded to a single negative {@link IntegerLiteral} in {@link
 * ExpressionBuilder}, so only non-literal operands reach this node.
 *
 * @param operand the integer expression being negated
 */
public record UnaryMinus(Expression operand) implements UnaryOp {
  public UnaryMinus {
    Objects.requireNonNull(operand);
  }

  @Override
  public UnaryMinus withOperand(Expression operand) {
    return new UnaryMinus(operand);
  }
}
