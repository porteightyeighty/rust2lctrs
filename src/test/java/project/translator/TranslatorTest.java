package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static project.translator.AstHelper.BOOL;
import static project.translator.AstHelper.I16;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.and;
import static project.translator.AstHelper.assign;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.boolLit;
import static project.translator.AstHelper.brk;
import static project.translator.AstHelper.call;
import static project.translator.AstHelper.crate;
import static project.translator.AstHelper.div;
import static project.translator.AstHelper.fn;
import static project.translator.AstHelper.fnUnit;
import static project.translator.AstHelper.gt;
import static project.translator.AstHelper.ifStmt;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.lt;
import static project.translator.AstHelper.mul;
import static project.translator.AstHelper.neg;
import static project.translator.AstHelper.not;
import static project.translator.AstHelper.or;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
import static project.translator.AstHelper.translateFn;
import static project.translator.AstHelper.translateFnRaw;
import static project.translator.AstHelper.translateUnitFn;
import static project.translator.AstHelper.var;
import static project.translator.AstHelper.whileStmt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import project.ast.Crate;
import project.ast.UnsupportedConstructException;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Simplifier;
import project.lctrs.Sort;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.lctrs.VarDecl;

class TranslatorTest {

  private static final IntValue I32_MIN = new IntValue(BigInteger.valueOf(Integer.MIN_VALUE));
  private static final IntValue I32_MAX = new IntValue(BigInteger.valueOf(Integer.MAX_VALUE));
  private static final IntValue I16_MIN = new IntValue(BigInteger.valueOf(-32768));
  private static final IntValue I16_MAX = new IntValue(BigInteger.valueOf(32767));
  private static final IntValue I16_SPAN = new IntValue(BigInteger.valueOf(65536));

  /** The i32 within-width bound on {@code t}: {@code (MIN <= t) AND (t <= MAX)}. */
  private static FnApp i32Bound(Term t) {
    return new FnApp(
        TheorySymbol.AND,
        List.of(
            new FnApp(TheorySymbol.LE, List.of(I32_MIN, t)),
            new FnApp(TheorySymbol.LE, List.of(t, I32_MAX))));
  }

  /** The i16 within-width bound on {@code t}: {@code (MIN <= t) AND (t <= MAX)}. */
  private static FnApp i16Bound(Term t) {
    return new FnApp(
        TheorySymbol.AND,
        List.of(
            new FnApp(TheorySymbol.LE, List.of(I16_MIN, t)),
            new FnApp(TheorySymbol.LE, List.of(t, I16_MAX))));
  }

  /** The i16 two's-complement wrap of {@code t}: {@code ((t - MIN) % SPAN) + MIN}. */
  private static FnApp wrap16(Term t) {
    return new FnApp(
        TheorySymbol.ADD,
        List.of(
            new FnApp(
                TheorySymbol.MOD,
                List.of(new FnApp(TheorySymbol.SUB, List.of(t, I16_MIN)), I16_SPAN)),
            I16_MIN));
  }

  /** The nullary error sink {@code err}. */
  private static FnApp err() {
    return new FnApp(new TermSymbol("err", List.of(), Sort.RESULT), List.of());
  }

  /**
   * The entry rule every function leads with: {@code entry -> body} guarded by the conjoined width
   * bounds of the parameters
   *
   * @param entry the entry configuration
   * @param body the configuration the body is lowered against
   * @param bounds the per-parameter width bounds, conjoined left to right
   * @return the entry rule
   */
  private static Rule entryRule(FnApp entry, FnApp body, FnApp... bounds) {
    Term phi = bounds[0];
    for (int i = 1; i < bounds.length; i++) {
      phi = new FnApp(TheorySymbol.AND, List.of(phi, bounds[i]));
    }
    return new Rule(entry, body, Optional.of(new Constraint(phi)));
  }

