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
  HashMap<Instruction, String> labels;
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
    nextVariableSlot = 1;
  }

  private void genCode(Function function, int[] labelCount) {
    variableSlots.clear();
    nextVariableSlot = Math.max(function.getArguments().size() + 1, 1);
    int stackSpace = function.getNumTempVars() + function.getNumTempAddressVars() + function.getArguments().size();
    if (stackSpace % 2 == 1) {
      stackSpace++;
    }
    out.print(String.format(".globl %s", function.getName()));
    out.printLabel(function.getName());
    labels = function.assignLabels(labelCount);
    // prologue
    out.printCodef("enter $(8 * %d), $0", stackSpace);
    for (int i = 0; i < Math.min(function.getArguments().size(), argsRegisters.length); i++) {
      variableSlots.put(function.getArguments().get(i), i + 1);
      out.printCodef("movq %s, %d(%%rbp)", argsRegisters[i], getOffset(i + 1));
    }
    for (int i = argsRegisters.length; i < function.getArguments().size(); i++) {
      variableSlots.put(function.getArguments().get(i), i + 1);
      int argOffset = ((i - argsRegisters.length) + 2) * 8;
      out.printCodef("movq %d(%%rbp), %%rdx", argOffset);
      out.printCodef("movq %%rdx, %d(%%rbp)", getOffset(i + 1));
    }
    // iterate through start and visit each instruction
    Set<Instruction> visitedInstructions = new HashSet<>();
    Stack<Instruction> instructions = new Stack<>();
    instructions.push(function.getStart());
    while (!instructions.isEmpty()) {
      Instruction instruction = instructions.pop();
      if (!visitedInstructions.add(instruction)) {
        out.printCodef("jmp %s", labels.get(instruction));
        continue;
      }
      if (labels.containsKey(instruction)) {
        out.printLabel(labels.get(instruction));
      }
      // TODO: do we need to check if an instruction has already been visited in this loop? and add a jump instruction if so?
      instruction.accept(this);
      for (int i = instruction.numNext() - 1; i >= 0; i--) {
        instructions.push(instruction.getNext(i));
      }
      if (instruction.numNext() == 0 && !(instruction instanceof ReturnInst)) {
        // epilogue
        out.printCode("leave");
        out.printCode("ret");
      }
//      for (int i = 0; i < instruction.numNext(); i++) {
//        instructions.push(instruction.getNext(i));
//      }
    }
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    for (Iterator<GlobalDecl> it = p.getGlobals(); it.hasNext(); ) {
      GlobalDecl global = it.next();
      String name = global.getSymbol().getName();
      int size = (int) global.getNumElement().getValue();
      out.print(String.format(".comm %s, %d, 8", name, size * 8));
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
    out.printCodef("movq %s@GOTPCREL(%%rip), %%rsi", varName);
    if (i.getOffset() != null) {
      int indexSlot = getVariableSlot(i.getOffset());
      out.printCodef("movq %d(%%rbp), %%rdx", getOffset(indexSlot));
      out.printCode("shl $3, %rdx");
      out.printCode("addq %rdx, %rsi");
    }
    out.printCodef("movq %%rsi, %d(%%rbp)", getOffset(destinationSlot));
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
        out.printCode("cqto");
        instruction = "idivq";
        break;
      case Mul:
        instruction = "imulq";
    }
    out.printCodef("%s %d(%%rbp), %%rax", instruction, getOffset(rhsSlot));
    out.printCodef("movq %%rax, %d(%%rbp)", getOffset(destinationSlot));
  }

  public void visit(CompareInst i) {
    int lhsSlot = getVariableSlot(i.getLeftOperand());
    int rhsSlot = getVariableSlot(i.getRightOperand());
    int destinationSlot = getVariableSlot(i.getDst());
    out.printCodef("movq $0, %d(%%rbp)", getOffset(destinationSlot));
    out.printCode("movq $0, %rcx");
    out.printCode("movq $1, %r8");
    out.printCodef("movq %d(%%rbp), %%rax", getOffset(lhsSlot));
    out.printCodef("movq %d(%%rbp), %%rdx", getOffset(rhsSlot));
    out.printCode("cmpq %rdx, %rax");
    // convert predicate to x86 condition
    String condition = i.getPredicate().name().toLowerCase();
    if (condition.substring(1).equals("t") || condition.substring(1).equals("q")) {
      condition = condition.substring(0, 1);
    }
    out.printCodef("cmov%sq %%r8, %%rcx", condition);
    out.printCodef("movq %%rcx, %d(%%rbp)", getOffset(destinationSlot));
  }

  public void visit(CopyInst i) {
    int destinationSlot = getVariableSlot(i.getDstVar());
    Value value = i.getSrcValue();
    if (value instanceof IntegerConstant) {
      IntegerConstant constant = (IntegerConstant) value;
      out.printCodef("movq $%d, %d(%%rbp)", constant.getValue(), getOffset(destinationSlot));
    } else if (value instanceof BooleanConstant) {
      BooleanConstant constant = (BooleanConstant) value;
      out.printCodef("movq $%d, %d(%%rbp)", constant.getValue() ? 1 : 0, getOffset(destinationSlot));
    } else if (value instanceof LocalVar) {
      int fromSlot = getVariableSlot((LocalVar) value);
      out.printCodef("movq %d(%%rbp), %%rax", getOffset(fromSlot));
      out.printCodef("movq %%rax, %d(%%rbp)", getOffset(destinationSlot));
    } else {
      throw new RuntimeException("copy instruction value not valid: " + value.toString());
    }
  }

  public void visit(JumpInst i) {
    int predicateSlot = getVariableSlot(i.getPredicate());
    out.printCodef("testq $1, %d(%%rbp)", getOffset(predicateSlot));
    out.printCodef("jnz %s", labels.get(i.getNext(1)));
  }

  public void visit(LoadInst i) {
    int destinationSlot = getVariableSlot(i.getDst());
    int sourceAddressSlot = getVariableSlot(i.getSrcAddress());
    out.printCodef("movq %d(%%rbp), %%rsi", getOffset(sourceAddressSlot));
    out.printCode("movq (%rsi), %rax");
    out.printCodef("movq %%rax, %d(%%rbp)", getOffset(destinationSlot));
  }

  public void visit(NopInst i) {
    // don't think we need to do anything here
  }

  public void visit(StoreInst i) {
    int destinationAddressSlot = getVariableSlot(i.getDestAddress());
    int valueSlot = getVariableSlot(i.getSrcValue());
    out.printCodef("movq %d(%%rbp), %%rdi", getOffset(destinationAddressSlot));
    out.printCodef("movq %d(%%rbp), %%rdx", getOffset(valueSlot));
    out.printCode("movq %rdx, (%rdi)");
  }

  public void visit(ReturnInst i) {
    int returnValueSlot = getVariableSlot(i.getReturnValue());
    out.printCodef("movq %d(%%rbp), %%rax", getOffset(returnValueSlot));
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {
    int destinationSlot = getVariableSlot(i.getDst());
    for (int j = 0; j < Math.min(i.getParams().size(), argsRegisters.length); j++) {
      int parameterSlot = getVariableSlot(i.getParams().get(j));
      out.printCodef("movq %d(%%rbp), %s", getOffset(parameterSlot), argsRegisters[j]);
    }
    for (int j = i.getParams().size() - 1; j >= argsRegisters.length; j--) {
      int parameterSlot = getVariableSlot(i.getParams().get(j));
      out.printCodef("pushq %d(%%rbp)", getOffset(parameterSlot));
    }
    out.printCodef("call %s", i.getCallee().getName());
    out.printCodef("movq %%rax, %d(%%rbp)", getOffset(destinationSlot));
    if (i.getParams().size() > argsRegisters.length) {
      out.printCodef("addq $%d, %%rsp", (i.getParams().size() - argsRegisters.length) * 8);
    }
  }

  public void visit(UnaryNotInst i) {
    int destinationSlot = getVariableSlot(i.getDst());
    int operandSlot = getVariableSlot(i.getInner());
    out.printCodef("movq %d(%%rbp), %%rax", getOffset(operandSlot));
    out.printCodef("notq %%rax");
    out.printCodef("movq %%rax, %d(%%rbp)", getOffset(destinationSlot));
  }
}
