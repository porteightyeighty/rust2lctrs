package project.translator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import project.ast.BinaryOp;
import project.ast.BodyBlock;
import project.ast.BooleanLiteral;
import project.ast.Crate;
import project.ast.Expression;
import project.ast.FunctionDeclaration;
import project.ast.IntegerLiteral;
import project.ast.Item;
import project.ast.Let;
import project.ast.Parameter;
import project.ast.Statement;
import project.ast.Variable;
import project.lctrs.BoolValue;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
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

  private Term processExpression(Context ctx, Expression expression) {
    return switch (expression) {
      case IntegerLiteral l -> new IntValue(l.value());
      case BooleanLiteral l -> new BoolValue(l.value());
      case BinaryOp op -> {
        Term left = processExpression(ctx, op.left());
        Term right = processExpression(ctx, op.right());
        Symbol theorySymbol = theorySymbolFor(op.operator(), left.sort());
        yield new FnApp(theorySymbol, List.of(left, right));
      }
      case Variable v -> ctx.resolve(v.name().name());
    };
  }

  /**
   * Resolves a binary operator to its theory symbol.
   *
   * @param op the AST binary operator
   * @param operandSort the shared sort of both operands for equality operators
   * @return the corresponding theory symbol
   */
  private static TheorySymbol theorySymbolFor(BinaryOp.Op op, Sort operandSort) {
    return switch (op) {
      case ADD -> TheorySymbol.ADD;
      case SUB -> TheorySymbol.SUB;
      case MUL -> TheorySymbol.MUL;
      case DIV -> TheorySymbol.DIV;
      case MOD -> TheorySymbol.MOD;
      case LT -> TheorySymbol.LT;
      case LE -> TheorySymbol.LE;
      case GT -> TheorySymbol.GT;
      case GE -> TheorySymbol.GE;
      case EQ -> operandSort == Sort.BOOL ? TheorySymbol.EQ_BOOL : TheorySymbol.EQ_INT;
      case NE -> operandSort == Sort.BOOL ? TheorySymbol.NEQ_BOOL : TheorySymbol.NEQ_INT;
    };
  }
}
