package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;

  private final String[] argsRegisters = { "%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9" };
  private final Map<Variable, Integer> variableSlots;
  private int nextVariableSlot;

  private int getVariableSlot(Variable variable) {
    if (variableSlots.containsKey(variable)) {
      return variableSlots.get(variable);
    }
    variableSlots.put(variable, nextVariableSlot);
    return nextVariableSlot++;
  }

  private int getOffset(int variableSlot) {
    return -8 * variableSlot;
  }

  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
    variableSlots = new HashMap<>();
    nextVariableSlot = 0;
  }

  private void genCode(Function function, int[] labelCount) {
    variableSlots.clear();
    nextVariableSlot = function.getArguments().size();
    int stackSpace = function.getNumTempVars() + function.getNumTempAddressVars() + function.getArguments().size();
    if (stackSpace % 2 == 1) {
      stackSpace++;
    }
    out.printCodef(".globl %s", function.getName());
    out.printLabel(function.getName());
    HashMap<Instruction, String> labels = function.assignLabels(labelCount);
    // prologue
    out.printCodef("enter $(8 * %d), $0", stackSpace);
    for (int i = 0; i < Math.min(function.getArguments().size(), argsRegisters.length); i++) {
      out.printCodef("movq %s, %d(%%rbp)", argsRegisters[i], getOffset(i + 1));
    }
    // iterate through start and visit each instruction
    Stack<Instruction> instructions = new Stack<>();
    instructions.push(function.getStart());
    while (!instructions.isEmpty()) {
      Instruction instruction = instructions.pop();
      if (labels.containsKey(instruction)) {
        out.printLabel(labels.get(instruction));
      }
      instruction.accept(this);
      for (int i = 0; i < instruction.numNext(); i++) {
        instructions.push(instruction.getNext(i));
      }
    }
    // epilogue
    out.printCode("leave");
    out.printCode("ret");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    for (Iterator<GlobalDecl> it = p.getGlobals(); it.hasNext(); ) {
      GlobalDecl global = it.next();
      String name = global.getSymbol().getName();
      int size = (int) global.getNumElement().getValue();
      out.printCodef(".comm %s, %d, 8", name, size);
    }
    int[] labelCount = { 0 };
    for (Iterator<Function> it = p.getFunctions(); it.hasNext(); ) {
      Function function = it.next();
      genCode(function, labelCount);
    }
    out.close();
  }

  public void visit(AddressAt i) {
    String varName = i.getBase().getName();
    int destinationSlot = getVariableSlot(i.getDst());
    out.printCodef("movq %s@GOTPCREL(%%rip) , %%rdx", varName);
    if (i.getOffset() != null) {
      int indexSlot = getVariableSlot(i.getOffset());
      out.printCodef("movq %d(%%rbp), %%rax", getOffset(indexSlot));
      out.printCode("shl $3, %%rax");
      out.printCode("addq %%rdx, %%rax");
    }
    out.printCodef("movq %%rax %d(%%rbp)", getOffset(destinationSlot));
  }

  public void visit(BinaryOperator i) {
    int lhsSlot = getVariableSlot(i.getLeftOperand());
    int rhsSlot = getVariableSlot(i.getRightOperand());
    int destinationSlot = getVariableSlot(i.getDst());
    out.printCodef("movq %d(%%rbp), %%rax", getOffset(lhsSlot));
    String instruction = "addq";
    switch (i.getOperator()) {
      case Sub:
        instruction = "subq";
        break;
      case Div:
        instruction = "idiv";
        break;
      case Mul:
        instruction = "imul";
    }
    out.printCodef("%s %d(%%rbp), %rax");
  }

  public void visit(CompareInst i) {}

  public void visit(CopyInst i) {}

  public void visit(JumpInst i) {}

  public void visit(LoadInst i) {}

  public void visit(NopInst i) {}

  public void visit(StoreInst i) {}

  public void visit(ReturnInst i) {}

  public void visit(CallInst i) {}

  public void visit(UnaryNotInst i) {}
}
