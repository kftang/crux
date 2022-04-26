package crux.ast.types;

/**
 * Types for Booleans values This should implement the equivalent methods along with and,or, and not
 * equivalent will check if the param is instance of BoolType
 */
public final class BoolType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  Type and(Type that) {
    return equivalent(that) ? new BoolType() : super.and(that);
  }

  @Override
  Type or(Type that) {
    return equivalent(that) ? new BoolType() : super.or(that);
  }

  @Override
  Type not() {
    return new BoolType();
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

  @Override
  public String toString() {
    return "bool";
  }
}
