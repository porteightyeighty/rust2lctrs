package project.ast;

import java.math.BigInteger;
import java.util.Objects;

/**
 * An integer literal expression.
 *
 * @param value the literal value
 */
public record IntegerLiteral(BigInteger value) implements Literal {
  public IntegerLiteral {
    Objects.requireNonNull(value);
  }
}
