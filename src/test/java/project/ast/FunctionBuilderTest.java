package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser;

public class FunctionBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @Test
  void buildsFunctionDeclarationWithoutParams() {
    String testInput = "fn x() -> i32 { return 10; }";
    RustParser.ItemContext itemContext = TestHelper.parseItem(testInput);
    Block block = new Block(List.of(new Return(new IntLit(10))));
    FunctionDef expected = new FunctionDef(new Identifier("x"), List.of(), block, Type.Int.i32);
    FunctionDef actual = assertInstanceOf(FunctionDef.class, astBuilder.buildItem(itemContext));
    assertEquals(expected, actual);
  }
}
