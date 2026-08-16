package project.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import project.translator.IntegerSemantics;

/**
 * Argument wiring only: which {@link IntegerSemantics} a command line resolves to, and that the two
 * ways of picking one can't be combined. Translation behaviour lives in {@code project.translator}.
 */
class Rust2LctrsCommandTest {

  private static Rust2LctrsCommand parse(String... args) {
    Rust2LctrsCommand command = new Rust2LctrsCommand();
    new CommandLine(command).parseArgs(args);
    return command;
  }

  @Test
  void rejectsProfileAndUnboundedTogether() {
    assertThrows(
        CommandLine.MutuallyExclusiveArgsException.class,
        () -> parse("--profile", "release", "--unbounded", "x.rs"));
  }

  @Test
  void rejectsUnboundedAsAProfile() {
    assertThrows(
        CommandLine.ParameterException.class, () -> parse("--profile", "unbounded", "x.rs"));
  }

  @Test
  void defaultsToDebug() {
    assertEquals(IntegerSemantics.debug, parse("x.rs").ints.semantics());
  }

  @Test
  void resolvesEachFlagToItsSemantics() {
    assertEquals(IntegerSemantics.release, parse("--profile", "release", "x.rs").ints.semantics());
    assertEquals(IntegerSemantics.unbounded, parse("--unbounded", "x.rs").ints.semantics());
  }
}
