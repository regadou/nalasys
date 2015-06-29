package org.regadou.nalasys;

import java.util.*;
import java.lang.reflect.*;

public abstract class Action {

   private static final Map<Class,Action> actionTypes = new LinkedHashMap<Class,Action>();

   public static void addActionType(Class type, Action a) {
      if (type == null || a == null)
         return;
      actionTypes.put(type, a);
   }

   public static Class[] getActionTypes() {
      return actionTypes.keySet().toArray(new Class[actionTypes.size()]);
   }

   public static Action getAction(Object src) {
      if (src == null)
         return null;
      else if (src instanceof Action)
         return (Action)src;
      else if (src instanceof CharSequence) {
         try { src = Class.forName(src.toString()); }
         catch (Exception e) {}
      }
      Class type = src.getClass();
      for (Map.Entry<Class,Action> entry : actionTypes.entrySet()) {
         if (entry.getKey().isAssignableFrom(type)) {
            Action a = entry.getValue();
            try {
               return ((Action)a.getClass().newInstance())
                       .setAction(src);
            }
            catch (Exception e) {
               throw new RuntimeException("Cannot create new instance for "+a);
            }
         }
      }
      return null;
   }

   public static boolean isAction(Object src) {
      if (src == null)
         return false;
      else if (src instanceof Action)
         return true;
      Class type = src.getClass();
      for (Map.Entry<Class,Action> entry : actionTypes.entrySet()) {
         if (entry.getKey().isAssignableFrom(type))
            return true;
      }
      return false;
   }

   public static boolean isAssignable(Class dst, Class src) {
      if (dst == null || dst.equals(Object.class))
         return true;
      else if (src == null)
         return !dst.isPrimitive();
      else if(dst.isAssignableFrom(src))
         return true;
      else if(src.isPrimitive())
         return isAssignable(getPrimitiveClass(dst), src);
      else if (dst.isPrimitive())
         return isAssignable(dst, getPrimitiveClass(dst));
      else
         return false;
   }

   public static Class getPrimitiveClass(Class c) {
      if (c == null)
         return Void.class;
      else if (c.isPrimitive())
         return c;
      try { return (Class) c.getField("TYPE").get(null); }
      catch (Exception e) { return Void.class; }

   }

   public static Constructor getConstructor(Class c, Object[] params) {
      if (c == null)
         return null;
      else if (params == null) {
          Constructor[] lst = c.getConstructors();
          return (lst.length == 0) ? null : lst[lst.length-1];
      }
      else if (params.getClass().getComponentType().equals(Class.class)) {
          try { return c.getConstructor((Class[])params); }
          catch (Exception e) { return null; }
      }
      else {
/** TODO: we must later develop an algorytm to cycle through
 *        all superclasses and interfaces of each param
 *
          Class[][] types = new Class[params.length][];
          int count[] = new int[params.length];
          for (int i = 0; i < params.length; i++) {
             Class t = (params[i] == null) ? Object.class : params[i].getClass();
             types[i] = Converter.getTypeClasses(t, true);
             count[i] = types[i].length;
          }
 ****/
          Class[] classes = new Class[params.length];
          for (int i = 0; i < params.length; i++)
              classes[i] = (params[i] == null) ? Object.class : params[i].getClass();
          try { return c.getConstructor(classes); }
          catch (Exception e) { return null; }
      }
   }

   public static Method getMethod(Class c, String name, Class[] types) {
      Method[] lst = getMethods(c, name, types);
      return (lst.length == 0) ? null : lst[lst.length-1];
   }

   public static Method[] getMethods(Class c, String name, Class[] types) {
      if (c == null || name == null)
         return new Method[0];
      name = name.trim();
      if (name.equals(""))
         return new Method[0];
      List<Method> methods = new ArrayList<Method>();
      for (Method method : c.getMethods()) {
         if (name.equals(method.getName())
              && (types == null || types.length == method.getParameterTypes().length))
            methods.add(method);
      }
      return methods.toArray(new Method[methods.size()]);
   }

    public static Object[] getParameters(Object[] src, Class[] types) {
       int np = (src == null) ? 0 : src.length;
       int nt = (types == null) ? 0 : types.length;
       Object[] dst = new Object[nt];
       for (int t = 0; t < nt; t++)
          dst[t] = Converter.convert((t < np) ? src[t] : null, types[t]);
       return dst;
    }

    public static Class[] getTypes(Object[] params, Class nullType) {
       int np = (params == null) ? 0 : params.length;
       Class[] types = new Class[np];
       for (int p = 0; p < np; p++)
          types[p] = (params[p] == null) ? nullType : params[p].getClass();
       return types;
    }

