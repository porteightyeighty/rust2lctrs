package project.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import project.testsupport.Benchmark;
import project.testsupport.Benchmarks;

/**
 * End-to-end tests: hand each benchmark's committed golden LCTRS to a real Cora install and assert
 * the termination verdict declared by its {@code // cora:} marker. This is the slow safety net of
 * the three-layer strategy, so it runs in the {@code verify} phase (via the failsafe plugin's
 * {@code *IT} convention), not {@code test}.
 *
 * <p>The golden — not a fresh translation — is what gets analysed, so the snapshot layer pins the
 * exact artifact and this layer proves that pinned artifact is semantically sound. The snapshot
 * layer runs first (in {@code test}), so by {@code verify} the goldens exist and are current.
 *
 * <p>Every test {@code assumeTrue}-skips when Cora is not installed, so the absence of the external
 * binary (or Z3) turns the e2e layer into a no-op rather than a build failure. See the README's
 * "Running Cora locally" section for setup.
 */
@Timeout(90)
final class CoraE2EIT {

  @BeforeAll
  static void requireCora() {
    assumeTrue(
        Cora.isAvailable(),
        "Cora binary not found (set CORA_BIN or add it to PATH); skipping e2e tests");
  }

  /**
   * Benchmarks that declare an expected verdict; those without a {@code // cora:} marker are
   * snapshot-only and excluded here.
   */
  static List<Benchmark> verdictBenchmarks() {
    return Benchmarks.all().stream().filter(b -> b.expectedVerdict().isPresent()).toList();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("verdictBenchmarks")
  void provesExpectedVerdict(Benchmark benchmark) throws IOException, InterruptedException {
    assumeTrue(
        Files.exists(benchmark.golden()),
        () -> "Golden missing for " + benchmark.name() + "; run ./mvnw test first");

    project.testsupport.Verdict expected = benchmark.expectedVerdict().orElseThrow();
    project.testsupport.Verdict actual = Cora.run(benchmark.golden());

    assertEquals(
        expected,
        actual,
        () -> {
          try {
            return "Cora verdict for "
                + benchmark.name()
                + " was "
                + actual
                + ", expected "
                + expected
                + " for LCTRS:\n"
                + Files.readString(benchmark.golden());
          } catch (IOException e) {
            return "Cora verdict mismatch for " + benchmark.name() + " (golden unreadable)";
          }
        });
  }
}
