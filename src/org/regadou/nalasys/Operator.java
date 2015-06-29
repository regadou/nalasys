package org.regadou.nalasys;

import java.io.*;
import java.util.*;

public abstract class Operator extends Action implements Serializable {

    private static final Map<String,Operator> operators = new HashMap<String,Operator>();
    private static final Class[] resolveTypes = new Class[]{Object[].class, char[].class};

    public static Operator getOperator(String code) {
       return operators.get(code.toLowerCase());
    }

   private String name;
   private String symbol;
   private int precedence = 0;
   private int paramCount = 2;

   public Operator(String name) {
      this.symbol = this.name = name.toLowerCase();
   }

   public Operator(String symbol, String name, int precedence) {
      this(symbol, name, precedence, 2);
   }

   public Operator(String symbol, String name, int precedence, int paramCount) {
      this.symbol = symbol.toLowerCase();
      this.name = name.toLowerCase();
      this.precedence = precedence;
      this.paramCount = paramCount;
      operators.put(this.symbol, this);
    }

   public String getName() { return name; }

   public int getPrecedence() { return precedence; }

   @org.codehaus.jackson.annotate.JsonValue
   public String getSymbol() { return symbol; }

   public int getParamCount() { return paramCount; }

   public abstract Object invoke(Object[] params);

   public String toString() {
      String txt = Character.isLetter(symbol.charAt(0)) ? name : "'"+symbol+"'";
      return "[Operator "+txt+"]";
   }

   public boolean equals(Object that) { return this == that; }

   public Class[] parameters() {
      int np = ((Operator)getAction()).getParamCount();
      Class[] params = new Class[np];
      for (int p = 0; p < np; p++)
         params[p] = Object.class;
      return params;
   }

   public Object execute(Object target, Object[] params) throws Exception {
      int np = (params == null) ? 0 : params.length;
      int minp = getParamCount();
      if (np < minp) {
         Object[] a = new Object[minp];
         System.arraycopy(params, 0, a, 0, np);
         params = a;
      }
      return invoke(params);
   }

   private static int compare(Object v0, Object v1) {
      return Converter.toString(v0).compareTo(Converter.toString(v1));
   }

   public static final Operator OF = new Operator("@", "of", 11){
      public Object invoke(Object[] params) {
         Object[] reverse = new Object[(params==null)?0:params.length];
         for (int p = 0; p < reverse.length; p++)
            reverse[p] = params[reverse.length-p-1];
         return Converter.getPathValue(params);
      }
   };

   public static final Operator NOT = new Operator("~", "not", 10, 1){
      public Object invoke(Object[] params) {
          return Converter.toBoolean(Converter.getValue(params[0])) == false;
      }
   };

   public static final Operator EXPONANT = new Operator("^", "exponant", 9) {
      public Object invoke(Object[] params) {
         Number n0 = Converter.toNumber(Converter.getValue(params[0]), 0);
         Number n1 = Converter.toNumber(Converter.getValue(params[1]), 0);
         return Complex.exponant(n0, n1);
      }
   };

   public static final Operator LOG = new Operator("\\", "logarithm", 9) {
      public Object invoke(Object[] params) {
         Number n0 = Converter.toNumber(Converter.getValue(params[0]), 0);
         Number n1 = Converter.toNumber(Converter.getValue(params[1]), 0);
         return Complex.logarithm(n0, n1);
      }
   };

   public static final Operator MULTIPLY = new Operator("*", "multiply", 8) {
      public Object invoke(Object[] params) {
         Number n0 = Converter.toNumber(Converter.getValue(params[0]), 0);
         Number n1 = Converter.toNumber(Converter.getValue(params[1]), 0);
         return Complex.multiply(n0, n1);
      }
   };

   public static final Operator DIVIDE = new Operator("/", "divide", 8) {
      public Object invoke(Object[] params) {
         Number n0 = Converter.toNumber(Converter.getValue(params[0]), 0);
         Number n1 = Converter.toNumber(Converter.getValue(params[1]), 0);
         return Complex.divide(n0, n1);
      }
   };

   public static final Operator MODULO = new Operator("%", "modulo", 8) {
      public Object invoke(Object[] params) {
         Number n0 = Converter.toNumber(Converter.getValue(params[0]), 0);
         Number n1 = Converter.toNumber(Converter.getValue(params[1]), 0);
         return Complex.ratio(n0, n1);
      }
   };

   public static final Operator ADD = new Operator("+", "add", 7) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         if (v0 == null)
            return v1;
         else if (v1 == null)
            return v0;
         else if (v0 instanceof Collection || v1 instanceof Collection)
            ;
         else if (v0 instanceof Number || v1 instanceof Number)
            return Complex.add(Converter.toNumber(v0, 0),
                                             Converter.toNumber(v1, 0));
         else if (v0 instanceof CharSequence && v1 instanceof CharSequence)
            return v0.toString() + v1.toString();