  /**
   * {@code fn f(n: i32) -> i32 { let x = n + 1; x }}. The point of interest is that the bound value
   * on the right-hand side is evaluated over the <em>pre-binding</em> scope: the first slot is the
   * live variable {@code n} and the second is {@code n + 1}, never {@code x}. Because {@code n + 1}
   * can overflow, {@code u1} splits into a guarded normal rule and an {@code err} rule on the
   * negated bound. Asserted before simplification, which would inline {@code u2} away.
   */
  @Test
  void letBindingEvaluatesValueOverPreBindingScope() {
    Lctrs lctrs =
        translateFnRaw(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    FnApp u1 = new FnApp(new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT), List.of(n));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u2 = new FnApp(u2Symbol, List.of(n, nPlusOne));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            u1, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(u1, u2, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u2scope, retX, Optional.empty());
    assertEquals(
        List.of(entryRule(entry, u1, i32Bound(n)), errRule, normalRule, retRule), lctrs.rules());
  }

  /**
   * Two sequential {@code let} bindings thread their configurations: the outgoing configuration of
   * the first rule is the incoming configuration (left-hand side) of the second. For {@code fn f(n:
   * i32) -> i32 { let x = n + 1; let y = x; x }} this gives {@code u1(n) -> u2(n, n + 1)} followed
   * by {@code u2(n, x) -> u3(n, x, x)}: the {@code u2} that closes the first rule reopens as the
   * head of the next rule's left-hand side, now over the scope variables. Asserted before
   * simplification, which would inline both {@code u2} and {@code u3} away.
   */
  @Test
  void sequentialLetsThreadConfigurations() {
    Lctrs lctrs =
        translateFnRaw(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), let("y", I32, var("x")), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    FnApp u1 = new FnApp(new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT), List.of(n));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u2 = new FnApp(u2Symbol, List.of(n, nPlusOne));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x));
    TermSymbol u3Symbol = new TermSymbol("u3", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);

    FnApp u3 = new FnApp(u3Symbol, List.of(n, x, x));
    FnApp u3scope = new FnApp(u3Symbol, List.of(n, x, y));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));

    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            u1, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    // Only the first let (n + 1) can overflow; let y = x carries no bound, so the last two rules
    // thread configurations unconstrained.
    Rule normalRule = new Rule(u1, u2, Optional.of(new Constraint(bound)));
    Rule expected2 = new Rule(u2scope, u3, Optional.empty());
    Rule expected3 = new Rule(u3scope, retX, Optional.empty());

    assertEquals(
        List.of(entryRule(entry, u1, i32Bound(n)), errRule, normalRule, expected2, expected3),
        lctrs.rules());
  }

  /**
   * A shadowing {@code let} stays a distinct LCTRS variable rather than collapsing onto the binding
   * it shadows. For {@code fn f(n: i32) -> i32 { let x = n; let x = x + 1; x }} the second {@code
   * x} mints a fresh name {@code x_1}: its value {@code x + 1} is evaluated over the <em>outer</em>
   * {@code x}. Asserted before simplification, which would inline {@code u3} and with it every
   * occurrence of {@code x_1}.
   */
  @Test
  void shadowingLetMintsDistinctVariable() {
    Lctrs lctrs =
        translateFnRaw(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, var("n")), let("x", I32, add(var("x"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl xShadow = new VarDecl("x_1", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp u1 = new FnApp(new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT), List.of(n));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u2 = new FnApp(u2Symbol, List.of(n, n));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x));
    TermSymbol u3Symbol = new TermSymbol("u3", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp u3 = new FnApp(u3Symbol, List.of(n, x, xPlusOne));
    FnApp u3scope = new FnApp(u3Symbol, List.of(n, x, xShadow));
    FnApp retShadow =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(xShadow));

    FnApp bound = i32Bound(xPlusOne);
    // let x = n carries no bound (single unguarded rule); only the shadowing let x = x + 1
    // overflows.
    Rule firstLet = new Rule(u1, u2, Optional.empty());
    Rule errRule =
        new Rule(
            u2scope,
            err(),
            Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(u2scope, u3, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u3scope, retShadow, Optional.empty());

    assertEquals(
        List.of(entryRule(entry, u1, i32Bound(n)), firstLet, errRule, normalRule, retRule),
        lctrs.rules());
  }

  /**
   * {@code fn f(x: i32, y: i32) -> i32 { if x < 1 && y > 2 { return 1; } 0 }}. A lazy boolean
   * operator over panic-free operands lowers eagerly: one conjunction inside the branch guards, no
   * program point beyond the entry hop, no err rule.
   */
  @Test
  void lazyAndLowersToConjunctionInBranchGuards() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I32), param("y", I32)),
            I32,
            block(
                ifStmt(
                    and(lt(var("x"), intLit(1)), gt(var("y"), intLit(2))), block(ret(intLit(1)))),
                ret(intLit(0))));

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry =
        new FnApp(new TermSymbol("f", List.of(Sort.INT, Sort.INT), Sort.RESULT), List.of(x, y));
    FnApp cond =
        new FnApp(
            TheorySymbol.AND,
            List.of(
                new FnApp(TheorySymbol.LT, List.of(x, IntValue.of(1))),
                new FnApp(TheorySymbol.GT, List.of(y, IntValue.of(2)))));
    TermSymbol retInt = new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT);
    FnApp u1 =
        new FnApp(new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT), List.of(x, y));

    Rule thenRule =
        new Rule(u1, new FnApp(retInt, List.of(IntValue.of(1))), Optional.of(new Constraint(cond)));
    Rule mergeRule =
        new Rule(
            u1,
            new FnApp(retInt, List.of(IntValue.of(0))),
            Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(cond)))));
    assertEquals(
        List.of(entryRule(entry, u1, i32Bound(x), i32Bound(y)), thenRule, mergeRule),
        lctrs.rules());
  }

  /**
   * {@code fn f(x: i32, y: i32) -> i32 { if x < 1 && y + 1 > 2 { return 1; } 0 }} under the debug
   * profile. Rust only evaluates {@code y + 1} when {@code x < 1} holds, so the overflow clause of
   * the right operand is guarded by the left's value: safety is {@code ¬(x < 1) ∨ bound(y + 1)},
   * and the err rule is unreachable whenever the left conjunct is false.
   */
  @Test
  void lazyAndGuardsRightOperandOverflowByLeftValue() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I32), param("y", I32)),
            I32,
            block(
                ifStmt(
                    and(lt(var("x"), intLit(1)), gt(add(var("y"), intLit(1)), intLit(2))),
                    block(ret(intLit(1)))),
                ret(intLit(0))));

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp u1 =
        new FnApp(new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT), List.of(x, y));
    FnApp yPlusOne = new FnApp(TheorySymbol.ADD, List.of(y, IntValue.of(1)));
    FnApp leftFalse =
        new FnApp(
            TheorySymbol.NOT, List.of(new FnApp(TheorySymbol.LT, List.of(x, IntValue.of(1)))));
    FnApp safety = new FnApp(TheorySymbol.OR, List.of(leftFalse, i32Bound(yPlusOne)));

    Rule errRule = lctrs.rules().get(1);
    assertEquals(u1, errRule.lhs());
    assertEquals(err(), errRule.rhs());
    assertEquals(
        Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(safety)))),
        errRule.constraint());
  }

  /**
   * As {@link #lazyAndGuardsRightOperandOverflowByLeftValue()} but for {@code ||}: the right
   * operand only evaluates when the left is false, so safety is {@code (x < 1) ∨ bound(y + 1)}.
   */
  @Test
  void lazyOrGuardsRightOperandOverflowByLeftValue() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I32), param("y", I32)),
            I32,
            block(
                ifStmt(
                    or(lt(var("x"), intLit(1)), gt(add(var("y"), intLit(1)), intLit(2))),
                    block(ret(intLit(1)))),
                ret(intLit(0))));

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp u1 =
        new FnApp(new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT), List.of(x, y));
    FnApp yPlusOne = new FnApp(TheorySymbol.ADD, List.of(y, IntValue.of(1)));
    FnApp leftTrue = new FnApp(TheorySymbol.LT, List.of(x, IntValue.of(1)));
    FnApp safety = new FnApp(TheorySymbol.OR, List.of(leftTrue, i32Bound(yPlusOne)));

    Rule errRule = lctrs.rules().get(1);
    assertEquals(u1, errRule.lhs());
    assertEquals(err(), errRule.rhs());
    assertEquals(
        Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(safety)))),
        errRule.constraint());
  }

  /**
   * {@code !} on an integer is bitwise negation, which is out of scope. It is indistinguishable
   * from logical not until the operand's sort is known, so it is rejected at translation rather
   * than lowered to ¬ over an int.
   */
  @Test
  void rejectsBitwiseNegationOnIntegerVariable() {
    assertThrows(
        UnsupportedConstructException.class,
        () ->
            translateFn(
                "f",
                List.of(param("n", I32)),
                I32,
                block(let("x", BOOL, not(var("n"))), ret(intLit(0)))));
  }

  @Test
  void rejectsBitwiseComplementOnIntegerLiteral() {
    assertThrows(
        UnsupportedConstructException.class,
        () ->
            translateFn(
                "f",
                List.of(param("n", I32)),
                I32,
                block(let("x", BOOL, not(intLit(5))), ret(intLit(0)))));
  }

  @Test
  void whileTrueWithNoBreakDiverges() {
    Lctrs lctrs = translateUnitFn("f", List.of(), block(whileStmt(boolLit(true), block())));

    TermSymbol retUnitSym = new TermSymbol("ret_unit", List.of(), Sort.RESULT);
    boolean foundUnitTail =
        lctrs.rules().stream().anyMatch(r -> r.rhs().equals(new FnApp(retUnitSym, List.of())));

    assertEquals(false, foundUnitTail);
    boolean anyGuarded = lctrs.rules().stream().anyMatch(r -> r.constraint().isPresent());
    assertEquals(false, anyGuarded);
  }

  @Test
  void whileTrueWithBreakMerges() {
    Lctrs lctrs = translateUnitFn("f", List.of(), block(whileStmt(boolLit(true), block(brk()))));

    TermSymbol retUnitSym = new TermSymbol("ret_unit", List.of(), Sort.RESULT);
    boolean foundUnitTail =
        lctrs.rules().stream().anyMatch(r -> r.rhs().equals(new FnApp(retUnitSym, List.of())));

    assertEquals(true, foundUnitTail);
  }

  @Test
  void translatesEmptyReturn() {
    Lctrs lctrs = translateUnitFn("f", List.of(), block(ret(Optional.empty())));

    // f() -> ret_unit()
    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    FnApp retUnit = new FnApp(new TermSymbol("ret_unit", List.of(), Sort.RESULT), List.of());
    Rule expected = new Rule(entry, retUnit, Optional.empty());

    assertEquals(true, lctrs.rules().contains(expected));
  }

  @Test
  void failsFastOnAUnitReturningCallInValuePosition() {
    var g = fnUnit("g", List.of(), block(ret(Optional.empty())));
    var f = fn("f", List.of(), I32, block(let("x", I32, call("g")), ret(intLit(0))));

    assertThrows(IllegalStateException.class, () -> new Translator(crate(g, f)).translate());
  }

  /**
   * Release wraps an overflowing {@code +} instead of faulting. For {@code fn f(x: i16) -> i16 {
   * let y = x + 1; y }} the bound value is {@code wrap((x + 1))} with no result-width constraint
   * and no companion {@code err} rule: the panic path is gone entirely, and the only constraint
   * left is the entry rule's parameter bound.
   */
  @Test
  void releaseWrapsAdditionWithNoErrRule() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, add(var("x"), intLit(1))), ret(var("y"))),
            IntegerSemantics.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp retWrapped =
        new FnApp(
            new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(wrap16(xPlusOne)));

    assertEquals(List.of(entryRule(entry, retWrapped, i16Bound(x))), lctrs.rules());
  }

  /**
   * Multiplication takes the identical wrap path as addition — the mod-based encoding is exact for
   * any overflow magnitude, so {@code *} is not special-cased. Same shape as {@link
   * #releaseWrapsAdditionWithNoErrRule} with {@code x * x} in place of {@code x + 1}.
   */
  @Test
  void releaseWrapsMultiplicationLikeAddition() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, mul(var("x"), var("x"))), ret(var("y"))),
            IntegerSemantics.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xTimesX = new FnApp(TheorySymbol.MUL, List.of(x, x));
    FnApp retWrapped =
        new FnApp(
            new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(wrap16(xTimesX)));

    assertEquals(List.of(entryRule(entry, retWrapped, i16Bound(x))), lctrs.rules());
  }

  /**
   * Unary minus wraps in release ({@code i16::MIN} negates to itself), so {@code let y = -x} binds
   * {@code wrap(-x)} with no {@code err} rule.
   */
  @Test
  void releaseWrapsUnaryMinus() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, neg(var("x"))), ret(var("y"))),
            IntegerSemantics.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp negX = new FnApp(TheorySymbol.NEG, List.of(x));
    FnApp retWrapped =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(wrap16(negX)));

    assertEquals(List.of(entryRule(entry, retWrapped, i16Bound(x))), lctrs.rules());
  }

  /**
   * Logical {@code !} carries no overflow, so release leaves it exactly as debug: {@code let y =
   * !b} binds {@code not(b)} unwrapped and unconstrained.
   */
  @Test
  void releaseLeavesLogicalNotUnchanged() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("b", BOOL)),
            BOOL,
            block(let("y", BOOL, not(var("b"))), ret(var("y"))),
            IntegerSemantics.release);

    VarDecl b = new VarDecl("b", Sort.BOOL);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.BOOL), Sort.RESULT), List.of(b));
    FnApp notB = new FnApp(TheorySymbol.NOT, List.of(b));
    FnApp retNotB =
        new FnApp(new TermSymbol("ret_Bool", List.of(Sort.BOOL), Sort.RESULT), List.of(notB));

    assertEquals(List.of(new Rule(entry, retNotB, Optional.empty())), lctrs.rules());
  }

  /**
   * Division panics under both rustc profiles, so its encoding is shared: the release translation
   * of {@code let y = x / z} is byte-identical to the debug one (the guarded {@code err} rule plus
   * the Euclidean-correction hoist). Asserting equality of the two outputs pins that {@code /} is
   * untouched by the wrap feature.
   */
  @Test
  void releaseLeavesDivisionIdenticalToDebug() {
    var body = block(let("y", I16, div(var("x"), var("z"))), ret(var("y")));
    var params = List.of(param("x", I16), param("z", I16));

    Lctrs debug = translateFn("f", params, I16, body, IntegerSemantics.debug);
    Lctrs release = translateFn("f", params, I16, body, IntegerSemantics.release);

    assertEquals(debug.rules(), release.rules());
  }

  /**
   * A literal-only tree has no inferable width, and rustc const-evaluates and rejects its overflow
   * at compile time under both profiles, so release emits it unwrapped. {@code let y = 2 + 3} binds
   * the plain sum {@code 2 + 3} with no wrap and no constraint; with no parameters the binding's
   * program point is a pure forward onto {@code ret_Int}, so chain removal folds the whole body
   * into the entry rule.
   */
  @Test
  void releaseEmitsLiteralOnlyArithmeticUnwrapped() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(),
            I16,
            block(let("y", I16, add(intLit(2), intLit(3))), ret(var("y"))),
            IntegerSemantics.release);

    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    FnApp twoPlusThree =
        new FnApp(
            TheorySymbol.ADD,
            List.of(new IntValue(BigInteger.TWO), new IntValue(BigInteger.valueOf(3))));
    FnApp retSum =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(twoPlusThree));

    assertEquals(List.of(new Rule(entry, retSum, Optional.empty())), lctrs.rules());
  }

  /**
   * The default constructor stays debug: the same {@code let y = x + 1} that wraps under release
   * (see {@link #releaseWrapsAdditionWithNoErrRule}) still splits into a guarded normal rule and an
   * {@code err} rule on the negated i16 bound. This is the "release changed nothing by default"
   * canary.
   */
  @Test
  void defaultSemanticsStillPanicOnOverflow() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, add(var("x"), intLit(1))), ret(var("y"))));

    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp retSum =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(xPlusOne));
    FnApp bound = i16Bound(xPlusOne);
    FnApp u1 = new FnApp(new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT), List.of(x));

    Rule errRule =
        new Rule(
            u1, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(u1, retSum, Optional.of(new Constraint(bound)));
    assertEquals(List.of(entryRule(entry, u1, i16Bound(x)), errRule, normalRule), lctrs.rules());
  }

  /**
   * The {@code let y = x + 1} that splits into a guarded rule plus an {@code err} rule under debug
   * ({@link #defaultSemanticsStillPanicOnOverflow}) and wraps under release ({@link
   * #releaseWrapsAdditionWithNoErrRule}) is just the bare sum here: no bound, no wrap, no err.
   */
  @Test
  void unboundedEmitsArithmeticWithNoBoundAndNoErrRule() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, add(var("x"), intLit(1))), ret(var("y"))),
            IntegerSemantics.unbounded);

    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp retSum =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(xPlusOne));

    assertEquals(List.of(new Rule(entry, retSum, Optional.empty())), lctrs.rules());
  }

  /**
   * Division keeps its {@code divisor ≠ 0} guard (partiality, not width) and loses only the {@code
   * MIN / -1} conjunct, there being no MIN. The three Euclidean-correction rules are untouched, so
   * the rule count matches debug's.
   */
  @Test
  void unboundedKeepsDivisorGuardButDropsMinOverNegOne() {
    var params = List.of(param("x", I16), param("z", I16));
    var body = block(let("y", I16, div(var("x"), var("z"))), ret(var("y")));

    Lctrs lctrs = translateFn("f", params, I16, body, IntegerSemantics.unbounded);

    VarDecl z = new VarDecl("z", Sort.INT);
    FnApp divisorNonZero = new FnApp(TheorySymbol.NEQ_INT, List.of(z, IntValue.of(0)));
    Constraint expected = new Constraint(new FnApp(TheorySymbol.NOT, List.of(divisorNonZero)));

    List<Rule> errRules = lctrs.rules().stream().filter(r -> r.rhs().equals(err())).toList();
    assertEquals(
        List.of(expected), errRules.stream().map(r -> r.constraint().orElseThrow()).toList());
    // Debug leads with an entry rule bounding the parameters; unbounded has no widths to bound, so
    // discount it before comparing the division encodings.
    assertEquals(
        translateFn("f", params, I16, body, IntegerSemantics.debug).rules().size() - 1,
        lctrs.rules().size());
  }

  /**
   * {@code translate()} keeps the forwarding rule a while loop's body end leaves behind, so the raw
   * LCTRS has more rules and symbols than its {@link Simplifier#simplify simplified} form, and the
   * entry symbol survives simplification.
   */
  @Test
  void translateIsRawUntilSimplified() {
    Crate crateAst =
        crate(
            fn(
                "f",
                List.of(param("x", I32)),
                I32,
                block(
                    let("y", I32, intLit(0)),
                    whileStmt(lt(var("y"), var("x")), block(assign("y", add(var("y"), intLit(1))))),
                    ret(var("y")))));

    Lctrs raw = new Translator(crateAst).translate();
    Lctrs simplified = Simplifier.simplify(raw);

    assertTrue(raw.rules().size() > simplified.rules().size());
    assertTrue(raw.sigma().size() > simplified.sigma().size());
    assertTrue(simplified.sigma().containsAll(raw.entries()));
  }
}
