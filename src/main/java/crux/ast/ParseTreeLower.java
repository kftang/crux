package crux.ast;

import crux.pt.CruxBaseVisitor;
import crux.pt.CruxParser;
import crux.ast.types.*;
import crux.ast.SymbolTable.Symbol;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class will convert the parse tree generated by ANTLR to AST It follows the visitor pattern
 * where declarations will be by DeclarationVisitor Class Statements will be resolved by
 * StatementVisitor Class Expressions will be resolved by ExpressionVisitor Class
 */

public final class ParseTreeLower {
  private final DeclarationVisitor declarationVisitor = new DeclarationVisitor();
  private final StatementVisitor statementVisitor = new StatementVisitor();
  private final ExpressionVisitor expressionVisitor = new ExpressionVisitor();

  private final SymbolTable symbolTable;

  public ParseTreeLower(PrintStream err) {
    symbolTable = new SymbolTable(err);
  }

  private static Position getPosition(ParserRuleContext ctx) {
    Token start = ctx.start;
    return new Position(start.getLine());
  }

  private static Type getType(String typeString) {
    switch (typeString) {
      case "void":
        return new VoidType();
      case "bool":
        return new BoolType();
      case "int":
        return new IntType();
      default:
        // error, type is not valid
        System.out.println("getType got invalid type");
        break;
    }
    return null;
  }

  private static Type getType(CruxParser.TypeContext ctx) {
    return getType(ctx.Identifier().getText());
  }

  private static Expression getLiteral(CruxParser.LiteralContext ctx) {
    if (ctx.Integer() != null) {
      return new LiteralInt(getPosition(ctx), Integer.parseInt(ctx.Integer().getText()));
    }
    if (ctx.False() != null) {
      return new LiteralBool(getPosition(ctx), false);
    }
    if (ctx.True() != null) {
      return new LiteralBool(getPosition(ctx), true);
    }
    System.out.println("getLiteral got no value");
    return null;
  }

  /**
   *
   * @return True if any errors
   */
  public boolean hasEncounteredError() {
    return symbolTable.hasEncounteredError();
  }


  /**
   * Lower top-level parse tree to AST
   * 
   * @return a {@link DeclarationList} object representing the top-level AST.
   */

  public DeclarationList lower(CruxParser.ProgramContext program) {
    List<Declaration> declarations = program.declarationList().declaration().stream()
            .map(declaration -> declaration.accept(declarationVisitor))
            .collect(Collectors.toList());
    return new DeclarationList(getPosition(program), declarations);
  }

  /**
   * Lower statement list by lower individual statement into AST.
   * 
   * @return a {@link StatementList} AST object.
   */

  private StatementList lower(CruxParser.StatementListContext statementList) {
    List<Statement> statements = statementList.statements.stream()
            .map(statement -> statement.accept(statementVisitor))
            .collect(Collectors.toList());
    return new StatementList(getPosition(statementList), statements);
  }

  /**
   * Similar to {@link #lower(CruxParser.StatementListContext)}, but handles symbol table as well.
   * 
   * @return a {@link StatementList} AST object.
   */

  private StatementList lower(CruxParser.StatementBlockContext statementBlock) {
    symbolTable.enter();
    StatementList statementList = lower(statementBlock.statementList());
    symbolTable.exit();
    return statementList;
  }

