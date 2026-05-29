package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser.BlockExpressionContext;
import project.parser.RustParser.ItemContext;

public class ItemBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @Test
  void buildsBodyBlockWithTrailingOnly() {
    String testInput = "{ return 10; }";
    BlockExpressionContext blockContext = TestHelper.parseBlock(testInput);
    BodyBlock expected = new BodyBlock(List.of(), new Return(new Integer(10)));
    assertEquals(expected, astBuilder.buildBodyBlock(blockContext));
  }

  @Test
  void buildsBodyBlock() {
    String testInput = "{ let x: i32 = 0; return 10; }";
    BlockExpressionContext blockContext = TestHelper.parseBlock(testInput);
    Let letStmt = new Let(new Identifier("x"), Type.Int.i32, new Integer(0));
    BodyBlock expected = new BodyBlock(List.of(letStmt), new Return(new Integer(10)));
    assertEquals(expected, astBuilder.buildBodyBlock(blockContext));
  }

  @Test
  void buildsFunctionDeclarationWithoutParams() {
    String testInput = "fn x() -> i32 { return 10; }";
    ItemContext itemContext = TestHelper.parseItem(testInput);
    BodyBlock block = new BodyBlock(List.of(), new Return(new Integer(10)));
    FunctionDeclaration expected =
        new FunctionDeclaration(new Identifier("x"), List.of(), block, Type.Int.i32);
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, astBuilder.buildItem(itemContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsFunctionDeclarationWithParams() {
    String testInput = "fn y(a: i32) -> i32 { return 10; }";
    ItemContext itemmContext = TestHelper.parseItem(testInput);
    Parameter param = new Parameter(new Identifier("a"), Type.Int.i32);
    BodyBlock block = new BodyBlock(List.of(), new Return(new Integer(10)));
    FunctionDeclaration expected =
        new FunctionDeclaration(new Identifier("y"), List.of(param), block, Type.Int.i32);
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, astBuilder.buildItem(itemmContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fn y() { return 10; }", // missing return type
        "fn x(self) -> i32 { return 10; }", // self param
        "async fn x() -> i32 {return 10; }", // async qualifier
        "unsafe fn x() -> i32 {return 10; }", // unsafe qualifier
        "const fn x() -> i32 {return 10; }", // const qualifier
        "extern fn x() -> i32 {return 10; }", // extern qualifier
        "extern \"C\" fn x() -> i32 {return 10; }", // extern "C" qualifier
        "fn x<T>(a: T) -> i32 {return 10; }", // generic parameters
        "fn x(...) -> i32 { return 10; }", // variadic parameters
      })
  void rejectsUnsupportedFunctionForms(String input) {
    ItemContext ctx = TestHelper.parseItem(input);
    assertThrows(UnsupportedConstructException.class, () -> astBuilder.buildItem(ctx));
  }

  @Test
  void buildsFunctionDeclarationWithMultipleParams() {
    String testInput = "fn z(a: i32, b: bool) -> i32 { return 10; }";
    ItemContext itemContext = TestHelper.parseItem(testInput);
    Parameter first = new Parameter(new Identifier("a"), Type.Int.i32);
    Parameter second = new Parameter(new Identifier("b"), Type.BOOL);
    BodyBlock block = new BodyBlock(List.of(), new Return(new Integer(10)));
    FunctionDeclaration expected =
        new FunctionDeclaration(new Identifier("z"), List.of(first, second), block, Type.Int.i32);
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, astBuilder.buildItem(itemContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "struct S { x: i32 }", // struct
        "enum E { A, B }", // enum
        "union U { x: i32 }", // union
        "trait T {}", // trait
        "impl S {}", // impl
        "use std::collections::HashMap;", // use declaration
        "mod m {}", // module
        "const C: i32 = 0;", // const item
        "static S: i32 = 0;", // static item
      })
  void rejectsNonFunctionItems(String input) {
    ItemContext ctx = TestHelper.parseItem(input);
    assertThrows(UnsupportedConstructException.class, () -> astBuilder.buildItem(ctx));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}", // empty block
        "{ let x: i32 = 0; }", // missing return
        "{ let x: i32 = 0; x }" // expression return
      })
  void rejectsUnsupportedBodyBlockForms(String input) {
    BlockExpressionContext blockExpressionContext = TestHelper.parseBlock(input);
    assertThrows(
        UnsupportedConstructException.class,
        () -> astBuilder.buildBodyBlock(blockExpressionContext));
  }
}
