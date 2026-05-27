package project.ast;

import java.util.Objects;

/**
 * A named, typed function parameter.
 *
 * @param identifier the parameter name
 * @param type the declared type
 */
public record Parameter(Identifier identifier, Type type) {
  public Parameter {
    Objects.requireNonNull(identifier);
    Objects.requireNonNull(type);
  }
}
