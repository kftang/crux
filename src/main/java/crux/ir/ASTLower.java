package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.util.*;
import java.util.stream.Collectors;

class InstPair {
  Instruction start;
  Instruction end;
  LocalVar value;

  public InstPair(Instruction start, Instruction end, LocalVar value) {
    this.start = start;
    this.end = end;
    this.value = value;
  }

  public InstPair(Instruction instruction, LocalVar value) {
    this.start = instruction;
    this.end = instruction;
    this.value = value;
  }

  public InstPair(Instruction start, Instruction end) {
    this.start = start;
    this.end = end;
    this.value = null;
  }

  public InstPair(Instruction instruction) {
    this.start = instruction;
    this.end = instruction;
    this.value = null;
  }

  public InstPair(LocalVar value) {
    this.start = new NopInst();
    this.end = this.start;
    this.value = value;
  }

  public void setNext(Instruction next) {
    this.end.setNext(0, next);
  }

  public void setNext(InstPair next) {
    this.end.setNext(0, next.start);
  }
}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

  private Stack<NopInst> loopExits = new Stack<>();

  private BinaryOperator.Op getOp(OpExpr.Operation op) {
    switch (op) {
      case ADD:
        return BinaryOperator.Op.Add;
      case SUB:
        return BinaryOperator.Op.Sub;
      case MULT:
        return BinaryOperator.Op.Mul;
      case DIV:
        return BinaryOperator.Op.Div;
      default:
        throw new RuntimeException("op is not a binary op");
    }
  }

  private CompareInst.Predicate getPredicate(OpExpr.Operation op) {
    switch (op) {
      case GE:
        return CompareInst.Predicate.GE;
      case GT:
        return CompareInst.Predicate.GT;
      case LE:
        return CompareInst.Predicate.LE;
      case LT:
        return CompareInst.Predicate.LT;
      case EQ:
        return CompareInst.Predicate.EQ;
      case NE:
        return CompareInst.Predicate.NE;
      default:
        throw new RuntimeException("op is not a compare predicate");
    }
  }

  private LocalVar makeLocalVar(Symbol symbol) {
    // TODO: Remove
    if (mCurrentFunction == null || mCurrentLocalVarMap == null) {
      throw new RuntimeException("can't make a local var outside of a function scope");
    }
    LocalVar localVar = mCurrentFunction.getTempVar(symbol.getType(), symbol.getName());
    mCurrentLocalVarMap.put(symbol, localVar);
    return localVar;
  }

  private InstPair makeInstPair(List<? extends Node> nodes) {
    InstPair last = null;
    InstPair first = null;
    for (Node node : nodes) {
      InstPair inst = node.accept(this);
      if (inst != null) {
        if (last != null) {
          last.setNext(inst);
        } else {
          first = inst;
        }
        last = inst;
      }
    }
    if (last == null) {
      return new InstPair(new NopInst());
      // throw new RuntimeException("last and first inst are null");
    }
    return new InstPair(first.start, last.end);
  }

  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {
    mCurrentProgram = new Program();
    return makeInstPair(declarationList.getChildren());
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(), (FuncType) functionDefinition.getSymbol().getType());
    mCurrentLocalVarMap = new HashMap<>();

    List<LocalVar> args = functionDefinition.getParameters().stream()
            .map(this::makeLocalVar)
            .collect(Collectors.toList());
    mCurrentFunction.setArguments(args);
    mCurrentProgram.addFunction(mCurrentFunction);

    InstPair funcBody = functionDefinition.getStatements().accept(this);
    mCurrentFunction.setStart(funcBody.start);
    mCurrentFunction = null;
    mCurrentLocalVarMap = null;
    return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {
    return makeInstPair(statementList.getChildren());
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    if (mCurrentFunction != null) {
      makeLocalVar(variableDeclaration.getSymbol());
    } else {
      mCurrentProgram.addGlobalVar(new GlobalDecl(variableDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, 1)));
    }
    return null;
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    if (mCurrentFunction != null) {
      throw new RuntimeException("array declaration inside function");
    }
    ArrayType arrayType = (ArrayType) arrayDeclaration.getSymbol().getType();
    mCurrentProgram.addGlobalVar(new GlobalDecl(arrayDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, arrayType.getExtent())));
    return null;
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    if (mCurrentFunction != null) {
      LocalVar localVar = mCurrentLocalVarMap.get(name.getSymbol());
      if (localVar != null) {
        return new InstPair(localVar);
      } else {
        Type type = name.getSymbol().getType();
        // global var access
        AddressVar addressVar = mCurrentFunction.getTempAddressVar(type);
        localVar = mCurrentFunction.getTempVar(type);
        AddressAt addressAt = new AddressAt(addressVar, name.getSymbol());
        LoadInst loadInst = new LoadInst(localVar, addressVar);
        addressAt.setNext(0, loadInst);
        return new InstPair(addressAt, loadInst, localVar);
      }
    }
    throw new RuntimeException("var access outside of function");
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    Expression location = assignment.getLocation();
    InstPair valueInst = assignment.getValue().accept(this);
    if (location instanceof VarAccess) {
      VarAccess varAccess = (VarAccess) location;
      LocalVar localVar = mCurrentLocalVarMap.get(varAccess.getSymbol());
      if (localVar != null) {
        // location is local var access
        CopyInst copyInst = new CopyInst(localVar, valueInst.value);
        valueInst.setNext(copyInst);
        return new InstPair(valueInst.start, copyInst);
      }
      // location is global var access
      Type type = varAccess.getSymbol().getType();
      AddressVar addressVar = mCurrentFunction.getTempAddressVar(type);
      AddressAt addressAt = new AddressAt(addressVar, varAccess.getSymbol());
      StoreInst storeInst = new StoreInst(valueInst.value, addressVar);
      addressAt.setNext(0, valueInst.start);
      valueInst.setNext(storeInst);
      return new InstPair(addressAt, storeInst);
    }
    // location is array
    ArrayAccess arrayAccess = (ArrayAccess) location;
    InstPair indexInst = arrayAccess.getIndex().accept(this);
    AddressVar addressVar = mCurrentFunction.getTempAddressVar(arrayAccess.getBase().getType());
    AddressAt addressAt = new AddressAt(addressVar, arrayAccess.getBase(), indexInst.value);
    StoreInst storeInst = new StoreInst(valueInst.value, addressVar);
    indexInst.setNext(addressAt);
    addressAt.setNext(0, valueInst.start);
    valueInst.setNext(storeInst);
    return new InstPair(indexInst.start, storeInst);
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    List<LocalVar> args = new ArrayList<>();
    InstPair last = null;
    InstPair first = null;
    for (Node node : call.getArguments()) {
      InstPair inst = node.accept(this);
      if (inst != null) {
        if (last != null) {
          last.setNext(inst);
        } else {
          first = inst;
        }
        last = inst;
        args.add(inst.value);
      }
    }
    if (last == null) {
      throw new RuntimeException("last and first inst are null");
    }
    if (call.getType() instanceof VoidType) {
      CallInst callInst = new CallInst(call.getCallee(), args);
      last.setNext(callInst);
      return new InstPair(first.start, callInst);
    }
    LocalVar returnVar = mCurrentFunction.getTempVar(call.getType());
    CallInst callInst = new CallInst(returnVar, call.getCallee(), args);
    last.setNext(callInst);
    return new InstPair(first.start, callInst, returnVar);
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    InstPair lhs = operation.getLeft().accept(this);
    LocalVar localVar = mCurrentFunction.getTempVar(operation.getType());
    if (operation.getRight() == null) {
      // unary not
      UnaryNotInst unaryNotInst = new UnaryNotInst(localVar, lhs.value);
      lhs.setNext(unaryNotInst);
      return new InstPair(lhs.start, unaryNotInst, localVar);
    }
    InstPair rhs = operation.getRight().accept(this);
    switch (operation.getOp()) {
      case LOGIC_AND: {
        JumpInst jumpInst = new JumpInst(lhs.value);
        CopyInst falseCopyInst = new CopyInst(localVar, rhs.value);
        CopyInst trueCopyInst = new CopyInst(localVar, lhs.value);
        NopInst endInst = new NopInst();
        jumpInst.setNext(0, rhs.start);
        jumpInst.setNext(1, trueCopyInst);
        rhs.setNext(falseCopyInst);
        falseCopyInst.setNext(0, endInst);
        trueCopyInst.setNext(0, endInst);
        return new InstPair(lhs.start, endInst, localVar);
      }
      case LOGIC_OR: {
        JumpInst jumpInst = new JumpInst(lhs.value);
        CopyInst falseCopyInst = new CopyInst(localVar, lhs.value);
        CopyInst trueCopyInst = new CopyInst(localVar, rhs.value);
        NopInst endInst = new NopInst();
        jumpInst.setNext(0, falseCopyInst);
        jumpInst.setNext(1, rhs.start);
        rhs.setNext(trueCopyInst);
        trueCopyInst.setNext(0, endInst);
        falseCopyInst.setNext(0, endInst);
        return new InstPair(lhs.start, endInst, localVar);
      }
      case ADD:
      case SUB:
      case MULT:
      case DIV: {
        lhs.setNext(rhs);
        BinaryOperator.Op op = getOp(operation.getOp());
        BinaryOperator binaryOperator = new BinaryOperator(op, localVar, lhs.value, rhs.value);
        rhs.setNext(binaryOperator);
        return new InstPair(lhs.start, binaryOperator, localVar);
      }
      case GE:
      case GT:
      case LE:
      case LT:
      case EQ:
      case NE: {
        lhs.setNext(rhs);
        CompareInst.Predicate predicate = getPredicate(operation.getOp());
        CompareInst compareInst = new CompareInst(localVar, predicate, lhs.value, rhs.value);
        rhs.setNext(compareInst);
        return new InstPair(lhs.start, compareInst, localVar);
      }
    }
    throw new RuntimeException("invalid operation");
  }

  private InstPair visit(Expression expression) {
    return null;
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    // location is array
    LocalVar localVar = mCurrentFunction.getTempVar(access.getType());
    InstPair indexInst = access.getIndex().accept(this);
    AddressVar addressVar = mCurrentFunction.getTempAddressVar(access.getType());
    AddressAt addressAt = new AddressAt(addressVar, access.getBase(), indexInst.value);
    LoadInst loadInst = new LoadInst(localVar, addressVar);
    indexInst.setNext(addressAt);
    addressAt.setNext(0, loadInst);
    return new InstPair(indexInst.start, loadInst, localVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    LocalVar localVar = mCurrentFunction.getTempVar(literalBool.getType());
    CopyInst copyInst = new CopyInst(localVar, BooleanConstant.get(mCurrentProgram, literalBool.getValue()));
    return new InstPair(copyInst, localVar);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    LocalVar localVar = mCurrentFunction.getTempVar(literalInt.getType());
    CopyInst copyInst = new CopyInst(localVar, IntegerConstant.get(mCurrentProgram, literalInt.getValue()));
    return new InstPair(copyInst, localVar);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    InstPair valueInst = ret.getValue().accept(this);
    ReturnInst returnInst = new ReturnInst(valueInst.value);
    valueInst.setNext(returnInst);
    return new InstPair(valueInst.start, returnInst);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    return new InstPair(loopExits.peek(), new NopInst());
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    InstPair condInst = ifElseBranch.getCondition().accept(this);
    InstPair thenInst = ifElseBranch.getThenBlock().accept(this);
    NopInst endInst = new NopInst();
    JumpInst jumpInst = new JumpInst(condInst.value);
    condInst.setNext(jumpInst);
    thenInst.setNext(endInst);
    if (ifElseBranch.getElseBlock().getChildren().size() > 0) {
      InstPair elseInst = ifElseBranch.getElseBlock().accept(this);
      jumpInst.setNext(0, elseInst.start);
      elseInst.setNext(endInst);
    } else {
      jumpInst.setNext(0, endInst);
    }
    jumpInst.setNext(1, thenInst.start);
    return new InstPair(condInst.start, endInst);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {
    NopInst endInst = new NopInst();
    loopExits.push(endInst);
    InstPair initInst = loop.getInit().accept(this);
    InstPair condInst = loop.getCond().accept(this);
    InstPair bodyInst = loop.getBody().accept(this);
    InstPair incInst = loop.getIncrement().accept(this);
    JumpInst checkCond = new JumpInst(condInst.value);
    checkCond.setNext(0, endInst);
    checkCond.setNext(1, bodyInst.start);
    condInst.setNext(checkCond);
    bodyInst.setNext(incInst);
    incInst.setNext(checkCond);
    loopExits.pop();
    return new InstPair(initInst.start, endInst);
  }
}
