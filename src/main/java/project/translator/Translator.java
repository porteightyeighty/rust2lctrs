package project.translator;

import java.util.ArrayList;
import java.util.List;
import project.ast.Crate;
import project.ast.FunctionDeclaration;
import project.ast.Item;
import project.lctrs.FnApp;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.TermSymbol;
import project.lctrs.Var;

public class Translator {

  final Crate crate;

  public Translator(Crate crate) {
    this.crate = crate;
  }

  public Lctrs translate() {
    Lctrs lctrs = new Lctrs();
  }

  private List<Rule> processItem(Item item) {
    return switch (item) {
      case FunctionDeclaration i -> processFunctionDeclaration(i);
    };
  }

  private List<Rule> processFunctionDeclaration(FunctionDeclaration functionDeclaration) {
    List<Rule> rules = new ArrayList<>();
    List<Sort> argSorts =
        functionDeclaration.parameters().stream().map(p -> Sort.of(p.type())).toList();
    Sort returnType = Sort.of(functionDeclaration.returnType());
    Symbol symbol = new TermSymbol(functionDeclaration.identifier().name(), argSorts, returnType);
    Term lhs = new FnApp(symbol, functionDeclaration.parameters().stream().map(Var:of).toList());

    return rules;
  }
}
