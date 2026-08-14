package project.lctrs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimplifierTest {

  private static final VarDecl X = new VarDecl("x", Sort.INT);

  private static final TermSymbol F = new TermSymbol("f", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol U1 = new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol U2 = new TermSymbol("u2", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol RET = new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT);

  private static FnApp app(Symbol s, Term... args) {
    return new FnApp(s, List.of(args));
  }

  private static Rule rule(Term lhs, Term rhs) {
    return new Rule(lhs, rhs, Optional.empty());
  }

  private static Lctrs lctrs(List<Symbol> sigma, Rule... rules) {
    return new Lctrs().appendSymbols(sigma).appendRules(List.of(rules));
  }

  private static Constraint constraint(Term formula) {
    return new Constraint(formula);
  }

  /**
   * The shape a division by the literal 2 leaves on its {@code err} rule: {@code ¬((2 ≠ 0) ∧ ¬((y =
   * MIN) ∧ (2 = -1)))} folds to {@code false}, so the rule can never fire and is dropped.
   */
  @Test
  void dropsRuleWithUnsatisfiableConstraint() {
    Term guard =
        app(
            TheorySymbol.AND,
            app(TheorySymbol.NEQ_INT, IntValue.of(2), IntValue.of(0)),
            app(
                TheorySymbol.NOT,
                app(
                    TheorySymbol.AND,
                    app(TheorySymbol.EQ_INT, X, IntValue.of(-2147483648L)),
                    app(TheorySymbol.EQ_INT, IntValue.of(2), IntValue.of(-1)))));
    Rule err =
        new Rule(app(U1, X), app(RET, X), Optional.of(constraint(app(TheorySymbol.NOT, guard))));
    Rule live = rule(app(U2, X), app(RET, X));

    Lctrs simplified =
        Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, U2, RET), err, live));

    assertEquals(List.of(live), simplified.rules());
    assertEquals(List.of(F, U1, U2, RET), simplified.sigma());
  }

  /**
   * A partially constant conjunction keeps its variable part: {@code ((2 ≠ 0) ∧ ¬(false)) ∧ (x ≥
   * 0)} folds to {@code x ≥ 0}.
   */
  @Test
  void foldsConstantConjunctsKeepingVariablePart() {
    Term agree = app(TheorySymbol.GE, X, IntValue.of(0));
    Term formula =
        app(
            TheorySymbol.AND,
            app(
                TheorySymbol.AND,
                app(TheorySymbol.NEQ_INT, IntValue.of(2), IntValue.of(0)),
                app(TheorySymbol.NOT, new BoolValue(false))),
            agree);
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(
        List.of(new Rule(app(U1, X), app(RET, X), Optional.of(constraint(agree)))),
        simplified.rules());
  }

  /**
   * A tautological constraint still mentioning a variable is kept untouched: its variables are in
   * LVar, so dropping it would enlarge the rewrite relation.
   */
  @Test
  void keepsTautologyMentioningVariable() {
    Term formula =
        app(
            TheorySymbol.OR,
            app(TheorySymbol.GT, IntValue.of(2), IntValue.of(0)),
            app(TheorySymbol.LT, X, IntValue.of(0)));
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));
    Lctrs in = lctrs(List.of(F, U1, RET), r);

    assertEquals(in.rules(), Simplifier.foldConstantConstraints(in).rules());
  }

  /**
   * A fold whose result mentions fewer variables than the original constraint is discarded: {@code
   * ((x < 1) ∨ true) ∧ (y > 0)} would fold to {@code y > 0}, erasing {@code x} from LVar, so the
   * constraint stays untouched.
   */
  @Test
  void keepsConstraintWhenFoldWouldEraseVariable() {
    VarDecl y = new VarDecl("y", Sort.INT);
    TermSymbol u3 = new TermSymbol("u3", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    Term formula =
        app(
            TheorySymbol.AND,
            app(TheorySymbol.OR, app(TheorySymbol.LT, X, IntValue.of(1)), new BoolValue(true)),
            app(TheorySymbol.GT, y, IntValue.of(0)));
    Rule r = new Rule(app(u3, X, y), app(RET, X), Optional.of(constraint(formula)));
    Lctrs in = lctrs(List.of(F, u3, RET), r);

    assertEquals(in.rules(), Simplifier.foldConstantConstraints(in).rules());
  }

  /** A ground constraint that folds to {@code true} is dropped, leaving the rule unconstrained. */
  @Test
  void dropsGroundConstraintFoldingToTrue() {
    Term formula =
        app(
            TheorySymbol.OR,
            app(TheorySymbol.GT, IntValue.of(2), IntValue.of(0)),
            app(TheorySymbol.LT, IntValue.of(1), IntValue.of(0)));
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(List.of(rule(app(U1, X), app(RET, X))), simplified.rules());
  }

  /**
   * A constraint that is already the bare value {@code true} (e.g. from {@code if true} with no
   * safety clauses) is dropped even though folding it is an identity.
   */
  @Test
  void dropsBareTrueConstraint() {
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(new BoolValue(true))));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(List.of(rule(app(U1, X), app(RET, X))), simplified.rules());
  }

  /** A rule whose constraint is already the bare value {@code false} can never fire. */
  @Test
  void dropsRuleWithBareFalseConstraint() {
    Rule dead = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(new BoolValue(false))));
    Rule live = rule(app(U2, X), app(RET, X));

    Lctrs simplified =
        Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, U2, RET), dead, live));

    assertEquals(List.of(live), simplified.rules());
  }

  /** A constraint with no constant atoms is untouched, and the LCTRS is returned as-is. */
  @Test
  void leavesVariableOnlyConstraintsAlone() {
    Term formula =
        app(
            TheorySymbol.AND,
            app(TheorySymbol.LT, X, IntValue.of(0)),
            app(TheorySymbol.NEQ_INT, app(TheorySymbol.MOD, X, IntValue.of(2)), IntValue.of(0)));
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));
    Lctrs in = lctrs(List.of(F, U1, RET), r);

    assertEquals(in.rules(), Simplifier.foldConstantConstraints(in).rules());
  }

  /**
   * A chain {@code f -> u1 -> u2 -> ret} collapses transitively: the entry rule steps straight to
   * {@code ret_Int}, the inlined rules disappear, and so do their head symbols in the signature.
   * The entry symbol {@code f} is protected even though its own rule is a single step.
   */
  @Test
  void collapsesChainTransitivelyAndProtectsEntry() {
    Lctrs simplified =
        Simplifier.inline(
            lctrs(
                List.of(F, U1, U2, RET),
                rule(app(F, X), app(U1, X)),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(RET, X))),
            Set.of(F));

    assertEquals(List.of(rule(app(F, X), app(RET, X))), simplified.rules());
    assertEquals(List.of(F, RET), simplified.sigma());
  }

  /**
   * Inlining substitutes into a constrained rule's right-hand side and leaves its constraint alone,
   * so the entry rule's parameter-width bound survives the removal of the program point it steps
   * to.
   */
  @Test
  void preservesConstraintOfTheRuleInlinedInto() {
    Constraint bound = constraint(app(TheorySymbol.LE, IntValue.of(0), X));
    Lctrs simplified =
        Simplifier.inline(
            lctrs(
                List.of(F, U1, RET),
                new Rule(app(F, X), app(U1, X), Optional.of(bound)),
                rule(app(U1, X), app(RET, X))),
            Set.of(F));

    assertEquals(List.of(new Rule(app(F, X), app(RET, X), Optional.of(bound))), simplified.rules());
    assertEquals(List.of(F, RET), simplified.sigma());
  }

  /** Inlining reaches occurrences nested inside a right-hand side, not just at its root. */
  @Test
  void inlinesNestedOccurrences() {
    TermSymbol wrap = new TermSymbol("u3", List.of(Sort.RESULT), Sort.RESULT);
    Lctrs simplified =
        Simplifier.inline(
            lctrs(
                List.of(F, U1, U2, wrap),
                rule(app(F, X), app(wrap, app(U1, X))),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(RET, X))),
            Set.of(F));

    assertEquals(List.of(rule(app(F, X), app(wrap, app(RET, X)))), simplified.rules());
  }

  /**
   * A body that computes on its arguments is inlined like any other single step, at every call site
   * and under the substitution of that site's arguments.
   */
  @Test
  void inlinesComputingBodyAtEveryCallSite() {
    TermSymbol u3 = new TermSymbol("u3", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    VarDecl y = new VarDecl("y", Sort.INT);
    IntValue one = new IntValue(BigInteger.ONE);
    Lctrs simplified =
        Simplifier.inline(
            lctrs(
                List.of(F, U1, u3, RET),
                rule(app(F, X), app(u3, X, FnApp.add(X, one))),
                rule(app(U1, X), app(u3, one, X)),
                rule(app(u3, X, y), app(RET, y))),
            Set.of(F, U1));

    assertEquals(
        List.of(rule(app(F, X), app(RET, FnApp.add(X, one))), rule(app(U1, X), app(RET, X))),
        simplified.rules());
    assertEquals(List.of(F, U1, RET), simplified.sigma());
  }

  /**
   * Non-candidates survive untouched: a constrained rule, a head with two rules, and a left-hand
   * side that pattern-matches a constructor rather than binding distinct variables.
   */
  @Test
  void keepsRulesThatAreNotSingleSteps() {
    TermSymbol u4 = new TermSymbol("u4", List.of(Sort.INT), Sort.RESULT);
    IntValue zero = new IntValue(BigInteger.ZERO);
    Constraint phi = new Constraint(new FnApp(TheorySymbol.LT, List.of(X, zero)));
    Rule constrained = new Rule(app(U1, X), app(U2, X), Optional.of(phi));
    Rule firstOfTwo = rule(app(U2, X), app(RET, X));
    Rule secondOfTwo = rule(app(U2, X), app(U1, X));
    // Same argument list on both sides, but a value is not a variable, so nothing to substitute.
    Rule patternLhs = rule(app(u4, zero), app(RET, zero));
    Lctrs in = lctrs(List.of(F, U1, U2, u4, RET), constrained, firstOfTwo, secondOfTwo, patternLhs);

    Lctrs simplified = Simplifier.inline(in, Set.of(F));

    assertEquals(in.rules(), simplified.rules());
    assertEquals(in.sigma(), simplified.sigma());
  }

  /**
   * A cycle of single steps has no well-defined expansion, so its members are kept; a step merely
   * leading into the cycle is still inlined.
   */
  @Test
  void keepsInliningCycles() {
    Lctrs simplified =
        Simplifier.inline(
            lctrs(
                List.of(F, U1, U2),
                rule(app(F, X), app(U1, X)),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(U1, X))),
            Set.of(F));

    assertEquals(
        List.of(
            rule(app(F, X), app(U1, X)),
            rule(app(U1, X), app(U2, X)),
            rule(app(U2, X), app(U1, X))),
        simplified.rules());
    assertEquals(List.of(F, U1, U2), simplified.sigma());
  }

  /** A self-loop is a cycle of one, so it survives rather than expanding forever. */
  @Test
  void keepsSelfLoop() {
    Lctrs in =
        lctrs(
            List.of(F, U1),
            rule(app(F, X), app(U1, X)),
            rule(app(U1, X), app(U1, FnApp.add(X, new IntValue(BigInteger.ONE)))));

    assertEquals(in.rules(), Simplifier.inline(in, Set.of(F)).rules());
  }

  /**
   * A head that also appears inside another rule's left-hand side is not removable: rewriting
   * right-hand sides alone would strand that pattern.
   */
  @Test
  void keepsHeadOccurringInAnotherLhs() {
    TermSymbol cont = new TermSymbol("u3", List.of(Sort.RESULT), Sort.RESULT);
    Lctrs in =
        lctrs(
            List.of(F, U1, RET, cont),
            rule(app(U1, X), app(RET, X)),
            rule(app(cont, app(U1, X)), app(RET, X)));

    Lctrs simplified = Simplifier.inline(in, Set.of(F));

    assertEquals(in.rules(), simplified.rules());
  }
}
