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
import project.parser.RustParser;

public class StatementBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @ParameterizedTest
  @ValueSource(strings = {"i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128"})
  void buildsIntegerLetStatements(String type) {
    String testInput = String.format("let i: %s = 0;", type);
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    Let expected = new Let(new Identifier("i"), Type.Int.valueOf(type), new Integer(0));
    Let actual = assertInstanceOf(Let.class, astBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBooleanLetStatements() {
    String testInput = "let b: bool = true;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    Let expected = new Let(new Identifier("b"), Type.BOOL, new Boolean(true));
    Let actual = assertInstanceOf(Let.class, astBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsFloatLetStatements() {
    String testInput = "let f: f32 = 1.0;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> astBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsReturnStatement() {
    String testInput = "return 0;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    Return expected = new Return(new Integer(0));
    Return actual = assertInstanceOf(Return.class, astBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsReturnStatementWithoutExpression() {
    String testInput = "return;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> astBuilder.buildStatement(statementContext));
  }

  @Test
  void buildsIfStatement() {
    String testInput = "if 1 { return 10; }";
    RustParser.IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    If expected = new If(new Integer(1), new Block(List.of(new Return(new Integer(10)))), Optional.empty());
    assertEquals(expected, astBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void buildsIfElseStatement() {
    String testInput = "if 1 {return 10;} else { return 5; }";
    RustParser.IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    Block ifBlock = new Block(List.of(new Return(new Integer(10))));
    Block elseBlock = new Block(List.of(new Return(new Integer(5))));
    If expected = new If(new Integer(1), ifBlock, Optional.of(elseBlock));
    assertEquals(expected, astBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void buildsIfElseIfStatement() {
    String testInput = "if 1 {return 10;} else if 2 { return 5; }";
    RustParser.IfExpressionContext ifExpressionContext = TestHelper.parseIf(testInput);
    Block ifBlock = new Block(List.of(new Return(new Integer(10))));
    Block secondIfBlock = new Block(List.of(new Return(new Integer(5))));
    Block elseIfBlock = new Block(List.of(new If(new Integer(2), secondIfBlock, Optional.empty())));
    If expected = new If(new Integer(1), ifBlock, Optional.of(elseIfBlock));
    assertEquals(expected, astBuilder.buildIfStatement(ifExpressionContext));
  }

  @Test
  void rejectsReturnStatementWithoutExpression() {
    String testInput = "return;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> astBuilder.buildStatement(statementContext));
  }
}
