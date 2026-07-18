package project.lctrs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-translation simplification. Two passes:
 *
 * <ul>
 *   <li>{@link #foldConstantConstraints}: constant-folds rule constraints, dropping ground
 *       constraints that fold to {@code true} and rules whose constraint folds to {@code false}.
 *       The uniform panic guards instantiate to constant atoms when an operand is a literal (e.g.
 *       {@code 2 ≠ 0} and {@code 2 = -1} for a division by the literal 2), leaving vacuous {@code
 *       err} rules behind.
 *   <li>{@link #removeForwardingRules}: removes forwarding rules {@code f(x₁, …, xₙ) -> g(x₁, …,
 *       xₙ)} and redirects every occurrence of {@code f} to {@code g}. The statement-by-statement
 *       translation leaves such rules behind (cf. Fuhs, Kop &amp; Nishida (2017)). A rule is a
 *       candidate only when its left-hand side is a symbol applied to distinct variables, that
 *       symbol heads no other rule and occurs in no other left-hand side, and the symbol is not
 *       protected by the caller (e.g. function entry symbols).
 * </ul>
 */
public final class Simplifier {

  private static final Logger LOG = LoggerFactory.getLogger(Simplifier.class);

  private Simplifier() {}

  /**
   * Runs both {@link #foldConstantConstraints} and {@link #removeForwardingRules} on the given
   * LCTRS. Constant constraints are folded first, then forwarding rules are removed.
   *
   * @param lctrs the LCTRS to simplify
   * @return a new, simplified LCTRS, or itself when nothing changes
   */
  public static Lctrs simplify(Lctrs lctrs) {
    Lctrs folded = foldConstantConstraints(lctrs);
    return removeForwardingRules(folded, folded.entries());
  }

  /**
   * Constant-folds every rule's constraint. A ground constraint that folds to {@code true} is
   * dropped (the rule becomes unconstrained); a rule whose constraint is logically unsatisfiable
   * can never fire and is removed whole. A surviving constraint never loses a variable (see {@link
   * #fold}), since every variable of the constraint is in LVar. Left- and right-hand sides are
   * never touched.
   *
   * @param lctrs the LCTRS to simplify
   * @return a new, simplified LCTRS, or {@code lctrs} itself when nothing folds
   */
  public static Lctrs foldConstantConstraints(Lctrs lctrs) {
    List<Rule> kept = new ArrayList<>();
    boolean changed = false;
    for (Rule r : lctrs.rules()) {
      Term formula = r.constraint().map(Constraint::formula).orElse(null);
      if (formula == null) {
        kept.add(r);
        continue;
      }
      // Before the identity shortcut: a bare boolean-value constraint folds to itself.
      Term folded = fold(formula);
      if (folded instanceof BoolValue(boolean satisfiable)) {
        if (!satisfiable) {
          changed = true;
          LOG.debug("Dropping rule with unsatisfiable constraint: {} -> {}", r.lhs(), r.rhs());
        } else if (variables(formula).isEmpty()) {
          changed = true;
          kept.add(new Rule(r.lhs(), r.rhs(), Optional.empty()));
        } else {
          kept.add(r);
        }
        continue;
      }
      if (folded == formula || !variables(folded).equals(variables(formula))) {
        kept.add(r);
        continue;
      }
      changed = true;
      kept.add(new Rule(r.lhs(), r.rhs(), Optional.of(new Constraint(folded))));
    }
    if (!changed) {
      return lctrs;
    }
    return new Lctrs()
        .appendSymbols(lctrs.sigma())
        .appendEntrySymbols(lctrs.entries())
        .appendRules(kept);
  }

  /**
   * Folds the constant subterms of a formula: comparisons of two integer values, (in)equality of
   * two boolean values, and the boolean connectives over a constant operand. Returns {@code t}
   * itself when nothing folds.
   *
   * <p>Folding preserves logical equivalence but may discard variable-containing subterms ({@code P
   * ∨ true} folds to {@code true}), so the caller must check that the result mentions the same
   * variables before keeping it: every variable of a constraint is in LVar and must be instantiated
   * by a theory value for the rule to fire, so erasing one from a surviving constraint would
   * enlarge the rewrite relation (Kop &amp; Nishida (2013), Def. 2.6).
   *
   * @param t the term to fold
   * @return the folded term, or {@code t} unchanged
   */
  private static Term fold(Term t) {
    if (!(t instanceof FnApp(Symbol symbol, List<Term> rawArgs))) {
      return t;
    }
    List<Term> args = rawArgs.stream().map(Simplifier::fold).toList();
    if (symbol instanceof TheorySymbol s) {
      Optional<Term> reduced = reduce(s, args);
      if (reduced.isPresent()) {
        return reduced.get();
      }
    }
    return args.equals(rawArgs) ? t : new FnApp(symbol, args);
  }

