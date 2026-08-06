package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evidence that a rejected construct reports where it is. This asserts the reported {@link Span},
 * when both thrown as {@link UnsupportedConstructException}, or collected as a {@link Diagnostic}.
 */
public class RejectionSpanTest {

  private DiagnosticRecorder diagnostics;
  private AstBuilder astBuilder;
  private ItemBuilder itemBuilder;

  @BeforeEach
  void setUp() {
    SpanTable spans = new SpanTable();
    diagnostics = new DiagnosticRecorder();
    astBuilder = new AstBuilder(spans, diagnostics);
    itemBuilder = new ItemBuilder(new SpanRecorder(spans), diagnostics);
  }

  @Test
  void thrownRejectionSpansTheOffendingConstruct() {
    String source =
        """
        fn f(
            a: i32,
            b: i32,
        ) -> f32 {
            return a;
        }
        """;
    UnsupportedConstructException e =
        assertThrows(
            UnsupportedConstructException.class,
            () -> itemBuilder.buildItem(TestHelper.parseItem(source)));
    assertSpanCovers(source, "f32", e.span());
  }

  @Test
  void collectedDiagnosticSpansTheOffendingConstruct() {
    String source =
        """
        fn f(a: i32) -> i32 {
            return a;
        }

        struct S {
            x: i32,
        }
        """;
    astBuilder.buildCrate(TestHelper.parseCrate(source));
    List<Diagnostic> recorded = diagnostics.diagnostics();
    assertEquals(1, recorded.size());
    assertSpanCovers(source, "struct S {\n    x: i32,\n}", recorded.get(0).span());
  }

  @Test
  void eachOutOfScopeConstructIsReportedSeparately() {
    String source =
        """
        fn f(a: i32) -> i32 {
            let b: f32 = 1.0;
            return a;
        }

        fn g(a: i32) -> i32 {
            for i in 0..10 {
                a = a + 1;
            }
            return a;
        }
        """;
    astBuilder.buildCrate(TestHelper.parseCrate(source));
    List<Diagnostic> recorded = diagnostics.diagnostics();
    assertEquals(2, recorded.size());
    assertSpanCovers(source, "f32", recorded.get(0).span());
    assertSpanCovers(source, "for i in 0..10 {\n        a = a + 1;\n    }", recorded.get(1).span());
  }

  /**
   * Asserts that {@code span} covers exactly {@code snippet} within {@code source}, and reports the
   * line the snippet starts on. Offsets are derived from the source rather than hardcoded, so the
   * assertion says "the span is the offending text" instead of restating character indices.
   *
   * @param source the full Rust source the span was taken from
   * @param snippet the exact text the span is expected to cover, which must occur only once
   * @param span the reported span
   */
  private static void assertSpanCovers(String source, String snippet, Span span) {
    int start = source.indexOf(snippet);
    assertTrue(start >= 0, () -> "snippet not present in source: " + snippet);
    assertEquals(
        start, source.lastIndexOf(snippet), () -> "snippet is not unique in source: " + snippet);
    assertEquals(start, span.startIndex(), "start index");
    assertEquals(start + snippet.length() - 1, span.endIndex(), "end index");
    assertEquals(
        source.substring(0, start).chars().filter(c -> c == '\n').count() + 1, span.line(), "line");
  }
}
