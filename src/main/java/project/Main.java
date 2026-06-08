package project;

import picocli.CommandLine;
import project.cli.Rust2LctrsCommand;

public class Main {
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Rust2LctrsCommand()).execute(args);
    System.exit(exitCode);
  }
}
