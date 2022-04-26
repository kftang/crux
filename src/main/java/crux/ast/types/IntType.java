package crux.ast.types;

/**
 * Types for Integers values. This should implement the equivalent methods along with add, sub, mul,
 * div, and compare. The method equivalent will check if the param is an instance of IntType.
 */
public final class IntType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  public String toString() {
    return "int";
  }

  @Override
  Type add(Type that) {
    return equivalent(that) ? new IntType() : super.add(that);
  }

  @Override
  Type sub(Type that) {
    return equivalent(that) ? new IntType() : super.sub(that);
  }

  @Override
  Type mul(Type that) {
    return equivalent(that) ? new IntType() : super.mul(that);
  }

  @Override
  Type div(Type that) {
    return equivalent(that) ? new IntType() : super.div(that);
  }

  @Override
  Type compare(Type that) {
    return equivalent(that) ? new BoolType() : super.compare(that);
  }

  @Override
  Type assign(Type source) {
    return equivalent(source) ? new VoidType() : super.assign(source);
  }

  @Override
  public boolean equivalent(Type that) {
    return this.getClass().isInstance(that);
  }
}
