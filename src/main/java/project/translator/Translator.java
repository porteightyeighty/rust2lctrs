package project.translator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import project.ast.BodyBlock;
import project.ast.Crate;
import project.ast.Expression;
import project.ast.FunctionDeclaration;
import project.ast.Item;
import project.ast.Let;
import project.ast.Parameter;
import project.ast.Statement;
import project.lctrs.FnApp;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.VarDecl;

public class Translator {

  final Crate crate;

  public Translator(Crate crate) {
    this.crate = crate;
  }

  public Lctrs translate() {
    Lctrs lctrs = new Lctrs();
    for (Item item : crate.items()) {
      lctrs.appendRules(processItem(item));
    }
    return lctrs;
  }

  private List<Rule> processItem(Item item) {
    return switch (item) {
      case FunctionDeclaration fn -> {
        // A function's return sort is the shared output sort of its whole program-point family,
        // so the Context is constructed per function once that sort is known.
        Context ctx = new Context(Sort.of(fn.returnType()));
        processFunctionDeclaration(ctx, fn);
        yield ctx.rules();
      }
    };
  }

  private void processFunctionDeclaration(Context ctx, FunctionDeclaration functionDeclaration) {
    for (Parameter parameter : functionDeclaration.parameters()) {
      ctx.addToScope(VarDecl.of(parameter));
    }
    Sort functionReturnType = Sort.of(functionDeclaration.returnType());
    // Want to hand roll the first symbol so that we start with the actual function name
    List<Sort> argSorts =
        functionDeclaration.parameters().stream().map(p -> Sort.of(p.type())).toList();
    Symbol entry =
        new TermSymbol(functionDeclaration.identifier().name(), argSorts, functionReturnType);
    Term incoming = new FnApp(entry, ctx.argsFromScope());
    processBlock(ctx, functionDeclaration.block(), incoming);
  }

  private void processBlock(Context ctx, BodyBlock block, Term incoming) {
    for (Statement statement : block.leading()) {
      incoming = processStatement(ctx, statement, incoming);
    }
    // processStatement(ctx, block.returnStatement(), incoming);
  }

  private Term processStatement(Context ctx, Statement statement, Term incoming) {
    return switch (statement) {
      case Let stmt -> processLetStatement(ctx, stmt, incoming);
      default -> null;
    };
  }

  private Term processLetStatement(Context ctx, Let let, Term incoming) {
    List<Term> oldArgs = ctx.argsFromScope();
    Term value = processExpression(ctx, let.value());
    ctx.addToScope(new VarDecl(let.identifier().name(), Sort.of(let.type())));
    Symbol to = ctx.advance();
    List<Term> rhsArgs = new ArrayList<>(oldArgs);
    rhsArgs.add(value);
    Term rhs = new FnApp(to, rhsArgs);
    ctx.addRule(new Rule(incoming, rhs, Optional.empty()));
    return rhs;
  }

  private Term processExpression(Context ctx, Expression value) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'processExpression'");
  }
}