    public static int getCompatibleClasses(Class[] ctypes, Object[] ptypes) {
       if (ctypes == null || ctypes.length == 0)
          return (ptypes == null) ? 0 : ptypes.length;
       else if(ptypes == null || ptypes.length == 0)
          return 0;

       int compatibles = 0;
       boolean isClass = (ptypes instanceof Class[]);
       for (int i = 0; i < ctypes.length; i++) {
          Class c = ctypes[i];
          if (c == null || c == Object.class)
             compatibles++;
          else if(i >= ptypes.length)
             continue;
          else {
             Class p;
             if (isClass)
                p = (Class)ptypes[i];
             else if (ptypes[i] == null)
                p = null;
             else
                p = ptypes[i].getClass();
             if (p == null || p == Object.class)
                compatibles++;
             else if (c.isAssignableFrom(p))
                compatibles++;
          }
       }
       return compatibles;
    }

    public static Object invokeMethod(Object target, String name, Object[] params) throws Exception {
       if (target == null || name == null || name.trim().equals(""))
          return null;
       int np = (params == null) ? 0 : params.length;
       Class[] types = new Class[np];
       for (int p = 0; p < np; p++)
          types[p] = (params[p] == null) ? Object.class : params[p].getClass();
       Method m = getMethod(target.getClass(), name, types);
       return (m == null) ? null : m.invoke(target, params);
    }

   private Object action;
   private String name;

   public Action() {}

   public String toString() {
      return "[Action "+getName()+"]";
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String getName() {
      if (name == null || name.equals("")) {
         if (action == null)
            name = "null";
         else {
            Property p = new Property(action, "name");
            if (p.isFound())
               return String.valueOf(p.getValue());
            else
               return action.getClass().getName();
         }
      }
      return name;
   }

   public void setName(String name) {
      this.name = (name == null) ? null : name.trim();
   }

   public Object getAction() { return action; }

   private Action setAction(Object a) {
      if (action == null)
         action = a;
      return this;
   }

   public int getPrecedence() { return 0; }

   public Object execute(Object[] params) throws Exception {
      return execute(null, params);
   }

   public abstract Object execute(Object target, Object[] params) throws Exception;

   public abstract Class[] parameters();

   static {
      addActionType(Method.class, new Action() {
         public Class[] parameters() {
            return ((Method)getAction()).getParameterTypes();
         }
         public Object execute(Object target, Object[] params) throws Exception {
            Method m = (Method)getAction();
            params = getParameters(params, m.getParameterTypes());
            return m.invoke(target, params);
         }
      });
      addActionType(Constructor.class, new Action() {
         public Class[] parameters() {
            return ((Constructor)getAction()).getParameterTypes();
         }
         public Object execute(Object target, Object[] params) throws Exception {
            Constructor c = (Constructor)getAction();
            params = getParameters(params, c.getParameterTypes());
            return c.newInstance(params);
         }
      });

      addActionType(Class.class, new Action() {
         public Class[] parameters() { return null; }
         public Object execute(Object target, Object[] params) throws Exception {
             Class type = (Class)getAction();
             if (params == null || params.length == 0)
                return type.newInstance();
             else if (params.length == 1 && params[0] != null && type.isAssignableFrom(params[0].getClass()))
                return params[0];
             else {
                Constructor cons = null;
                try { cons = type.getConstructor(getTypes(params, Object.class)); }
                catch (Exception e) {
                   Class[] ptypes = getTypes(params, null);
                   int miss = 0 - ptypes.length;
                   for (Constructor c : type.getConstructors()) {
                      Class[] ctypes = c.getParameterTypes();
                      int dif = ptypes.length - ctypes.length;
                      if (dif > 0)
                         continue;
                      else if (dif == 0) {
                         cons = c;
                         miss = 0;
                         if (getCompatibleClasses(ctypes, ptypes) == ctypes.length)
                            break;
                      }
                      else if (dif > miss) {
                         cons = c;
                         miss = dif;
                      }
                   }
                }

                if (cons == null)
                   return (params.length == 1) ? Converter.convert(params[0], type) : null;
                params = getParameters(params, cons.getParameterTypes());
                return cons.newInstance(params);
             }
         }
      });
      addActionType(Expression.class, new Action() {
          public Class[] parameters() { return null; }
          public Object execute(Object target, Object[] params) throws Exception {
             // we should have something in the Expression to set target
             return ((Expression)getAction()).getValue(params);
          }
      });
   }
}
