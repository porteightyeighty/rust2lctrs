package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static project.translator.AstHelper.BOOL;
import static project.translator.AstHelper.I16;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.boolLit;
import static project.translator.AstHelper.call;
import static project.translator.AstHelper.crate;
import static project.translator.AstHelper.div;
import static project.translator.AstHelper.fn;
import static project.translator.AstHelper.fnUnit;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.mul;
import static project.translator.AstHelper.neg;
import static project.translator.AstHelper.not;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
import static project.translator.AstHelper.translateFn;
import static project.translator.AstHelper.translateUnitFn;
import static project.translator.AstHelper.var;
import static project.translator.AstHelper.whileStmt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import project.ast.UnsupportedConstructException;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
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
   * {@code fn f(n: i32) -> i32 { let x = n + 1; x }}. The point of interest is that the bound value
   * on the right-hand side is evaluated over the <em>pre-binding</em> scope: the first slot is the
   * live variable {@code n} and the second is {@code n + 1}, never {@code x}. Because {@code n + 1}
   * can overflow, the entry splits into a guarded normal rule and an {@code err} rule on the
   * negated bound.
   */
  @Test
  void letBindingEvaluatesValueOverPreBindingScope() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, nPlusOne));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            entry, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(entry, u1, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u1scope, retX, Optional.empty());
    assertEquals(List.of(errRule, normalRule, retRule), lctrs.rules());
  }

  /**
   * Two sequential {@code let} bindings thread their configurations: the outgoing configuration of
   * the first rule is the incoming configuration (left-hand side) of the second. For {@code fn f(n:
   * i32) -> i32 { let x = n + 1; let y = x; x }} this gives {@code f(n) -> u1(n, n + 1)} followed
   * by {@code u1(n, x) -> u2(n, x, x)}: the {@code u1} that closes rule 1 reopens as the head of
   * rule 2's left-hand side, now over the scope variables.
   */
  @Test
  void sequentialLetsThreadConfigurations() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), let("y", I32, var("x")), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, nPlusOne));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);

    FnApp u2 = new FnApp(u2Symbol, List.of(n, x, x));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x, y));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));

    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            entry, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    // Only the first let (n + 1) can overflow; let y = x carries no bound, so rules 3 and 4 thread
    // configurations unconstrained.
    Rule normalRule = new Rule(entry, u1, Optional.of(new Constraint(bound)));
    Rule expected2 = new Rule(u1scope, u2, Optional.empty());
    Rule expected3 = new Rule(u2scope, retX, Optional.empty());

    assertEquals(List.of(errRule, normalRule, expected2, expected3), lctrs.rules());
  }

  /**
   * A shadowing {@code let} stays a distinct LCTRS variable rather than collapsing onto the binding
   * it shadows. For {@code fn f(n: i32) -> i32 { let x = n; let x = x + 1; x }} the second {@code
   * x} mints a fresh name {@code x_1}: its value {@code x + 1} is evaluated over the <em>outer</em>
   * {@code x}.
   */
  @Test
  void shadowingLetMintsDistinctVariable() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, var("n")), let("x", I32, add(var("x"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl xShadow = new VarDecl("x_1", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, n));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp u2 = new FnApp(u2Symbol, List.of(n, x, xPlusOne));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x, xShadow));
    FnApp retShadow =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(xShadow));

    FnApp bound = i32Bound(xPlusOne);
    // let x = n carries no bound (single unguarded rule); only the shadowing let x = x + 1
    // overflows.
    Rule firstLet = new Rule(entry, u1, Optional.empty());
    Rule errRule =
        new Rule(
            u1scope,
            err(),
            Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(u1scope, u2, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u2scope, retShadow, Optional.empty());

    assertEquals(List.of(firstLet, errRule, normalRule, retRule), lctrs.rules());
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
  void translatesUnitFunctionWithWhileLoop() {
    Lctrs lctrs =
        translateUnitFn(
            "f", List.of(), block(whileStmt(boolLit(true), block(ret(Optional.empty())))));

    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    TermSymbol retUnitSym = new TermSymbol("ret_unit", List.of(), Sort.RESULT);

    // There will be a merge point for the while loop, which falls through to the unit return tail
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
  void rejectsCallingAUnitReturningFunction() {
    var g = fnUnit("g", List.of(), block(ret(Optional.empty())));
    var f = fn("f", List.of(), I32, block(let("x", I32, call("g")), ret(intLit(0))));

    assertThrows(
        UnsupportedConstructException.class, () -> new Translator(crate(g, f)).translate());
  }

  /**
   * Release wraps an overflowing {@code +} instead of faulting. For {@code fn f(x: i16) -> i16 {
   * let y = x + 1; y }} the bound value is {@code wrap((x + 1))} and the entry rule carries neither
   * a width constraint nor a companion {@code err} rule — the panic path is gone entirely.
   */
  @Test
  void releaseWrapsAdditionWithNoErrRule() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, add(var("x"), intLit(1))), ret(var("y"))),
            Profile.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(x, wrap16(xPlusOne)));
    FnApp u1scope = new FnApp(u1Symbol, List.of(x, y));
    FnApp retY = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(y));

    Rule normalRule = new Rule(entry, u1, Optional.empty());
    Rule retRule = new Rule(u1scope, retY, Optional.empty());
    assertEquals(List.of(normalRule, retRule), lctrs.rules());
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
            Profile.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xTimesX = new FnApp(TheorySymbol.MUL, List.of(x, x));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(x, wrap16(xTimesX)));
    FnApp u1scope = new FnApp(u1Symbol, List.of(x, y));
    FnApp retY = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(y));

    assertEquals(
        List.of(new Rule(entry, u1, Optional.empty()), new Rule(u1scope, retY, Optional.empty())),
        lctrs.rules());
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
            Profile.release);

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp negX = new FnApp(TheorySymbol.NEG, List.of(x));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(x, wrap16(negX)));
    FnApp u1scope = new FnApp(u1Symbol, List.of(x, y));
    FnApp retY = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(y));

    assertEquals(
        List.of(new Rule(entry, u1, Optional.empty()), new Rule(u1scope, retY, Optional.empty())),
        lctrs.rules());
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
            Profile.release);

    VarDecl b = new VarDecl("b", Sort.BOOL);
    VarDecl y = new VarDecl("y", Sort.BOOL);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.BOOL), Sort.RESULT), List.of(b));
    FnApp notB = new FnApp(TheorySymbol.NOT, List.of(b));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.BOOL, Sort.BOOL), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(b, notB));
    FnApp u1scope = new FnApp(u1Symbol, List.of(b, y));
    FnApp retY = new FnApp(new TermSymbol("ret_Bool", List.of(Sort.BOOL), Sort.RESULT), List.of(y));

    assertEquals(
        List.of(new Rule(entry, u1, Optional.empty()), new Rule(u1scope, retY, Optional.empty())),
        lctrs.rules());
  }

  /**
   * Division panics in both profiles, so its encoding is shared: the release translation of {@code
   * let y = x / z} is byte-identical to the debug one (the guarded {@code err} rule plus the
   * Euclidean-correction hoist). Asserting equality of the two profiles' output pins that {@code /}
   * is untouched by the wrap feature.
   */
  @Test
  void releaseLeavesDivisionIdenticalToDebug() {
    var body = block(let("y", I16, div(var("x"), var("z"))), ret(var("y")));
    var params = List.of(param("x", I16), param("z", I16));

    Lctrs debug = translateFn("f", params, I16, body, Profile.debug);
    Lctrs release = translateFn("f", params, I16, body, Profile.release);

    assertEquals(debug.rules(), release.rules());
  }

  /**
   * A literal-only tree has no inferable width, and rustc const-evaluates and rejects its overflow
   * at compile time in both profiles, so release emits it unwrapped. {@code let y = 2 + 3} binds
   * the plain sum {@code 2 + 3} with no wrap and no constraint.
   */
  @Test
  void releaseEmitsLiteralOnlyArithmeticUnwrapped() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(),
            I16,
            block(let("y", I16, add(intLit(2), intLit(3))), ret(var("y"))),
            Profile.release);

    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    FnApp twoPlusThree =
        new FnApp(
            TheorySymbol.ADD,
            List.of(new IntValue(BigInteger.TWO), new IntValue(BigInteger.valueOf(3))));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(twoPlusThree));
    FnApp u1scope = new FnApp(u1Symbol, List.of(y));
    FnApp retY = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(y));

    assertEquals(
        List.of(new Rule(entry, u1, Optional.empty()), new Rule(u1scope, retY, Optional.empty())),
        lctrs.rules());
  }

  /**
   * The default constructor stays debug: the same {@code let y = x + 1} that wraps under release
   * (see {@link #releaseWrapsAdditionWithNoErrRule}) still splits into a guarded normal rule and an
   * {@code err} rule on the negated i16 bound. This is the "release changed nothing by default"
   * canary.
   */
  @Test
  void defaultProfileStillPanicsOnOverflow() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("x", I16)),
            I16,
            block(let("y", I16, add(var("x"), intLit(1))), ret(var("y"))));

    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(x, xPlusOne));
    FnApp u1scope = new FnApp(u1Symbol, List.of(x, y));
    FnApp retY = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(y));
    FnApp bound = i16Bound(xPlusOne);

    Rule errRule =
        new Rule(
            entry, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(entry, u1, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u1scope, retY, Optional.empty());
    assertEquals(List.of(errRule, normalRule, retRule), lctrs.rules());
  }
}
