package crux.ast.types;

/**
 * The variable base is the type of the array element. This could be int or bool. The extent
 * variable is number of elements in the array.
 *
 */
public final class ArrayType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;
  private final Type base;
  private final long extent;

  public ArrayType(long extent, Type base) {
    this.extent = extent;
    this.base = base;
  }

  public Type getBase() {
    return base;
  }

  public long getExtent() {
    return extent;
  }

  private Type cloneBase() {
    if (base.getClass() == IntType.class) {
      return new IntType();
    } else if (base.getClass() == BoolType.class) {
      return new BoolType();
    }
    System.err.println("Base type is not int or bool when indexing an array");
    return null;
  }

  @Override
  public boolean equivalent(Type that) {
    return base.equivalent(that);
  }

  @Override
  Type index(Type that) {
    return that.getClass() == IntType.class ? cloneBase() : super.index(that);
  }

  @Override
  public String toString() {
    return String.format("array[%d,%s]", extent, base);
  }
}
