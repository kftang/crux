package crux.backend;

import java.util.*;
import java.io.*;

public class CodePrinter {
  PrintStream out;

  public CodePrinter(String name) {
    try {
      out = new PrintStream(name);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public void printLabel(String s) {
    out.printf("%s:%n", s);
  }

  public void printCode(String s) {
    out.println("    " + s);
  }

  public void printCodef(String format, Object... args) {
    out.printf(format, args);
    out.println();
  }

  public void close() {
    out.close();
  }
}
