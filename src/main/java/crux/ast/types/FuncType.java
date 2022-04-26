package crux.ast.types;

/**
 * The field args is a TypeList with a type for each param. The type ret is the type of the function
 * return. The function return could be int, bool, or void. This class should implement the call
 * method.
 */
public final class FuncType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  private TypeList args;
  private Type ret;

  public FuncType(TypeList args, Type returnType) {
    this.args = args;
    this.ret = returnType;
  }

  public Type getRet() {
    return ret;
  }

  public TypeList getArgs() {
    return args;
  }

  @Override
  Type call(Type args) {
    if (args.getClass().isInstance(args)) {
      TypeList argsList = (TypeList) args;
      if (args.equivalent(argsList)) {
        Class<? extends Type> retClass = ret.getClass();
        if (retClass == BoolType.class) {
          return new BoolType();
        } else if (retClass == IntType.class) {
          return new IntType();
        } else if (retClass == VoidType.class) {
          return new VoidType();
        } else {
          return super.call(args);
        }
      }
    }
    return super.call(args);
  }

  @Override
  public boolean equivalent(Type that) {
    if (!this.getClass().isInstance(that)) {
      return false;
    }
    FuncType func = (FuncType) that;
    return ret.equivalent(func.ret) && args.equivalent(func.args);
  }

  @Override
  public String toString() {
    return "func(" + args + "):" + ret;
  }
}
