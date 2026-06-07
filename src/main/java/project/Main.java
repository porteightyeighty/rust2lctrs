package project;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.SpanTable;
import project.lctrs.Lctrs;
import project.lctrs.Serialiser;
import project.parser.RustLexer;
import project.parser.RustParser;
import project.translator.Translator;

public class Main {
  static void main() {
    String input = "fn main() -> i32 {let i: i32 = 0; let x: i32 = i + 5; return x;}";
    CharStream inputStream = CharStreams.fromString(input);
    RustLexer lexer = new RustLexer(inputStream);
    TokenStream tokens = new CommonTokenStream(lexer);
    RustParser parser = new RustParser(tokens);
    RustParser.CrateContext crateCtx = parser.crate();
    System.out.println(crateCtx.toStringTree(parser));
    SpanTable spanTable = new SpanTable();
    AstBuilder astBuilder = new AstBuilder(spanTable);
    Crate crate = astBuilder.buildCrate(crateCtx);
    Translator translator = new Translator(crate);
    Lctrs lctrs = translator.translate();
    System.out.println(Serialiser.serialise(lctrs));
  }
}
