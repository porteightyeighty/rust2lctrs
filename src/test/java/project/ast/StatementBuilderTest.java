package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser.BlockExpressionContext;
import project.parser.RustParser.IfExpressionContext;
import project.parser.RustParser.StatementContext;

public class StatementBuilderTest {

  private SpanTable spans;
  private StatementBuilder statementBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    statementBuilder = new StatementBuilder(new SpanRecorder(spans));
  }

  @ParameterizedTest
  @ValueSource(strings = {"i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128"})
  void buildsIntegerLetStatements(String type) {
    String testInput = String.format("let i: %s = 0;", type);
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Let expected = new Let(new Identifier("i"), Type.Int.valueOf(type), new Integer(0));
    Let actual = assertInstanceOf(Let.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBooleanLetStatements() {
    String testInput = "let b: bool = true;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Let expected = new Let(new Identifier("b"), Type.BOOL, new Boolean(true));
    Let actual = assertInstanceOf(Let.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsMutableLetStatement() {
    String testInput = "let mut x: i32 = 0;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Let expected = new Let(new Identifier("x"), Type.Int.i32, new Integer(0));
    Let actual = assertInstanceOf(Let.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsAssignmentStatement() {
    String testInput = "x = 5;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Assignment expected = new Assignment(new Identifier("x"), new Integer(5));
    Assignment actual =
        assertInstanceOf(Assignment.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        ";", // empty statement
        "fn inner() -> i32 { return 1; }", // nested item
        "println!();", // macro invocation
      })
  void rejectsUnsupportedStatements(String input) {
    StatementContext statementContext = TestHelper.parseStmt(input);
    assertThrows(
        UnsupportedConstructException.class, () -> statementBuilder.buildStatement(statementContext));
  }

  @Test
  void rejectsFloatLetStatements() {
    String testInput = "let f: f32 = 1.0;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> statementBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsReturnStatement() {
    String testInput = "return 0;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Return expected = new Return(new Integer(0));
    Return actual = assertInstanceOf(Return.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsReturnStatementWithoutExpression() {
    String testInput = "return;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> statementBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsIfStatement() {
    String testInput = "if 1 { return 10; }";
    IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    If expected =
        new If(new Integer(1), new Block(List.of(new Return(new Integer(10)))), Optional.empty());
    assertEquals(expected, statementBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void buildsIfElseStatement() {
    String testInput = "if 1 {return 10;} else { return 5; }";
    IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    Block ifBlock = new Block(List.of(new Return(new Integer(10))));
    Block elseBlock = new Block(List.of(new Return(new Integer(5))));
    If expected = new If(new Integer(1), ifBlock, Optional.of(elseBlock));
    assertEquals(expected, statementBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void buildsIfElseIfStatement() {
    String testInput = "if 1 {return 10;} else if 2 { return 5; }";
    IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    Block ifBlock = new Block(List.of(new Return(new Integer(10))));
    Block secondIfBlock = new Block(List.of(new Return(new Integer(5))));
    Block elseIfBlock = new Block(List.of(new If(new Integer(2), secondIfBlock, Optional.empty())));
    If expected = new If(new Integer(1), ifBlock, Optional.of(elseIfBlock));
    assertEquals(expected, statementBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void buildsWhileStatement() {
    String testInput = "while true { return 1; }";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    While expected = new While(new Boolean(true), new Block(List.of(new Return(new Integer(1)))));
    While actual = assertInstanceOf(While.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsWhileStatementWithEmptyBody() {
    String testInput = "while true {}";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    While expected = new While(new Boolean(true), new Block(List.of()));
    While actual = assertInstanceOf(While.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsLoopStatement() {
    String testInput = "loop { break; }";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Loop expected = new Loop(new Block(List.of(new Break())));
    Loop actual = assertInstanceOf(Loop.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsLoopStatementWithEmptyBody() {
    String testInput = "loop {}";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    Loop expected = new Loop(new Block(List.of()));
    Loop actual = assertInstanceOf(Loop.class, statementBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBreakStatement() {
    String testInput = "break;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertEquals(new Break(), statementBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsContinueStatement() {
    String testInput = "continue;";
    StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertEquals(new Continue(), statementBuilder.buildStatement(statementContext));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "for i in 0..10 { break; }", // iterator loop
        "while let x = 5 { break; }", // predicate-pattern loop
        "'outer: loop { break; }", // labelled loop
        "'outer: while true { break; }", // labelled while
      })
  void rejectsUnsupportedLoops(String input) {
    StatementContext statementContext = TestHelper.parseStmt(input);
    assertThrows(
        UnsupportedConstructException.class, () -> statementBuilder.buildStatement(statementContext));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "break 'outer;", // labelled break
        "break 5;", // break with value
        "continue 'outer;", // labelled continue
      })
  void rejectsUnsupportedBreakContinue(String input) {
    StatementContext statementContext = TestHelper.parseStmt(input);
    assertThrows(
        UnsupportedConstructException.class, () -> statementBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsBodyBlockWithTrailingOnly() {
    String testInput = "{ return 10; }";
    BlockExpressionContext blockContext = TestHelper.parseBlock(testInput);
    BodyBlock expected = new BodyBlock(List.of(), new Return(new Integer(10)));
    assertEquals(expected, statementBuilder.buildBodyBlock(blockContext));
  }

  @Test
  void buildsBodyBlock() {
    String testInput = "{ let x: i32 = 0; return 10; }";
    BlockExpressionContext blockContext = TestHelper.parseBlock(testInput);
    Let letStmt = new Let(new Identifier("x"), Type.Int.i32, new Integer(0));
    BodyBlock expected = new BodyBlock(List.of(letStmt), new Return(new Integer(10)));
    assertEquals(expected, statementBuilder.buildBodyBlock(blockContext));
  }

  @Test
  void normalisesTrailingExpressionToImplicitReturn() {
    String testInput = "{ let x: i32 = 0; x }";
    BlockExpressionContext blockContext = TestHelper.parseBlock(testInput);
    Let letStmt = new Let(new Identifier("x"), Type.Int.i32, new Integer(0));
    BodyBlock expected =
        new BodyBlock(List.of(letStmt), new Return(new Variable(new Identifier("x"))));
    assertEquals(expected, statementBuilder.buildBodyBlock(blockContext));
  }

  @Test
  void implicitAndExplicitReturnsAreEquivalent() {
    BodyBlock implicit = statementBuilder.buildBodyBlock(TestHelper.parseBlock("{ 10 }"));
    BodyBlock explicit = statementBuilder.buildBodyBlock(TestHelper.parseBlock("{ return 10; }"));
    assertEquals(explicit, implicit);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}", // empty block
        "{ let x: i32 = 0; }", // missing return
        "{ if true { return 1; } else { return 2; } }" // trailing block expression
      })
  void rejectsUnsupportedBodyBlockForms(String input) {
    BlockExpressionContext blockExpressionContext = TestHelper.parseBlock(input);
    assertThrows(
        UnsupportedConstructException.class,
        () -> statementBuilder.buildBodyBlock(blockExpressionContext));
  }
}
