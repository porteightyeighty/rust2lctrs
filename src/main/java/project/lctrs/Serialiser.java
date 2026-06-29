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
   * Serialises a term whose position already delimits it (a rule side, constraint, or function
   * argument), so an infix root drops its own parens: {@code ¬((x > y))} becomes {@code ¬(x > y)}.
   * Operands still self-bracket, so the output cannot reparse differently.
   *
   * @param term the term to serialise without redundant outer parens
   * @return the term in Cora's input format
   */
  private static String serialiseDelimited(Term term) {
    if (term instanceof FnApp f && f.symbol() instanceof TheorySymbol && f.args().size() == 2) {
      return serialise(f.args().get(0))
          + " "
          + f.symbol().notation()
          + " "
          + serialise(f.args().get(1));
    }
    return serialise(term);
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
   * syntax. Program-point symbols and the unary {@code ¬} are written prefix as {@code f(a, b)}.
   */
  private static String serialise(FnApp f) {
    if (f.symbol() instanceof TheorySymbol && f.args().size() == 2) {
      return "("
          + serialise(f.args().get(0))
          + " "
          + f.symbol().notation()
          + " "
          + serialise(f.args().get(1))
          + ")";
    }
    if (f.args().isEmpty()) {
      return f.symbol().notation();
    }
    return f.symbol().notation()
        + f.args().stream()
            .map(Serialiser::serialiseDelimited)
            .collect(Collectors.joining(", ", "(", ")"));
  }
}
