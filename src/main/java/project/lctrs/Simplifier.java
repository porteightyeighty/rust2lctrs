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
 *   <li>{@link #inline}: removes a program point whose behaviour is a single unconditional step,
 *       substituting its rule's right-hand side into every occurrence of it. The
 *       statement-by-statement translation leaves such program points behind (cf. Fuhs, Kop &amp;
 *       Nishida (2017))
 * </ul>
 */
public final class Simplifier {

  private static final Logger LOG = LoggerFactory.getLogger(Simplifier.class);

  private Simplifier() {}

  /**
   * Runs both {@link #foldConstantConstraints} and {@link #inline} on the given LCTRS. Constant
   * constraints are folded first: dropping a rule whose constraint is unsatisfiable can leave its
   * head with a single defining rule, which inlining then removes.
   *
   * @param lctrs the LCTRS to simplify
   * @return a new, simplified LCTRS, or itself when nothing changes
   */
  public static Lctrs simplify(Lctrs lctrs) {
    Lctrs folded = foldConstantConstraints(lctrs);
    return inline(folded, folded.entries());
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
   * Inlines the single-step program points of an LCTRS: each removable symbol's rule is deleted and
   * every occurrence of that symbol, at any depth in any right-hand side, is replaced by the rule's
   * right-hand side under the substitution of its arguments. Inlining is transitive, so a chain
   * {@code u₁ -> u₂ -> u₃} collapses onto {@code u₃}, and the removed symbols are dropped from the
   * terms signature.
   *
   * @param lctrs the LCTRS to simplify
   * @param keep symbols that must never be removed, such as function entry symbols
   * @return a new, simplified LCTRS, or itself when nothing is removable
   */
  public static Lctrs inline(Lctrs lctrs, Set<Symbol> keep) {
    List<Rule> rules = lctrs.rules();
    Map<Symbol, Integer> lhsUses = new HashMap<>();
    for (Rule r : rules) {
      countSymbols(r.lhs(), lhsUses);
    }

    Map<Symbol, Rule> bodies = new LinkedHashMap<>();
    for (Rule r : rules) {
      if (isSingleStep(r)) {
        Symbol f = ((FnApp) r.lhs()).symbol();
        if (!keep.contains(f) && lhsUses.get(f) == 1) {
          bodies.put(f, r);
        }
      }
    }
    bodies.keySet().removeAll(cyclic(bodies));
    if (bodies.isEmpty()) {
      return lctrs;
    }
    LOG.debug("Inlining {} program point(s): {}", bodies.size(), bodies.keySet());

    List<Rule> kept = new ArrayList<>();
    for (Rule r : rules) {
      if (r.lhs() instanceof FnApp f && bodies.containsKey(f.symbol())) {
        continue; // the inlined rule itself
      }
      kept.add(new Rule(r.lhs(), expand(r.rhs(), bodies), r.constraint()));
    }
    List<Symbol> sigma = lctrs.sigma().stream().filter(s -> !bodies.containsKey(s)).toList();
    return new Lctrs().appendSymbols(sigma).appendEntrySymbols(lctrs.entries()).appendRules(kept);
  }

  /**
   * Reports whether a rule defines its head as a single unconditional step: unconstrained, the
   * left-hand side a symbol applied to distinct variables, and the right-hand side mentioning no
   * other variable.
   *
   * @param r the rule to classify
   * @return {@code true} if the rule's right-hand side can replace applications of its head
   */
  private static boolean isSingleStep(Rule r) {
    if (r.constraint().isPresent() || !(r.lhs() instanceof FnApp f)) {
      return false;
    }
    Set<Term> params = Set.copyOf(f.args());
    return params.size() == f.args().size()
        && params.stream().allMatch(a -> a instanceof VarDecl)
        && params.containsAll(variables(r.rhs()));
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
   * Finds the candidates lying on a cycle of the inlining graph, whose edges run from a candidate
   * to the candidates its right-hand side mentions.
   *
   * @param bodies the candidates to check
   * @return the candidates that are part of an inlining cycle
   */
  private static Set<Symbol> cyclic(Map<Symbol, Rule> bodies) {
    Map<Symbol, Set<Symbol>> edges = new LinkedHashMap<>();
    bodies.forEach(
        (f, r) -> {
          Map<Symbol, Integer> used = new HashMap<>();
          countSymbols(r.rhs(), used);
          Set<Symbol> out = new HashSet<>(used.keySet());
          out.retainAll(bodies.keySet());
          edges.put(f, out);
        });
    Set<Symbol> onCycle = new HashSet<>();
    for (Symbol f : bodies.keySet()) {
      if (reaches(f, f, edges, new HashSet<>())) {
        onCycle.add(f);
      }
    }
    return onCycle;
  }

  /**
   * Reports whether {@code target} is reachable from {@code from} in one or more edges.
   *
   * @param from the symbol to walk out from
   * @param target the symbol to look for
   * @param edges the inlining graph
   * @param seen the symbols already walked out from, to keep the walk finite
   * @return {@code true} if some path of at least one edge leads to {@code target}
   */
  private static boolean reaches(
      Symbol from, Symbol target, Map<Symbol, Set<Symbol>> edges, Set<Symbol> seen) {
    for (Symbol next : edges.getOrDefault(from, Set.of())) {
      if (next.equals(target) || (seen.add(next) && reaches(next, target, edges, seen))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Rewrites {@code t}, replacing every application of an inlined symbol with that symbol's
   * right-hand side under the substitution of the application's arguments. Nested applications are
   * expanded first, and the substituted body is expanded in turn, so a chain of inlined symbols
   * collapses in one walk.
   *
   * @param t the term to rewrite
   * @param bodies the acyclic map of inlined symbols to their defining rules
   * @return the rewritten term
   */
  private static Term expand(Term t, Map<Symbol, Rule> bodies) {
    if (!(t instanceof FnApp(Symbol s, List<Term> rawArgs))) {
      return t;
    }
    List<Term> args = rawArgs.stream().map(a -> expand(a, bodies)).toList();
    Rule body = bodies.get(s);
    if (body == null) {
      return args.equals(rawArgs) ? t : new FnApp(s, args);
    }
    List<Term> params = ((FnApp) body.lhs()).args();
    Map<VarDecl, Term> substitution = new HashMap<>();
    for (int i = 0; i < params.size(); i++) {
      substitution.put((VarDecl) params.get(i), args.get(i));
    }
    return expand(substitute(body.rhs(), substitution), bodies);
  }

  /**
   * Applies a substitution to a term, replacing every variable in its domain.
   *
   * @param t the term to rewrite
   * @param substitution the variables to replace and their replacements
   * @return the substituted term
   */
  private static Term substitute(Term t, Map<VarDecl, Term> substitution) {
    return switch (t) {
      case VarDecl v -> substitution.getOrDefault(v, v);
      case Value v -> v;
      case FnApp(Symbol s, List<Term> args) ->
          new FnApp(s, args.stream().map(a -> substitute(a, substitution)).toList());
    };
  }
}
