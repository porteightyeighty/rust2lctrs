package project.translator;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import project.ast.BinaryOp;
import project.ast.BooleanLiteral;
import project.ast.Expression;
import project.ast.FunctionCall;
import project.ast.IntegerLiteral;
import project.ast.Type;
import project.ast.UnaryMinus;
import project.ast.UnaryNot;
import project.ast.Variable;
import project.lctrs.BoolValue;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TheorySymbol;

/**
 * Lowers a {@link project.ast.Expression} to a theory term, and derives the safety formula under
 * which that expression evaluates without panicking (integer overflow, division or remainder by
 * zero, and the {@code MIN / -1} edge case), following Fuhs, Kop &amp; Nishida (2017), §8.1.
 *
 * <p>This is the expression-level half of translation, split out from {@link Translator} so the
 * statement/control-flow lowering and rule emission stay separate from term construction and the
 * overflow analysis. It is a pure layer: every method depends only on {@link Context#resolve} to
 * read variable sorts and source widths, and emits no rewrite rules. Rule emission (routing a
 * faulting expression to {@code err}) is the caller's job in {@link Translator}.
 *
 * <p>All methods are static and the class is not instantiable.
 */
final class ExpressionLowering {

  private ExpressionLowering() {}

  record Corrected(Term guard, Term value) {}

  record DivisionHoist(Optional<Type.Int> width, Term safety, List<Corrected> alternatives) {}

  static DivisionHoist hoistInfo(Context ctx, BinaryOp division) {
    var left = lower(ctx, division.left());
    var right = lower(ctx, division.right());
    var quotient = new FnApp(TheorySymbol.DIV, List.of(left, right));
    var remainder = new FnApp(TheorySymbol.MOD, List.of(left, right));
    var aNeg = new FnApp(TheorySymbol.LT, List.of(left, IntValue.of(0)));
    var remNonZero = new FnApp(TheorySymbol.NEQ_INT, List.of(remainder, IntValue.of(0)));
    var agree =
        new FnApp(
            TheorySymbol.OR,
            List.of(
                new FnApp(TheorySymbol.GE, List.of(left, IntValue.of(0))),
                new FnApp(TheorySymbol.EQ_INT, List.of(remainder, IntValue.of(0)))));
    var divPos =
        new FnApp(
            TheorySymbol.AND,
            List.of(
                new FnApp(TheorySymbol.AND, List.of(aNeg, remNonZero)),
                new FnApp(TheorySymbol.GT, List.of(right, IntValue.of(0)))));
    var divNeg =
        new FnApp(
            TheorySymbol.AND,
            List.of(
                new FnApp(TheorySymbol.AND, List.of(aNeg, remNonZero)),
                new FnApp(TheorySymbol.LT, List.of(right, IntValue.of(0)))));
    List<Corrected> corrected =
        switch (division.operator()) {
          case DIV -> {
            List<Corrected> corrections =
                List.of(
                    new Corrected(agree, quotient),
                    new Corrected(
                        divPos, new FnApp(TheorySymbol.ADD, List.of(quotient, IntValue.of(1)))),
                    new Corrected(
                        divNeg, new FnApp(TheorySymbol.SUB, List.of(quotient, IntValue.of(1)))));
            yield corrections;
          }
          case MOD -> {
            List<Corrected> corrections =
                List.of(
                    new Corrected(agree, remainder),
                    new Corrected(divPos, new FnApp(TheorySymbol.SUB, List.of(remainder, right))),
                    new Corrected(divNeg, new FnApp(TheorySymbol.ADD, List.of(remainder, right))));
            yield corrections;
          }
          default ->
              throw new IllegalArgumentException(
                  "hoistInfo on non-division: " + division.operator());
        };

    return new DivisionHoist(
        inferWidth(ctx, division), safety(ctx, division).orElseThrow(), corrected);
  }