  /**
   * A parse tree visitor to create AST nodes derived from {@link Declaration}
   */
  private final class DeclarationVisitor extends CruxBaseVisitor<Declaration> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}
     * 
     * @return an AST {@link VariableDeclaration}
     */
     
    @Override 
    public VariableDeclaration visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
      Position position = getPosition(ctx);
      String name = ctx.name.getText();
      Type type = getType(ctx.type());

      Symbol symbol = symbolTable.add(position, name, type);
      return new VariableDeclaration(position, symbol);
    }

    /**
     * Visit a parse tree array declaration and creates an AST {@link ArrayDeclaration}
     * 
     * @return an AST {@link ArrayDeclaration}
     */

    @Override
    public Declaration visitArrayDeclaration(CruxParser.ArrayDeclarationContext ctx) {
      Position position = getPosition(ctx);
      String name = ctx.name.getText();
      Type baseType = getType(ctx.type());
      long length = Integer.parseInt(ctx.length.getText());
      Type type = new ArrayType(length, baseType);

      Symbol symbol = symbolTable.add(position, name, type);
      return new ArrayDeclaration(position, symbol);
    }

    /**
     * Visit a parse tree function definition and create an AST {@link FunctionDefinition}
     * 
     * @return an AST {@link FunctionDefinition}
     */

    @Override
    public Declaration visitFunctionDefinition(CruxParser.FunctionDefinitionContext ctx) {
      Position position = getPosition(ctx);
      String name = ctx.name.getText();
      Type returnType = getType(ctx.type());
      List<Type> argumentTypes = ctx.parameterList().parameters.stream()
              .map(CruxParser.ParameterContext::type)
              .map(ParseTreeLower::getType)
              .collect(Collectors.toList());

      Type function = new FuncType(new TypeList(argumentTypes), returnType);
      Symbol symbol = symbolTable.add(position, name, function);

      // new scope inside function, add all args to sym table
      symbolTable.enter();
      List<Symbol> parameters = ctx.parameterList().parameters.stream()
              .map(parameter -> symbolTable.add(getPosition(parameter), parameter.name.getText(), getType(parameter.type())))
              .collect(Collectors.toList());

      // lower all statements in statement list
      StatementList statements = lower(ctx.body);
      symbolTable.exit();

      return new FunctionDefinition(position, symbol, parameters, statements);
    }
  }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Statement}
   */

  private final class StatementVisitor extends CruxBaseVisitor<Statement> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}. Since
     * {@link VariableDeclaration} is both {@link Declaration} and {@link Statement}, we simply
     * delegate this to
     * {@link DeclarationVisitor#visitVariableDeclaration(CruxParser.VariableDeclarationContext)} which we
     * implement earlier.
     *
     * @return an AST {@link VariableDeclaration}
     */

    @Override
    public Statement visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
      return declarationVisitor.visitVariableDeclaration(ctx);
    }

    /**
     * Visit a parse tree assignment statement and create an AST {@link Assignment}
     * 
     * @return an AST {@link Assignment}
     */
    @Override
    public Statement visitAssignmentStatement(CruxParser.AssignmentStatementContext ctx) {
      Expression designator = ctx.name.accept(expressionVisitor);
      Expression expression = ctx.value.accept(expressionVisitor);
      return new Assignment(getPosition(ctx), designator, expression);
    }

    /**
     * Visit a parse tree assignment nosemi statement and create an AST {@link Assignment}
     * 
     * @return an AST {@link Assignment}
     */

    @Override
    public Statement visitAssignmentStatementNoSemi(CruxParser.AssignmentStatementNoSemiContext ctx) {
      Expression designator = ctx.name.accept(expressionVisitor);
      Expression expression = ctx.value.accept(expressionVisitor);
      return new Assignment(getPosition(ctx), designator, expression);
    }

    /**
     * Visit a parse tree call statement and create an AST {@link Call}. Since {@link Call} is both
     * {@link Expression} and {@link Statement}, we simply delegate this to
     * {@link ExpressionVisitor#visitCallExpression(CruxParser.CallExpressionContext)} that we will
     * implement later.
     * 
     * @return an AST {@link Call}
     */

    @Override
    public Statement visitCallStatement(CruxParser.CallStatementContext ctx) {
      return expressionVisitor.visitCallExpression(ctx.callExpression());
    }

    /**
     * Visit a parse tree if-else branch and create an AST {@link IfElseBranch}. The template code
     * shows partial implementations that visit the then block and else block recursively before
     * using those returned AST nodes to construct {@link IfElseBranch} object.
     * 
     * @return an AST {@link IfElseBranch}
     */

    @Override
    public Statement visitIfStatement(CruxParser.IfStatementContext ctx) {
      Expression conditional = ctx.condition.accept(expressionVisitor);

      // construct then branch statement list
      StatementList thenBranch = lower(ctx.thenBody);

      if (ctx.elseBody != null) {
        // construct else branch statement list
        StatementList elseBranch = lower(ctx.elseBody);
        return new IfElseBranch(getPosition(ctx), conditional, thenBranch, elseBranch);
      }

      return new IfElseBranch(getPosition(ctx), conditional, thenBranch, new StatementList(getPosition(ctx), new ArrayList<>()));
    }

    /**
     * Visit a parse tree for loop and create an AST {@link For}. You'll going to use a similar
     * techniques as {@link #visitIfStatement(CruxParser.IfStatementContext)} to decompose this
     * construction.
     * 
     * @return an AST {@link For}
     */

    @Override
    public Statement visitForStatement(CruxParser.ForStatementContext ctx) {
      symbolTable.enter();
      Assignment init = (Assignment) visitAssignmentStatement(ctx.init);
      Expression condition = ctx.condition.accept(expressionVisitor);
      Assignment update = (Assignment) visitAssignmentStatementNoSemi(ctx.update);
      StatementList statements = lower(ctx.body);
      symbolTable.exit();
      return new For(getPosition(ctx), init, condition, update, statements);
    }

    /**
     * Visit a parse tree return statement and create an AST {@link Return}. Here we show a simple
     * example of how to lower a simple parse tree construction.
     * 
     * @return an AST {@link Return}
     */

    @Override
    public Statement visitReturnStatement(CruxParser.ReturnStatementContext ctx) {
      return new Return(getPosition(ctx), ctx.value.accept(expressionVisitor));
    }

    /**
     * Creates a Break node
     */

    @Override
    public Statement visitBreakStatement(CruxParser.BreakStatementContext ctx) {
      return new Break(getPosition(ctx));
    }
  }

  private final class ExpressionVisitor extends CruxBaseVisitor<Expression> {
    /**
     * Parse Expression0 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammar
     */

    @Override
    public Expression visitExpression0(CruxParser.Expression0Context ctx) {
      if (ctx.higherExpression != null) {
        return ctx.higherExpression.accept(expressionVisitor);
      }
      Expression left = ctx.left.accept(expressionVisitor);
      OpExpr.Operation op = OpExpr.Operation.get(ctx.op.getText()).get();
      Expression right = ctx.right.accept(expressionVisitor);
      return new OpExpr(getPosition(ctx), op, left, right);
    }

    /**
     * Parse Expression1 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammar
     */

    @Override
    public Expression visitExpression1(CruxParser.Expression1Context ctx) {
      if (ctx.higherExpression != null) {
        return ctx.higherExpression.accept(expressionVisitor);
      }
      Expression left = ctx.left.accept(expressionVisitor);
      OpExpr.Operation op = OpExpr.Operation.get(ctx.op.getText()).get();
      Expression right = ctx.right.accept(expressionVisitor);
      return new OpExpr(getPosition(ctx), op, left, right);
    }

    /**
     * Parse Expression2 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammar
     */
    
    @Override
    public Expression visitExpression2(CruxParser.Expression2Context ctx) {
      if (ctx.higherExpression != null) {
        return ctx.higherExpression.accept(expressionVisitor);
      }
      Expression left = ctx.left.accept(expressionVisitor);
      OpExpr.Operation op = OpExpr.Operation.get(ctx.op.getText()).get();
      Expression right = ctx.right.accept(expressionVisitor);
      return new OpExpr(getPosition(ctx), op, left, right);
    }

    /**
     * Parse Expression3 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammar
     */

    @Override
    public Expression visitExpression3(CruxParser.Expression3Context ctx) {
      if (ctx.negateExpression != null) {
        return new OpExpr(getPosition(ctx), OpExpr.Operation.LOGIC_NOT, ctx.negateExpression.accept(expressionVisitor), null);
      }
      if (ctx.precedenceExpression != null) {
        return ctx.precedenceExpression.accept(expressionVisitor);
      }
      if (ctx.designatorExpression != null) {
        return ctx.designatorExpression.accept(expressionVisitor);
      }
      if (ctx.callExpression() != null) {
        return ctx.callExpression().accept(expressionVisitor);
      }
      if (ctx.valueExpression != null) {
        return ctx.valueExpression.accept(expressionVisitor);
      }
      System.out.println("visitExpression3 got invalid value");
      return null;
    }

    /**
     * Create an Call Node
     */

     @Override
     public Call visitCallExpression(CruxParser.CallExpressionContext ctx) {
       Position position = getPosition(ctx);
       List<Expression> arguments = ctx.arguments.expressions.stream()
               .map(expression -> expression.accept(expressionVisitor))
               .collect(Collectors.toList());
       Symbol function = symbolTable.lookup(position, ctx.functionName.getText());
       return new Call(position, function, arguments);
     }

    /**
     * visitDesignator will check for a name or ArrayAccess FYI it should account for the case when
     * the designator was dereferenced
     */

     @Override
     public Expression visitDesignator(CruxParser.DesignatorContext ctx) {
       Position position = getPosition(ctx);
       Symbol variable = symbolTable.lookup(position, ctx.name.getText());
       if (ctx.index != null) {
         return new ArrayAccess(position, variable, ctx.index.accept(expressionVisitor));
       }
       return new VarAccess(position, variable);
     }

    /**
     * Create an Literal Node
     */

     @Override
     public Expression visitLiteral(CruxParser.LiteralContext ctx) {
       return getLiteral(ctx);
     }
  }
}
