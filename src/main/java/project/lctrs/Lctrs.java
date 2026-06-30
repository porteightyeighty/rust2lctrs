package project.lctrs;

import java.util.ArrayList;
import java.util.List;

/**
 * An LCTRS as the pair (Σ, R): the terms signature of program-defined symbols and the constrained
 * rewrite rules over them. The theory signature is fixed and built into Cora, so it is not held
 * here.
 */
public class Lctrs {

  List<Symbol> sigma;
  List<Rule> rules;

  /** Creates an empty LCTRS with no signature symbols and no rules. */
  public Lctrs() {
    this.sigma = new ArrayList<>();
    this.rules = new ArrayList<>();
  }

  /**
   * Appends term symbols to the signature, in order, skipping any already declared.
   *
   * @param symbols the term symbols to add
   * @return this LCTRS, for chaining
   */
  public Lctrs appendSymbols(List<Symbol> symbols) {
    for (Symbol s : symbols) {
      if (!sigma.contains(s)) {
        sigma.add(s);
      }
    }
    return this;
  }

  /**
   * Appends a single rewrite rule.
   *
   * @param rule the rule to add
   * @return this LCTRS, for chaining
   */
  public Lctrs appendRule(Rule rule) {
    this.rules.add(rule);
    return this;
  }

  /**
   * Appends rewrite rules, in order.
   *
   * @param rules the rules to add
   * @return this LCTRS, for chaining
   */
  public Lctrs appendRules(List<Rule> rules) {
    this.rules.addAll(rules);
    return this;
  }

  /**
   * Returns an immutable snapshot of the terms signature, in insertion order.
   *
   * @return the term symbols declared by this LCTRS
   */
  public List<Symbol> sigma() {
    return List.copyOf(sigma);
  }

  /**
   * Returns an immutable snapshot of the rules accumulated in this LCTRS.
   *
   * @return the rewrite rules, in insertion order
   */
  public List<Rule> rules() {
    return List.copyOf(rules);
  }
}
