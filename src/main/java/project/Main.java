package project;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.parser.RustLexer;
import project.parser.RustParser;

public class Main {
  static void main() {
    String input = "fn main() -> i32 {let i: i32 = 0; let x: i32 = i + 5; x}";
    CharStream inputStream = CharStreams.fromString(input);
    RustLexer lexer = new RustLexer(inputStream);
    TokenStream tokens = new CommonTokenStream(lexer);
    RustParser parser = new RustParser(tokens);
    RustParser.CrateContext crateCtx = parser.crate();
    System.out.println(crateCtx.toStringTree(parser));
    AstBuilder astBuilder = new AstBuilder();
    Crate crate = astBuilder.buildCrate(crateCtx);
    System.out.println(crate);
  }
}
