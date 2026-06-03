package project.lctrs;

/**
 * A boolean theory value, {@code true} or {@code false}.
 *
 * @param value the boolean this term denotes
 */
public record BoolValue(boolean value) implements Value {
  @Override
  public Sort sort() {
    return Sort.BOOL;
  }
}