  /**
   * Translates an expression to a theory term over the current scope. Literals become theory
   * values, binary operations become applications of the corresponding theory symbol, and variables
   * resolve to their declaration in scope.
   *
   * @param ctx the per-function translation state
   * @param expression the expression to translate
   * @return the term denoting the expression
   */
  static Term lower(Context ctx, Expression expression) {
    return switch (expression) {
      case IntegerLiteral expr -> new IntValue(expr.value());
      case BooleanLiteral expr -> new BoolValue(expr.value());
      case BinaryOp expr -> {
        Term left = lower(ctx, expr.left());
        Term right = lower(ctx, expr.right());
        Symbol theorySymbol = theorySymbolFor(expr.operator(), left.sort());
        yield new FnApp(theorySymbol, List.of(left, right));
      }
      case Variable expr -> ctx.resolve(expr.name()).varDecl();
      case UnaryNot expr -> new FnApp(TheorySymbol.NOT, List.of(lower(ctx, expr.operand())));
      case UnaryMinus expr -> new FnApp(TheorySymbol.NEG, List.of(lower(ctx, expr.operand())));
      case FunctionCall expr ->
          throw new IllegalStateException("FunctionCall should have been hoisted before lowering");
    };
  }

  /**
   * The formula under which {@code expression} evaluates without panicking — the conjunction of the
   * overflow and division-safety clauses of the expression and its sub-expressions. Empty when the
   * expression cannot fault (e.g. a literal, a variable, or an arithmetic tree with no
   * width-tracked operands and no division). Callers conjoin this onto their normal-path guards and
   * route the negation to {@code err}.
   *
   * @param ctx the per-function translation state
   * @param expression the expression about to be evaluated into a rule's right-hand side
   * @return the safety formula, or empty if the expression cannot fault
   */
  static Optional<Term> safety(Context ctx, Expression expression) {
    return switch (expression) {
      case IntegerLiteral e -> Optional.empty();
      case BooleanLiteral e -> Optional.empty();
      case Variable e -> Optional.empty();
      // ¬ cannot fault; its operand still can (e.g. a nested division), so propagate that.
      case UnaryNot e -> safety(ctx, e.operand());
      // -x overflows on -MIN (debug panic), so guard the result width alongside the operand's own.
      case UnaryMinus e -> conjoin(safety(ctx, e.operand()), withinWidth(ctx, e));
      case BinaryOp expr -> {
        // Each node carries its own bound (withinWidth below); recursing collects the operands' own
        // overflow clauses, so this node and its children are bounded in separate roles.
        Optional<Term> leftFree = safety(ctx, expr.left());
        Optional<Term> rightFree = safety(ctx, expr.right());
        Optional<Term> combined = conjoin(leftFree, rightFree);
        Optional<Term> clause =
            switch (expr.operator()) {
              // Encodes Rust's *debug* semantics: overflowing +,-,* panics. In release these
              // wrap (two's-complement), which is equally well-defined, so guarding the width
              // here is a deliberate choice of the panicking model — unlike DIV/MOD below, whose
              // checks fire in release too and so are unconditional Rust semantics.
              case ADD, SUB, MUL -> withinWidth(ctx, expression);
              case DIV, MOD -> {
                Term divisorNonZero =
                    new FnApp(
                        TheorySymbol.NEQ_INT,
                        List.of(lower(ctx, expr.right()), new IntValue(BigInteger.valueOf(0))));
                // Rust panics on MIN / -1 and MIN % -1: the true quotient MAX+1 is unrepresentable.
                // A result bound only catches DIV (MIN / -1 lands out of range); MIN % -1 evaluates
                // to 0, which is in range, so it would slip through. Guard the precise overflow
                // condition for both operators instead: unsafe iff left = MIN and right = -1.
                yield conjoin(Optional.of(divisorNonZero), notMinOverNegOne(ctx, expr));
              }
              // Comparisons (GT..NE): bool-valued, so no overflow clause; operands are already
              // bounded via combined.
              default -> Optional.empty();
            };
        yield conjoin(combined, clause);
      }
      case FunctionCall expr ->
          throw new IllegalStateException("FunctionCall should have been hoisted before lowering");
    };
  }

  /**
   * Conjoins two optional formulae, treating empty as "no clause": the conjunction of two present
   * formulae, the single present one when the other is empty, or empty when both are.
   *
   * @param a the first formula, or empty
   * @param b the second formula, or empty
   * @return their conjunction, or whichever is present, or empty
   */
  static Optional<Term> conjoin(Optional<Term> a, Optional<Term> b) {
    if (a.isEmpty()) {
      return b;
    }
    if (b.isEmpty()) {
      return a;
    }
    return Optional.of(new FnApp(TheorySymbol.AND, List.of(a.get(), b.get())));
  }

