package project;

import picocli.CommandLine;
import project.cli.Rust2LctrsCommand;

/** Command-line entry point: dispatches to the {@link Rust2LctrsCommand} picocli command. */
public class Main {

  /**
   * Runs the {@code rust2lctrs} command and exits the JVM with its status code.
   *
   * @param args the command-line arguments passed to the picocli command
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Rust2LctrsCommand()).execute(args);
    System.exit(exitCode);
  }
}
