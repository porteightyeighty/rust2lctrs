package project.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import project.testsupport.Benchmark;
import project.testsupport.Benchmarks;
import project.testsupport.Translate;

/**
 * Golden-file (snapshot) tests: translate each benchmark's Rust source and assert the result
 * matches its committed {@code .lctrs} golden. This is the second of the three test layers — it
 * catches unintended ripple effects of a translation-rule change across whole programs, where the
 * per-rule unit tests only pin one construct.
 *
 * <p>Regenerating goldens is deliberate, not automatic. Run with {@code -Dsnapshot.update=true} to
 * rewrite the golden files from the current translator output, then review the {@code git diff} as
 * you would any other change before committing. Without that flag a missing or stale golden fails.
 */
final class SnapshotTest {

  /** System property that switches the suite from asserting to rewriting goldens. */
  private static final String UPDATE_PROPERTY = "snapshot.update";

  static java.util.List<Benchmark> benchmarks() {
    return Benchmarks.all();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("benchmarks")
  void translationMatchesGolden(Benchmark benchmark) throws IOException {
    String actual = Translate.toLctrs(Files.readString(benchmark.rust()), benchmark.profile());

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(benchmark.golden(), actual, StandardCharsets.UTF_8);
      return;
    }

    if (!Files.exists(benchmark.golden())) {
      fail(
          "No golden for benchmark '"
              + benchmark.name()
              + "' at "
              + benchmark.golden()
              + ". Generate it with: ./mvnw test -Dsnapshot.update=true (then review the diff).");
    }

    String expected = Files.readString(benchmark.golden());
    assertEquals(
        expected,
        actual,
        () ->
            "Translation of "
                + benchmark.rust()
                + " drifted from its golden "
                + benchmark.golden());
  }
}
