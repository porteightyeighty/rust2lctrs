package project.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import project.testsupport.Benchmark;
import project.testsupport.Benchmarks;
import project.testsupport.Translate;
import project.translator.IntegerSemantics;

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

  static List<Benchmark> benchmarks() {
    return Benchmarks.all();
  }

  /**
   * Guards the other direction of the corpus mapping: discovery walks {@code .rs} to golden, so a
   * golden left behind by a renamed source or a deleted marker would otherwise sit in the
   * per-semantics subdirectories forever, never asserted and never regenerated.
   */
  @Test
  void everyGoldenBelongsToABenchmark() throws IOException {
    Set<Path> claimed = benchmarks().stream().map(Benchmark::golden).collect(Collectors.toSet());

    List<Path> orphans = new ArrayList<>();
    for (IntegerSemantics semantics : IntegerSemantics.values()) {
      Path dir = Benchmarks.DIR.resolve(semantics.name());
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.list(dir)) {
        files
            .filter(p -> p.getFileName().toString().endsWith(".lctrs"))
            .filter(p -> !claimed.contains(p))
            .forEach(orphans::add);
      }
    }
    orphans.sort(null);

    assertTrue(
        orphans.isEmpty(),
        () ->
            "Golden files with no benchmark claiming them: "
                + orphans
                + ". Either restore the source's marker or delete the golden.");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("benchmarks")
  void translationMatchesGolden(Benchmark benchmark) throws IOException {
    String actual = Translate.toLctrs(Files.readString(benchmark.rust()), benchmark.semantics());

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.createDirectories(benchmark.golden().getParent());
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
