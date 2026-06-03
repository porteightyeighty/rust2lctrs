package project.lctrs;

import java.util.Objects;

/**
 * A variable term of a given sort.
 *
 * @param name the variable's name
 * @param sort the variable's sort
 */
public record Var(String name, Sort sort) implements Term {
  public Var {
    Objects.requireNonNull(name);
    Objects.requireNonNull(sort);
  }
}
