package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {
    private Symbol currentFunctionSymbol = null;
    private boolean lastStatementReturns = false;

    @Override
    public Void visit(ArrayAccess access) {
      // type check the index
      access.getIndex().accept(this);

      setNodeType(access, access.getBase().getType().index(getType(access.getIndex())));
      return null;
    }

    @Override
    public Void visit(VarAccess vaccess) {
      setNodeType(vaccess, vaccess.getSymbol().getType());
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      var arrayType = (ArrayType) arrayDeclaration.getSymbol().getType();
      var type = arrayType.getBase();
      if (type.getClass() != IntType.class && type.getClass() != BoolType.class) {
        addTypeError(arrayDeclaration, "Invalid type in array declaration");
      }
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      var type = variableDeclaration.getSymbol().getType();
      if (type.getClass() != IntType.class && type.getClass() != BoolType.class) {
        addTypeError(variableDeclaration, "Invalid type in variable declaration");
      }
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      // visit children's types
      assignment.getChildren().forEach(node -> node.accept(this));

      var assignType = getType(assignment.getLocation()).assign(getType(assignment.getValue()));
      setNodeType(assignment, assignType);
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Break brk) {
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Call call) {
      // visit children's types
      call.getChildren().forEach(node -> node.accept(this));

      // get type list for args
      var argsTypeList = new TypeList(call.getArguments().stream().map(TypeChecker.this::getType).collect(Collectors.toList()));

      // set type of this node
      setNodeType(call, call.getCallee().getType().call(argsTypeList));
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(DeclarationList declarationList) {
      declarationList.getChildren().forEach(node -> node.accept(this));
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      var functionSymbol = functionDefinition.getSymbol();
      var funcType = (FuncType) functionSymbol.getType();
      boolean isMain = functionSymbol.getName().equals("main");
      if (isMain) {
        if (funcType.getRet().getClass() != VoidType.class) {
          addTypeError(functionDefinition, "The return type of main function must be void");
        }
        if (!funcType.getArgs().isEmpty()) {
          addTypeError(functionDefinition, "The main function must have no arguments");
        }
      }
      for (var type : funcType.getArgs()) {
        if (type.getClass() != IntType.class && type.getClass() != BoolType.class) {
          addTypeError(functionDefinition, "Invalid type in function parameter");
        }
      }

      currentFunctionSymbol = functionDefinition.getSymbol();
      functionDefinition.getStatements().accept(this);

      if (isMain && funcType.getRet().getClass() != VoidType.class && !lastStatementReturns) {
        addTypeError(functionDefinition, "Non-void function does not return");
      }
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      ifElseBranch.getCondition().accept(this);
      if (getType(ifElseBranch.getCondition()).getClass() != BoolType.class) {
        addTypeError(ifElseBranch, "Condition is not a boolean expression");
      }
      ifElseBranch.getThenBlock().accept(this);
      ifElseBranch.getElseBlock().accept(this);
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(For forloop) {
      forloop.getCond().accept(this);
      if (getType(forloop.getCond()).getClass() != BoolType.class) {
        addTypeError(forloop, "Condition is not a boolean expression");
      }
      forloop.getInit().accept(this);
      forloop.getBody().accept(this);
      forloop.getIncrement().accept(this);
      return null;
    }

    @Override
    public Void visit(OpExpr opExpr) {
      opExpr.getLeft().accept(this);
      var leftType = getType(opExpr.getLeft());
      var op = opExpr.getOp();
      if (op != OpExpr.Operation.LOGIC_NOT) {
        opExpr.getRight().accept(this);
        var rightType = getType(opExpr.getRight());
        opExpr.getRight().accept(this);
        Type exprType = null;
        switch (op) {
          case EQ:
          case GE:
          case GT:
          case LE:
          case LT:
          case NE:
            exprType = leftType.compare(rightType);
            break;
          case ADD:
            exprType = leftType.add(rightType);
            break;
          case DIV:
            exprType = leftType.div(rightType);
            break;
          case SUB:
            exprType = leftType.sub(rightType);
            break;
          case MULT:
            exprType = leftType.mul(rightType);
            break;
          case LOGIC_OR:
            exprType = leftType.or(rightType);
            break;
          case LOGIC_AND:
            exprType = leftType.and(rightType);
            break;
        }
        if (exprType != null) {
          setNodeType(opExpr, exprType);
        }
      } else {
        setNodeType(opExpr, getType(opExpr.getLeft()).not());
      }
      return null;
    }

    @Override
    public Void visit(Return ret) {
      ret.getValue().accept(this);
      var valueType = getType(ret.getValue());
      var funcType = (FuncType) currentFunctionSymbol.getType();
      if (!funcType.getRet().equivalent(valueType)) {
        addTypeError(ret, "The return value's type does not match function's return type");
      }
      lastStatementReturns = true;
      return null;
    }

    @Override
    public Void visit(StatementList statementList) {
      boolean first = true;
      for (var statement : statementList.getChildren()) {
        if (!first && lastStatementReturns) {
          addTypeError(statementList, "Unreachable statement");
        }
        statement.accept(this);
        first = false;
      }
      return null;
    }
  }
}
