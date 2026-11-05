package project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@link CoraRunner} with stand-in shell scripts, so no Cora install is needed. */
@Timeout(30)
class CoraRunnerTest {

  @TempDir Path dir;

  /** Writes an executable shell script and returns its path. */
  static Path script(Path dir, String name, String body) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, "#!/bin/sh\n" + body + "\n");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
    return file;
  }

  private static CoraRunner runner(Path bin, Duration timeout, int slots) {
    return new CoraRunner(bin, timeout, slots, Map.of());
  }

  @Test
  void returnsCorasOutputWhenItPrintsAVerdict() throws Exception {
    Path bin = script(dir, "cora", "echo YES; echo \"proof of $3\"");

    CoraRunner.Analysis analysis = runner(bin, Duration.ofSeconds(5), 1).analyse("f(x) -> x");

    assertEquals(CoraRunner.Outcome.FINISHED, analysis.outcome());
    assertTrue(analysis.output().startsWith("YES"), analysis.output());
  }

  @Test
  void passesCallByValueAndTheTranslatedSystem() throws Exception {
    Path bin = script(dir, "cora", "echo MAYBE; echo \"$1 $2\"; cat \"$3\"");

    CoraRunner.Analysis analysis = runner(bin, Duration.ofSeconds(5), 1).analyse("f(x) -> x");

    assertTrue(analysis.output().contains("--strategy cbv"), analysis.output());
    assertTrue(analysis.output().contains("f(x) -> x"), analysis.output());
  }

  @Test
  void reportsAFailureWhenNoVerdictIsPrinted() throws Exception {
    Path bin = script(dir, "cora", "echo 'Parsing error' >&2; exit 1");

    CoraRunner.Analysis analysis = runner(bin, Duration.ofSeconds(5), 1).analyse("");

    assertEquals(CoraRunner.Outcome.FAILED, analysis.outcome());
    assertTrue(analysis.output().contains("Parsing error"), analysis.output());
  }

  @Test
  void killsCoraAndItsChildrenOnTimeout() throws Exception {
    Path pidFile = dir.resolve("child.pid");
    Path bin = script(dir, "cora", "sleep 60 & echo $! > " + pidFile + "; wait");

    CoraRunner.Analysis analysis = runner(bin, Duration.ofMillis(500), 1).analyse("");

    assertEquals(CoraRunner.Outcome.TIMED_OUT, analysis.outcome());
    long child = Long.parseLong(Files.readString(pidFile).strip());
    assertFalse(
        ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false),
        "the child process outlived the timeout");
  }

  @Test
  void refusesRatherThanQueuesWhenEverySlotIsTaken() throws Exception {
    Path started = dir.resolve("started");
    Path bin = script(dir, "cora", "touch " + started + "; sleep 2; echo YES");
    CoraRunner runner = runner(bin, Duration.ofSeconds(10), 1);

    CompletableFuture<CoraRunner.Analysis> first =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return runner.analyse("");
              } catch (InterruptedException e) {
                throw new IllegalStateException(e);
              }
            });
    while (!Files.exists(started)) {
      TimeUnit.MILLISECONDS.sleep(20);
    }

    assertEquals(CoraRunner.Outcome.BUSY, runner.analyse("").outcome());
    assertEquals(CoraRunner.Outcome.FINISHED, first.get().outcome());
    assertEquals(CoraRunner.Outcome.FINISHED, runner.analyse("").outcome());
  }

  @Test
  void truncatesVeryLongOutput() throws Exception {
    Path bin = script(dir, "cora", "echo YES; head -c 400000 /dev/zero | tr '\\0' x");

    CoraRunner.Analysis analysis = runner(bin, Duration.ofSeconds(5), 1).analyse("");

    assertEquals(CoraRunner.Outcome.FINISHED, analysis.outcome());
    assertTrue(analysis.output().endsWith("[output truncated]\n"));
    assertTrue(analysis.output().length() < CoraRunner.MAX_OUTPUT + 100);
  }

  @Test
  void resolvesBareNamesOnThePath() throws Exception {
    Path bin = script(dir, "cora", "echo YES");

    assertEquals(bin, CoraRunner.resolve("cora", "/nonexistent:" + dir).orElseThrow());
    assertTrue(CoraRunner.resolve("cora", "/nonexistent").isEmpty());
    assertTrue(CoraRunner.resolve(dir.resolve("missing").toString(), null).isEmpty());
  }
}
