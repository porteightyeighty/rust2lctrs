package project.lctrs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-translation simplification: removes forwarding rules {@code f(x₁, …, xₙ) -> g(x₁, …, xₙ)}
 * and redirects every occurrence of {@code f} to {@code g}. The statement-by-statement translation
 * leaves such rules behind (cf. Fuhs, Kop &amp; Nishida (2017).
 *
 * <p>A rule is a candidate only when its left-hand side is a symbol applied to distinct variables,
 * that symbol heads no other rule and occurs in no other left-hand side, and the symbol is not
 * protected by the caller (e.g. function entry symbols).
 */
public final class Simplifier {

  private static final Logger LOG = LoggerFactory.getLogger(Simplifier.class);

  private Simplifier() {}

  /**
   * Removes the forwarding rules of an LCTRS, redirecting occurrences of each removed symbol to its
   * forwarding target (transitively, so a chain {@code u₁ -> u₂ -> u₃} collapses onto {@code u₃})
   * and dropping the removed symbols from the terms signature.
   *
   * @param lctrs the LCTRS to simplify
   * @param keep symbols that must never be removed, such as function entry symbols
   * @return a new, simplified LCTRS, or {@code lctrs} itself when nothing is removable
   */
  public static Lctrs removeForwardingRules(Lctrs lctrs, Set<Symbol> keep) {
    List<Rule> rules = lctrs.rules();
    Map<Symbol, Integer> lhsUses = new HashMap<>();
    for (Rule r : rules) {
      countSymbols(r.lhs(), lhsUses);
    }

    Map<Symbol, Symbol> forward = new LinkedHashMap<>();
    for (Rule r : rules) {
      if (isForwarding(r)) {
        Symbol f = ((FnApp) r.lhs()).symbol();
        if (!keep.contains(f) && lhsUses.get(f) == 1) {
          forward.put(f, ((FnApp) r.rhs()).symbol());
        }
      }
    }
    forward.keySet().removeAll(cyclic(forward));
    if (forward.isEmpty()) {
      return lctrs;
    }
    LOG.debug("Removing {} forwarding rule(s) for {}", forward.size(), forward.keySet());

    List<Rule> kept = new ArrayList<>();
    for (Rule r : rules) {
      if (r.lhs() instanceof FnApp f && forward.containsKey(f.symbol())) {
        continue; // the forwarding rule itself
      }
      kept.add(new Rule(r.lhs(), redirect(r.rhs(), forward), r.constraint()));
    }
    List<Symbol> sigma = lctrs.sigma().stream().filter(s -> !forward.containsKey(s)).toList();
    return new Lctrs().appendSymbols(sigma).appendRules(kept);
  }

  /**
   * Reports whether a rule is a pure forward: unconstrained, both sides function applications of
   * different symbols, the left-hand side's arguments distinct variables, and the right-hand side
   * applied to exactly those variables in the same order.
   *
   * @param r the rule to classify
   * @return {@code true} if the rule forwards its configuration unchanged
   */
  private static boolean isForwarding(Rule r) {
    return r.constraint().isEmpty()
        && r.lhs() instanceof FnApp f
        && r.rhs() instanceof FnApp g
        && !f.symbol().equals(g.symbol())
        && f.args().equals(g.args())
        && f.args().stream().allMatch(a -> a instanceof VarDecl)
        && Set.copyOf(f.args()).size() == f.args().size();
  }

  /**
   * Counts every function symbol occurrence in {@code t}, at any depth, into {@code counts}.
   *
   * @param t the term to walk
   * @param counts the per-symbol occurrence counts to add to
   */
  private static void countSymbols(Term t, Map<Symbol, Integer> counts) {
    if (t instanceof FnApp(Symbol s, List<Term> args)) {
      counts.merge(s, 1, Integer::sum);
      args.forEach(a -> countSymbols(a, counts));
    }
  }

  /**
   * Finds the symbols lying on a cycle of the forwarding map. Symbols merely leading into a cycle
   * are not on it and stay removable.
   *
   * @param forward the forwarding map to check
   * @return the symbols that are part of a forwarding cycle
   */
  private static Set<Symbol> cyclic(Map<Symbol, Symbol> forward) {
    Set<Symbol> onCycle = new HashSet<>();
    for (Symbol start : forward.keySet()) {
      List<Symbol> path = new ArrayList<>();
      Symbol current = start;
      while (forward.containsKey(current) && !onCycle.contains(current)) {
        int seenAt = path.indexOf(current);
        if (seenAt >= 0) {
          onCycle.addAll(path.subList(seenAt, path.size()));
          break;
        }
        path.add(current);
        current = forward.get(current);
      }
    }
    return onCycle;
  }

  /**
   * Rewrites {@code t}, replacing every application of a removed symbol with an application of its
   * transitive forwarding target.
   *
   * @param t the term to rewrite
   * @param forward the acyclic forwarding map
   * @return the rewritten term
   */
  private static Term redirect(Term t, Map<Symbol, Symbol> forward) {
    if (!(t instanceof FnApp)) {
      return t;
    }
    Symbol target = s;
    while (forward.containsKey(target)) {
      target = forward.get(target);
    }
    return new FnApp(target, args.stream().map(a -> redirect(a, forward)).toList());
  }
}
