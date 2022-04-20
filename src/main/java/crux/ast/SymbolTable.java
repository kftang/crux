package crux.ast;

import crux.ast.types.*;


import java.io.PrintStream;
import java.util.*;

/**
 * Symbol table will map each symbol from Crux source code to its declaration or appearance in the
 * source. The symbol table is made up of scopes, Each scope is a map which maps an identifier to
 * it's symbol. Scopes are inserted to the table starting from the first scope (Global Scope). The
 * Global scope is the first scope in each Crux program and it contains all the built in functions
 * and names. The symbol table is an ArrayList of scops.
 */
public final class SymbolTable {

  /**
   * Symbol is used to record the name and type of names in the code. Names include function names,
   * global variables, global arrays, and local variables.
   */
  static public final class Symbol implements java.io.Serializable {
    static final long serialVersionUID = 12022L;
    private final String name;
    private final Type type;
    private final String error;

    /**
     *
     * @param name String
     * @param type the Type
     */
    private Symbol(String name, Type type) {
      this.name = name;
      this.type = type;
      this.error = null;
    }

    private Symbol(String name, String error) {
      this.name = name;
      this.type = null;
      this.error = error;
    }

    /**
     *
     * @return String the name
     */
    public String getName() {
      return name;
    }

    /**
     *
     * @return the type
     */
    public Type getType() {
      return type;
    }

    @Override
    public String toString() {
      if (error != null) {
        return String.format("Symbol(%s:%s)", name, error);
      }
      return String.format("Symbol(%s:%s)", name, type);
    }

    public String toString(boolean includeType) {
      if (error != null) {
        return toString();
      }
      return includeType ? toString() : String.format("Symbol(%s)", name);
    }
  }

  private final PrintStream err;
  // stack of symbol tables
  private final Deque<Map<String, Symbol>> symbolTables = new LinkedList<>();

  private boolean encounteredError = false;

  SymbolTable(PrintStream err) {
    this.err = err;
    Map<String, Symbol> globals = new HashMap<>();
    // initialize globals
    Symbol readInt = new Symbol("readInt", new FuncType(new TypeList(), new IntType()));
    Symbol readChar = new Symbol("readChar", new FuncType(new TypeList(), new IntType()));
    Symbol printBool = new Symbol("printBool", new FuncType(TypeList.of(new BoolType()), new VoidType()));
    Symbol printInt = new Symbol("printInt", new FuncType(TypeList.of(new IntType()), new VoidType()));
    Symbol printChar = new Symbol("printChar", new FuncType(TypeList.of(new IntType()), new VoidType()));
    Symbol println = new Symbol("println", new FuncType(new TypeList(), new VoidType()));
    globals.put("readInt" ,readInt);
    globals.put("readChar" ,readChar);
    globals.put("printBool" ,printBool);
    globals.put("printInt" ,printInt);
    globals.put("printChar" ,printChar);
    globals.put("println" ,println);
    symbolTables.push(globals);
    //TODO
  }

  boolean hasEncounteredError() {
    return encounteredError;
  }

  /**
   * Called to tell symbol table we entered a new scope.
   */

  void enter() {
    symbolTables.push(new HashMap<>());
  }

  /**
   * Called to tell symbol table we are exiting a scope.
   */

  void exit() {
    symbolTables.pop();
  }

  /**
   * Insert a symbol to the table at the most recent scope. if the name already exists in the
   * current scope that's a declareation error.
   */
  Symbol add(Position pos, String name, Type type) {
    var symbolTable = symbolTables.peek();
    if (symbolTable.containsKey(name)) {
      err.printf("RedefinitionError%s[Variable %s already declared.]%n", pos, name);
      encounteredError = true;
      return new Symbol(name, "RedefinitionError");
    }
    var symbol = new Symbol(name, type);
    symbolTable.put(name, symbol);
    return symbol;
  }

  /**
   * lookup a name in the SymbolTable, if the name not found in the table it shouold encounter an
   * error and return a symbol with ResolveSymbolError error. if the symbol is found then return it.
   */
  Symbol lookup(Position pos, String name) {
    var symbol = find(name);
    if (symbol == null) {
      err.printf("ResolveSymbolError%s[Could not find %s.]%n", pos, name);
      encounteredError = true;
      return new Symbol(name, "ResolveSymbolError");
    } else {
      return symbol;
    }
  }

  /**
   * Try to find a symbol in the table starting form the most recent scope.
   */
  private Symbol find(String name) {
    var it = symbolTables.descendingIterator();
    while (it.hasNext()) {
      var symbolTable = it.next();
      if (symbolTable.containsKey(name)) {
        return symbolTable.get(name);
      }
    }
    return null;
  }
}
