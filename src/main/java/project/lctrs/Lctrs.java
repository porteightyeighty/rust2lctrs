package project.lctrs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An LCTRS as the pair (Σ, R): the terms signature of program-defined symbols and the constrained
 * rewrite rules over them. The theory signature is fixed and built into Cora, so it is not held
 * here.
 */
public class Lctrs {

  private final List<Symbol> sigma;
  private final List<Rule> rules;
  private final Set<Symbol> entries;

  /** Creates an empty LCTRS with no signature symbols and no rules. */
  public Lctrs() {
    this.sigma = new ArrayList<>();
    this.rules = new ArrayList<>();
    this.entries = new HashSet<>();
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
   * Marks symbols as function entry symbols, so that simplification never removes them.
   *
   * @param symbols the entry symbols to record
   * @return this LCTRS
   */
  public Lctrs appendEntrySymbols(Collection<Symbol> symbols) {
    entries.addAll(symbols);
    return this;
  }

  /**
   * Returns an immutable snapshot of the function entry symbols.
   *
   * @return the entry symbols recorded on this LCTRS
   */
  public Set<Symbol> entries() {
    return Set.copyOf(entries);
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
