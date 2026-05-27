package project.ast;

import java.util.Objects;

public record Parameter(Identifier identifier, Type type) {
  public Parameter {
    Objects.requireNonNull(identifier);
    Objects.requireNonNull(type);
  }
}