         Collection c = new ArrayList();
         c.addAll(Converter.toCollection(v0));
         c.addAll(Converter.toCollection(v1));
         return c;
      }
   };

   public static final Operator SUBSTRACT = new Operator("-", "substract", 7) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         if (v1 == null)
            return v0;
         else if (v0 instanceof Collection || v1 instanceof Collection)
            ;
         else if (v0 instanceof Number || v1 instanceof Number)
            return Complex.substract(Converter.toNumber(v0, 0),
                                     Converter.toNumber(v1, 0));
         else if (v0 instanceof CharSequence && v1 instanceof CharSequence) {
            String s0 = v0.toString();
            String s1 = v1.toString();
            int p = s0.indexOf(s1);
            if (p >= 0)
               s0 = s0.substring(0, p) + s0.substring(p+s1.length());
            return s0;
         }

         Collection c = new ArrayList();
         c.addAll(Converter.toCollection(v0));
         c.removeAll(Converter.toCollection(v1));
         return c;
      }
   };

   public static final Operator LESS = new Operator("<", "less", 5) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) < 0;
      }
   };

   public static final Operator LESSEQUAL = new Operator("<=", "lessequal", 5) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) <= 0;
      }
   };

   public static final Operator MORE = new Operator(">", "more", 5) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) > 0;
      }
   };

   public static final Operator MOREEQUAL = new Operator(">=", "moreequal", 5) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) >= 0;
      }
   };

   public static final Operator EQUAL = new Operator("=", "equal", 4) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) == 0;
      }
   };

   public static final Operator NOTEQUAL = new Operator("<>", "notequal", 4) {
      public Object invoke(Object[] params) {
         Object v0 = Converter.getValue(params[0]);
         Object v1 = Converter.getValue(params[1]);
         return compare(v0, v1) != 0;
      }
   };

   public static final Operator AND = new Operator("&", "and", 3) {
      public Object invoke(Object[] params) {
         return Converter.toBoolean(Converter.getValue(params[0]))
             && Converter.toBoolean(Converter.getValue(params[1]));
      }
   };

   public static final Operator OR = new Operator("|", "or", 2) {
      public Object invoke(Object[] params) {
         return Converter.toBoolean(Converter.getValue(params[0]))
             || Converter.toBoolean(Converter.getValue(params[1]));
      }
   };

   public static final Operator HAVE = new Operator("#", "have", 2) {
      public Object invoke(Object[] params) {
         return Converter.getPathValue(params);
      }
   };

   public static final Operator ASSIGN = new Operator(":", "assign", -1) {
      public Object invoke(Object[] params) {
         Object var = params[0];
         if (var instanceof Expression)
            return ASSIGN.invoke(new Object[]{((Expression)var).getValue(), params[1]});
         else if (var instanceof Map.Entry) {
            Map.Entry e = (Map.Entry)var;
            e.setValue(Converter.getValue(params[1]));
         }
         else if (var instanceof CharSequence)
            Context.currentContext().setAttribute(var.toString(), Converter.getValue(params[1]));
         else if (var instanceof char[])
            Context.currentContext().setAttribute(new String((char[])var), Converter.getValue(params[1]));
         else if (var instanceof byte[])
            Context.currentContext().setAttribute(new String((byte[])var), Converter.getValue(params[1]));
         return var;
      }
   };

   public static final Operator EVAL = new Operator(",", "eval", -2) {
      public Object invoke(Object[] params) {
         if (params != null && params.length > 2) {
            Object n = Converter.getValue(params[2]);
            if (n instanceof Number) {
               int i = ((Number)n).intValue();
               if (i == 0) {
                  List lst = new ArrayList();
                  lst.add(Converter.getValue(params[0]));
                  lst.add(Converter.getValue(params[1]));
                  return lst;
               }
               else if (i > 0) {
                  List lst = Converter.toList(Converter.getValue(params[0]));
                  lst.add(Converter.getValue(params[1]));
                  return lst;
               }
            }
         }
         return (params != null && params.length > 0) ? params[params.length - 1] : null;
      }
   };

   public static final Operator EXEC = new Operator(".", "exec", -3) {
      public Object invoke(Object[] params) {
          return (params != null && params.length > 0) ? params[params.length - 1] : null;
      }
   };

   static {
      operators.put("!", EXEC);
      operators.put(";", EVAL);
      operators.put(":=", ASSIGN);
      operators.put("==", EQUAL);
      operators.put("&&", AND);
      operators.put("||", OR);
   }
}

