package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser.ItemContext;

public class ItemBuilderTest {

  private SpanTable spans;
  private DiagnosticRecorder diagnostics;
  private ItemBuilder itemBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    diagnostics = new DiagnosticRecorder();
    itemBuilder = new ItemBuilder(new SpanRecorder(spans), diagnostics);
  }

  @Test
  void buildsFunctionDeclarationWithoutParams() {
    String testInput = "fn x() -> i32 { return 10; }";
    ItemContext itemContext = TestHelper.parseItem(testInput);
    Block block = new Block(List.of(new Return(new IntegerLiteral(BigInteger.valueOf(10)))));
    FunctionDeclaration expected =
        new FunctionDeclaration(new Identifier("x"), List.of(), block, Optional.of(Type.Int.i32));
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, itemBuilder.buildItem(itemContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsFunctionDeclarationWithParams() {
    String testInput = "fn y(a: i32) -> i32 { return 10; }";
    ItemContext itemmContext = TestHelper.parseItem(testInput);
    Parameter param = new Parameter(new Identifier("a"), Type.Int.i32);
    Block block = new Block(List.of(new Return(new IntegerLiteral(BigInteger.valueOf(10)))));
    FunctionDeclaration expected =
        new FunctionDeclaration(
            new Identifier("y"), List.of(param), block, Optional.of(Type.Int.i32));
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, itemBuilder.buildItem(itemmContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fn x(self) -> i32 { return 10; }", // self param
        "async fn x() -> i32 {return 10; }", // async qualifier
        "unsafe fn x() -> i32 {return 10; }", // unsafe qualifier
        "const fn x() -> i32 {return 10; }", // const qualifier
        "extern fn x() -> i32 {return 10; }", // extern qualifier
        "extern \"C\" fn x() -> i32 {return 10; }", // extern "C" qualifier
        "fn x<T>(a: T) -> i32 {return 10; }", // generic parameters
      })
  void rejectsUnsupportedFunctionForms(String input) {
    ItemContext ctx = TestHelper.parseItem(input);
    assertThrows(UnsupportedConstructException.class, () -> itemBuilder.buildItem(ctx));
  }

  @Test
  void collectsUnsupportedParameterAsDiagnostic() {
    // Rejected inside the per-parameter loop, which collects rather than aborting, so the
    // rejection surfaces as a recorded diagnostic carrying the specific throw-site message.
    ItemContext ctx = TestHelper.parseItem("fn x(...) -> i32 { return 10; }"); // variadic parameter
    itemBuilder.buildItem(ctx);
    List<Diagnostic> recorded = diagnostics.diagnostics();
    assertEquals(1, recorded.size());
    assertEquals("Varargs are not supported", recorded.get(0).message());
  }

  @Test
  void buildsFunctionDeclarationWithMultipleParams() {
    String testInput = "fn z(a: i32, b: bool) -> i32 { return 10; }";
    ItemContext itemContext = TestHelper.parseItem(testInput);
    Parameter first = new Parameter(new Identifier("a"), Type.Int.i32);
    Parameter second = new Parameter(new Identifier("b"), Type.BOOL);
    Block block = new Block(List.of(new Return(new IntegerLiteral(BigInteger.valueOf(10)))));
    FunctionDeclaration expected =
        new FunctionDeclaration(
            new Identifier("z"), List.of(first, second), block, Optional.of(Type.Int.i32));
    FunctionDeclaration actual =
        assertInstanceOf(FunctionDeclaration.class, itemBuilder.buildItem(itemContext));
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
    assertThrows(UnsupportedConstructException.class, () -> itemBuilder.buildItem(ctx));
  }
}
