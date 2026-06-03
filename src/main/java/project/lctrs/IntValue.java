package project.lctrs;

import java.math.BigInteger;
import java.util.Objects;

/**
 * An integer theory value. Backed by {@link BigInteger} so values are unbounded.
 *
 * @param value the integer this term denotes
 */
public record IntValue(BigInteger value) implements Value {
  public IntValue {
    Objects.requireNonNull(value);
  }

  @Override
  public Sort sort() {
    return Sort.INT;
  }
}
