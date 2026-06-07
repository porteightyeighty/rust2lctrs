package project.translator;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import project.ast.Assignment;
import project.ast.BinaryOp;
import project.ast.Block;
import project.ast.BooleanLiteral;
import project.ast.Break;
import project.ast.Continue;
import project.ast.Crate;
import project.ast.Expression;
import project.ast.FunctionDeclaration;
import project.ast.Identifier;
import project.ast.If;
import project.ast.IntegerLiteral;
import project.ast.Item;
import project.ast.Let;
import project.ast.Loop;
import project.ast.Parameter;
import project.ast.Return;
import project.ast.Statement;
import project.ast.Type;
import project.ast.Variable;
import project.ast.While;
import project.lctrs.Lctrs;

/**
 * Test-only DSL for hand-building {@link project.ast} fragments without going through the ANTLR
 * parser or {@link project.ast.AstBuilder}. Keeps {@code translate()}-driven tests readable: a
 * whole function is one nested factory call rather than a page of {@code new} expressions.
 *
 * <p>Static factories only — meant to be statically imported: {@code import static
 * project.translator.Ast.*;}. Names are short on purpose; this is glue, not production code.
 */
final class AstHelper {

  private AstHelper() {}

  // --- types (the two the fragment has) ------------------------------------

  static final Type I32 = Type.Int.i32;
  static final Type BOOL = Type.BOOL;

  // --- identifiers ---------------------------------------------------------

  static Identifier id(String name) {
    return new Identifier(name);
  }

  // --- expressions ---------------------------------------------------------

  static IntegerLiteral intLit(long value) {
    return new IntegerLiteral(BigInteger.valueOf(value));
  }

  static BooleanLiteral boolLit(boolean value) {
    return new BooleanLiteral(value);
  }

  static Variable var(String name) {
    return new Variable(id(name));
  }

  static BinaryOp bin(BinaryOp.Op op, Expression left, Expression right) {
    return new BinaryOp(op, left, right);
  }

  static BinaryOp add(Expression l, Expression r) {
    return bin(BinaryOp.Op.ADD, l, r);
  }

  static BinaryOp sub(Expression l, Expression r) {
    return bin(BinaryOp.Op.SUB, l, r);
  }

  static BinaryOp mul(Expression l, Expression r) {
    return bin(BinaryOp.Op.MUL, l, r);
  }

  static BinaryOp eq(Expression l, Expression r) {
    return bin(BinaryOp.Op.EQ, l, r);
  }

  static BinaryOp lt(Expression l, Expression r) {
    return bin(BinaryOp.Op.LT, l, r);
  }

  // --- statements ----------------------------------------------------------

  static Let let(String name, Type type, Expression value) {
    return new Let(id(name), type, value);
  }

  static Assignment assign(String name, Expression value) {
    return new Assignment(id(name), value);
  }

  static Return ret(Expression value) {
    return new Return(value);
  }

  static If ifStmt(Expression cond, Block thenBlock) {
    return new If(cond, thenBlock, Optional.empty());
  }

  static If ifElse(Expression cond, Block thenBlock, Block elseBlock) {
    return new If(cond, thenBlock, Optional.of(elseBlock));
  }

  static While whileStmt(Expression cond, Block block) {
    return new While(cond, block);
  }

  static Loop loop(Block block) {
    return new Loop(block);
  }

  static Break brk() {
    return new Break();
  }

  static Continue cont() {
    return new Continue();
  }

  // --- blocks --------------------------------------------------------------

  /** A scoped block. */
  static Block block(Statement... statements) {
    return new Block(List.of(statements));
  }

  // --- functions and crates ------------------------------------------------

  static Parameter param(String name, Type type) {
    return new Parameter(id(name), type);
  }

  static FunctionDeclaration fn(String name, List<Parameter> params, Type returnType, Block body) {
    return new FunctionDeclaration(id(name), params, body, returnType);
  }

  static Crate crate(Item... items) {
    return new Crate(List.of(items));
  }

  // --- one-call convenience for the common case ----------------------------

  /**
   * Builds a single-function crate and translates it, returning the resulting LCTRS so the test can
   * assert over {@link Lctrs#rules()}.
   *
   * @param name the function name (also the entry program-point symbol)
   * @param params the parameter list
   * @param returnType the declared return type
   * @param body the function body
   * @return the translated LCTRS
   */
  static Lctrs translateFn(String name, List<Parameter> params, Type returnType, Block body) {
    return new Translator(crate(fn(name, params, returnType, body))).translate();
  }
}
