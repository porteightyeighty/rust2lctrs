package project.translator;

import java.util.ArrayList;
import java.util.List;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.VarDecl;

final class Context {

  private int counter = 0;
  private List<VarDecl> scope = new ArrayList<>();
  private final List<Symbol> sigma = new ArrayList<>();
  private final List<Rule> rules = new ArrayList<>();
  private final Sort returnSort;

  Context(Sort returnSort) {
    this.returnSort = returnSort;
  }

  Symbol advance() {
    counter++;
    Symbol s = symbolFor(counter);
    sigma.add(s);
    return s;
  }

  List<Term> argsFromScope() {
    return List.<Term>copyOf(scope);
  }

  void addToScope(VarDecl var) {
    scope.add(var);
  }

  void shrinkScope(int newSize) {
    scope = scope.subList(0, newSize);
  }

  void addRule(Rule r) {
    rules.add(r);
  }

  List<Rule> rules() {
    return List.copyOf(rules);
  }

  private Symbol symbolFor(int counter) {
    String notation = "u" + String.valueOf(counter);
    List<Sort> argSorts = scope.stream().map((v) -> v.sort()).toList();
    return new TermSymbol(notation, argSorts, returnSort);
  }
}