  /**
   * Reduces a single theory application over already-folded arguments, or returns empty when no
   * reduction applies. Arithmetic symbols are not folded: the translation only produces constant
   * comparisons.
   *
   * @param s the theory symbol
   * @param args the folded arguments
   * @return the reduced term, or empty when no reduction applies
   */
  private static Optional<Term> reduce(TheorySymbol s, List<Term> args) {
    return switch (s) {
      case LT, LE, GT, GE, EQ_INT, NEQ_INT ->
          args.get(0) instanceof IntValue(BigInteger a)
                  && args.get(1) instanceof IntValue(BigInteger b)
              ? Optional.of(new BoolValue(compare(s, a.compareTo(b))))
              : Optional.empty();
      case EQ_BOOL, NEQ_BOOL ->
          args.get(0) instanceof BoolValue(boolean a) && args.get(1) instanceof BoolValue(boolean b)
              ? Optional.of(new BoolValue(s == TheorySymbol.EQ_BOOL ? a == b : a != b))
              : Optional.empty();
      case NOT ->
          args.get(0) instanceof BoolValue(boolean v)
              ? Optional.of(new BoolValue(!v))
              : Optional.empty();
      case AND -> {
        if (args.get(0) instanceof BoolValue(boolean a)) {
          yield a ? Optional.of(args.get(1)) : Optional.of(new BoolValue(false));
        }
        if (args.get(1) instanceof BoolValue(boolean b)) {
          yield b ? Optional.of(args.get(0)) : Optional.of(new BoolValue(false));
        }
        yield Optional.empty();
      }
      case OR -> {
        if (args.get(0) instanceof BoolValue(boolean a)) {
          yield a ? Optional.of(new BoolValue(true)) : Optional.of(args.get(1));
        }
        if (args.get(1) instanceof BoolValue(boolean b)) {
          yield b ? Optional.of(new BoolValue(true)) : Optional.of(args.get(0));
        }
        yield Optional.empty();
      }
      default -> Optional.empty();
    };
  }

  /**
   * Collects the variables occurring in a term.
   *
   * @param t the term to walk
   * @return the set of variables occurring in {@code t}
   */
  private static Set<VarDecl> variables(Term t) {
    return switch (t) {
      case VarDecl v -> Set.of(v);
      case Value v -> Set.of();
      case FnApp(Symbol s, List<Term> args) -> {
        Set<VarDecl> vars = new HashSet<>();
        args.forEach(a -> vars.addAll(variables(a)));
        yield vars;
      }
    };
  }

  /**
   * Evaluates an integer comparison symbol against a {@link Comparable#compareTo} result.
   *
   * @param s the comparison symbol
   * @param cmp the comparison of left against right
   * @return the truth value of the comparison
   */
  private static boolean compare(TheorySymbol s, int cmp) {
    return switch (s) {
      case LT -> cmp < 0;
      case LE -> cmp <= 0;
      case GT -> cmp > 0;
      case GE -> cmp >= 0;
      case EQ_INT -> cmp == 0;
      case NEQ_INT -> cmp != 0;
      default -> throw new IllegalArgumentException("not a comparison: " + s);
    };
  }

  /**
   * Removes the forwarding rules of an LCTRS, redirecting occurrences of each removed symbol to its
   * forwarding target (transitively, so a chain {@code u₁ -> u₂ -> u₃} collapses onto {@code u₃})
   * and dropping the removed symbols from the terms signature.
   *
   * @param lctrs the LCTRS to simplify
   * @param keep symbols that must never be removed, such as function entry symbols
   * @return a new, simplified LCTRS, or itself when nothing is removable
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
    return new Lctrs().appendSymbols(sigma).appendEntrySymbols(lctrs.entries()).appendRules(kept);
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
    if (!(t instanceof FnApp(Symbol s, List<Term> args))) {
      return t;
    }
    Symbol target = s;
    while (forward.containsKey(target)) {
      target = forward.get(target);
    }
    return new FnApp(target, args.stream().map(a -> redirect(a, forward)).toList());
  }
}