  /**
   * The bound clause {@code w.min <= e <= w.max} asserting that {@code expression} stays within its
   * inferred integer width. Empty when the width cannot be inferred (e.g. literal-only operands),
   * in which case no overflow clause is contributed.
   *
   * @param ctx the per-function translation state
   * @param expression the arithmetic expression to bound
   * @return the in-range clause, or empty if the operand width is unknown
   */
  private static Optional<Term> withinWidth(Context ctx, Expression expression) {
    return inferWidth(ctx, expression)
        .map(
            w -> {
              Term t = lower(ctx, expression);
              Term lo = new FnApp(TheorySymbol.LE, List.of(new IntValue(w.min()), t));
              Term hi = new FnApp(TheorySymbol.LE, List.of(t, new IntValue(w.max())));
              return new FnApp(TheorySymbol.AND, List.of(lo, hi));
            });
  }

  /**
   * Infers the integer width of an expression, taken from the first width-tracked variable operand
   * encountered (left-biased for binary operations). Literals carry no width of their own.
   *
   * @param ctx the per-function translation state
   * @param expression the expression whose width to infer
   * @return the integer width, or empty if no width-tracked operand is present
   */
  private static Optional<Type.Int> inferWidth(Context ctx, Expression expression) {
    return switch (expression) {
      case Variable v ->
          ctx.resolve(v.name()).sourceType() instanceof Type.Int i
              ? Optional.of(i)
              : Optional.empty();
      case IntegerLiteral e -> Optional.empty();
      case BooleanLiteral e -> Optional.empty();
      case UnaryNot e -> Optional.empty(); // bool-valued, carries no integer width
      case UnaryMinus e -> inferWidth(ctx, e.operand());
      case BinaryOp e -> inferWidth(ctx, e.left()).or(() -> inferWidth(ctx, e.right()));
      case FunctionCall expr ->
          throw new IllegalStateException("FunctionCall should have been hoisted before lowering");
    };
  }

  /**
   * The DIV/MOD overflow guard {@code ¬(left = MIN ∧ right = -1)}, where MIN is the minimum value
   * of the operands' integer width. Empty when that width cannot be inferred (e.g. literal-only
   * operands), matching {@link #withinWidth}'s convention of guarding only width-tracked terms.
   *
   * @param ctx the per-function translation state
   * @param expr the division or remainder expression to guard
   * @return the negated MIN/-1 condition, or empty if the operand width is unknown
   */
  private static Optional<Term> notMinOverNegOne(Context ctx, BinaryOp expr) {
    return inferWidth(ctx, expr)
        .map(
            w -> {
              Term leftIsMin =
                  new FnApp(
                      TheorySymbol.EQ_INT, List.of(lower(ctx, expr.left()), new IntValue(w.min())));
              Term rightIsNegOne =
                  new FnApp(
                      TheorySymbol.EQ_INT,
                      List.of(lower(ctx, expr.right()), new IntValue(BigInteger.valueOf(-1))));
              return new FnApp(
                  TheorySymbol.NOT,
                  List.of(new FnApp(TheorySymbol.AND, List.of(leftIsMin, rightIsNegOne))));
            });
  }

  /**
   * Resolves a binary operator to its theory symbol.
   *
   * @param op the AST binary operator
   * @param operandSort the shared sort of both operands for equality operators
   * @return the corresponding theory symbol
   */
  private static TheorySymbol theorySymbolFor(BinaryOp.Op op, Sort operandSort) {
    return switch (op) {
      case ADD -> TheorySymbol.ADD;
      case SUB -> TheorySymbol.SUB;
      case MUL -> TheorySymbol.MUL;
      case DIV -> TheorySymbol.DIV;
      case MOD -> TheorySymbol.MOD;
      case LT -> TheorySymbol.LT;
      case LE -> TheorySymbol.LE;
      case GT -> TheorySymbol.GT;
      case GE -> TheorySymbol.GE;
      case EQ -> operandSort == Sort.BOOL ? TheorySymbol.EQ_BOOL : TheorySymbol.EQ_INT;
      case NE -> operandSort == Sort.BOOL ? TheorySymbol.NEQ_BOOL : TheorySymbol.NEQ_INT;
    };
  }
}
