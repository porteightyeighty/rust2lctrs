package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * A function definition: the only top-level {@link Item} in the supported fragment.
 *
 * @param identifier the function name
 * @param parameters the parameter list (may be empty)
 * @param block the function body
 * @param returnType the declared return type (always required in the supported fragment)
 */
public final record FunctionDef(
    Identifier identifier, List<Parameter> parameters, Block block, Type returnType)
    implements Item {
  public FunctionDef {
    Objects.requireNonNull(identifier);
    Objects.requireNonNull(parameters);
    Objects.requireNonNull(block);
    Objects.requireNonNull(returnType);
  }
}
