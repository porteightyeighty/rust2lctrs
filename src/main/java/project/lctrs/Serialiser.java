package project.lctrs;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders an {@link Lctrs} and its constituent parts to Cora's textual input format.
 *
 * <p>The output has two sections: the signature, listing each symbol with its sort profile (e.g.
 * {@code + :: Int -> Int -> Int}), followed by the rules. Binary theory symbols are written infix;
 * everything else is written prefix. This is a pure rendering layer with no side effects, so all
 * methods are static and the class is not instantiable.
 */
public final class Serialiser {

  private Serialiser() {}

  /**
   * Serialises a complete LCTRS: its signature followed by a blank line and then its rules.
   *
   * @param lctrs the system to serialise
   * @return the LCTRS in Cora's input format
   */
  public static String serialise(Lctrs lctrs) {
    StringBuilder out = new StringBuilder();
    out.append(serialiseSignature(lctrs.sigma()));
    out.append(System.lineSeparator());
    out.append(serialiseRules(lctrs.rules()));
    return out.toString();
  }

  /**
   * Serialises the signature, one symbol per line as {@code notation :: s₁ -> … -> sₙ -> result}.
   *
   * @param signature the symbols to declare, in output order
   * @return the signature section, each declaration terminated by a line separator
   */
  private static String serialiseSignature(List<Symbol> signature) {
    StringBuilder out = new StringBuilder();
    for (Symbol currentSymbol : signature) {
      out.append(currentSymbol.notation());
      out.append(" :: ");
      List<Sort> argSorts = currentSymbol.argSorts();
      for (int i = 0; i < argSorts.size(); i++) {
        out.append(argSorts.get(i).notation());
        out.append(" -> ");
      }
      out.append(currentSymbol.resultSort().notation());
      out.append(System.lineSeparator());
    }
    return out.toString();
  }

  /**
   * Serialises a list of rules, one per line.
   *
   * @param rules the rules to serialise, in output order
   * @return the rules section, each rule terminated by a line separator
   */
  private static String serialiseRules(List<Rule> rules) {
    StringBuilder out = new StringBuilder();
    for (Rule currentRule : rules) {
      out.append(serialise(currentRule));
      out.append(System.lineSeparator());
    }
    return out.toString();
  }

  /**
   * Serialises a single rule as {@code lhs -> rhs} followed, if present, by a pipe-separated
   * constraint {@code | φ}.
   *
   * @param rule the rule to serialise
   * @return the rule in Cora's input format
   */
  public static String serialise(Rule rule) {
    StringBuilder out = new StringBuilder();
    out.append(serialiseDelimited(rule.lhs()));
    out.append(" -> ");
    out.append(serialiseDelimited(rule.rhs()));
    if (rule.constraint().isPresent()) {
      out.append(" | ");
      out.append(serialiseDelimited(rule.constraint().get().formula()));
    }
    return out.toString();
  }

  /**
   * Serialises a term: an application, a variable (rendered as its name), or a value.
   *
   * @param term the term to serialise
   * @return the term in Cora's input format
   */
  public static String serialise(Term term) {
    return switch (term) {
      case FnApp f -> serialise(f);
      case VarDecl v -> v.name();
      case Value v -> v.render();
    };
  }

  /**
   * Serialises a function application. Binary theory symbols (arithmetic, comparisons, boolean
   * connectives) are written infix and parenthesised, e.g. {@code (i + 5)}, matching Cora's input
   * syntax. A unary theory symbol over an atomic operand is written as {@code -x} or {@code ¬b};
   * program-point symbols and other prefix symbols are written as {@code f(a, b)}.
   */
  private static String serialise(FnApp f) {
    if (f.symbol() instanceof TheorySymbol && f.args().size() == 2) {
      return "(" + infix(f) + ")";
    }
    if (f.symbol() instanceof TheorySymbol
        && f.args().size() == 1
        && isAtomicOperand(f.args().get(0))) {
      return f.symbol().notation() + serialise(f.args().get(0));
    }
    if (f.args().isEmpty()) {
      return f.symbol().notation();
    }
    return f.symbol().notation()
        + f.args().stream()
            .map(Serialiser::serialiseDelimited)
            .collect(Collectors.joining(", ", "(", ")"));
  }

  /**
   * Serialises a term whose position already delimits it (a rule side, constraint, or function
   * argument), so an infix root drops its own parens: {@code ¬((x > y))} becomes {@code ¬(x > y)}.
   *
   * @param term the term to serialise without redundant outer parens
   * @return the term in Cora's input format
   */
  private static String serialiseDelimited(Term term) {
    if (term instanceof FnApp f && f.symbol() instanceof TheorySymbol && f.args().size() == 2) {
      return infix(f);
    }
    return serialise(term);
  }

  /**
   * Renders a binary theory application as un-parenthesised infix {@code a op b}, the shared form
   * that {@link #serialise(FnApp)} wraps in parens and {@link #serialiseDelimited} emits bare.
   *
   * @param f a binary theory application
   * @return the infix rendering without outer parens
   */
  private static String infix(FnApp f) {
    return serialise(f.args().get(0))
        + " "
        + f.symbol().notation()
        + " "
        + serialise(f.args().get(1));
  }

  /**
   * Whether a unary symbol's operand is a single token, so it can be written without
   * brackets({@code -x}).
   *
   * @param operand the operand of a unary symbol
   * @return {@code true} if the symbol can be written without brackets
   */
  private static boolean isAtomicOperand(Term operand) {
    return switch (operand) {
      case VarDecl v -> true;
      case Value v -> !v.render().startsWith("-");
      case FnApp f -> false;
    };
  }
}
