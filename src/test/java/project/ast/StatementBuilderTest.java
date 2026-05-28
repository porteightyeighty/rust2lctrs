package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    LetStmt expected = new LetStmt(new Identifier("i"), Type.Int.valueOf(type), new IntLit(0));
    LetStmt actual = assertInstanceOf(LetStmt.class, astBuilder.buildStatement(statementContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsReturnStatement() {
    String testInput = "return 0;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    Return expected = new Return(new IntLit(0));
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
}
