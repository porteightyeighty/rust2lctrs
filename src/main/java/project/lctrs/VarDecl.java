package project.lctrs;

import java.util.Objects;
import project.ast.Parameter;

/**
 * A variable term of a given sort.
 *
 * @param name the variable's name
 * @param sort the variable's sort
 */
public record VarDecl(String name, Sort sort) implements Term {

  public VarDecl {
    Objects.requireNonNull(name);
    Objects.requireNonNull(sort);
  }

  /**
   * Creates a variable term for a function parameter, taking its name and mapping its type to the
   * corresponding sort.
   *
   * @param parameter the function parameter
   * @return the variable term denoting that parameter
   */
  public static VarDecl of(Parameter parameter) {
    return new VarDecl(parameter.identifier().name(), Sort.of(parameter.type()));
  }
}
