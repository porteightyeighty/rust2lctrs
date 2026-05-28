package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser;

public class StatementBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  // TODO: add tests for different integer types
  @Test
  void buildsIntegerLetStatement() {
    String testInput = "let i: i32 = 0;";
    RustParser.StatementContext statementContext = TestHelper.parseStmt(testInput);
    LetStmt expected = new LetStmt(new Identifier("i"), Type.Int.i32, new IntLit(0));
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
}
