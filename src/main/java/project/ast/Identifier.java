package project.ast;

import java.util.Objects;

/**
 * A simple identifier (variable or function name).
 *
 * @param name the raw text of the identifier
 */
public record Identifier(String name) {
  public Identifier {
    Objects.requireNonNull(name);
  }
}
