package project.ast;

import java.util.List;
import java.util.Objects;

public record FunctionCall(Identifier function, List<Expression> args) implements Expression {
  public FunctionCall {
    Objects.requireNonNull(function);
    List.copyOf(args);
  }
}
