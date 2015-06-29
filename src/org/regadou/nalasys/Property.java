package org.regadou.nalasys;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class Property<K,V> implements Map.Entry<K,V> {

   public static final List<String> universalProperties = Arrays.asList(new String[]{
      "class", "properties", "length", "content", "hash"
   });
   public static final List<String[]> universalMethodNames = Arrays.asList(new String[][]{
      {},                           // class
      {"properties"},               // properties
      {"length","size"},            // length
      {"list","content","elements"},// content
      {"hashCode"}                  // hash
   });

   public static final int CLASS_PROPERTY=0,   PROPERTIES_PROPERTY=1, LENGTH_PROPERTY=2,
                           CONTENT_PROPERTY=3, HASH_PROPERTY=4;
   public static final int GETTER=0, SETTER=1, ACCESSORS=2;

   private static Map<Class,Map<String,Method[]>> properties = new HashMap<Class,Map<String,Method[]>>();
   private static Map<Class,Map<String,Method>> methodsMap = new HashMap<Class,Map<String,Method>>();
   private static Map<Class,Map<String,Method>> staticMethodsMap = new HashMap<Class,Map<String,Method>>();
   private static Map<Class,Map<String,Method[]>> staticFieldsMap = new HashMap<Class,Map<String,Method[]>>();
   private static final Method[] fieldAccessors = getFieldAccessors();
   static { getClassProperties(Class.class); }

   private URL url;
   private K key;
   private V value;
   private Class<V> type;
   private Object source;
   private Object restrictions;
   private Method[] accessors;
   private boolean standalone;

   public Property(URL url) {
      this.url = url;
   }

   public Property(URI uri) {
      try { url = uri.toURL(); }
      catch (Exception e) { Context.exception(e, "new Property("+uri+")"); }
   }

   public Property(File file) {
      try { url = file.toURI().toURL(); }
      catch (Exception e) { Context.exception(e, "new Property("+file+")"); }
   }

   public Property(String src) {
      try { url = new URL(src); }
      catch (Exception e) {
         File file = new File(src);
         if (file.exists() || (file.getParentFile() != null && file.getParentFile().exists())) {
            try { url = file.toURI().toURL(); }
            catch (Exception e2) { Context.exception(e2, "new Property("+src+")"); }
         }
         else {
            String name = src.toString();
            int eq = name.indexOf('=');
            int col = name.indexOf(':');
            if (col > 0 && (eq < 0 || col < eq))
                  eq = col;
            if (eq >= 0) {
               key = (K)name.substring(0, eq);
               value = (V)name.substring(eq+1);
            }
            else
               key = (K)name;
         }
      }
   }

   public Property(Object source, K key) {
      this(source, key, null, null, null);
   }

   public Property(Object source, K key, V value) {
      this.source = source;
      this.key = key;
      if (this.source == null) {
         this.value = value;
         standalone = true;
      }
      else {
         setAccessors();
         if (value != null)
            setValue(value);
      }
   }

   public Property(Object source, K key, Class<V> type, Method getter, Method setter) {
      this.source = source;
      this.key = key;
      this.restrictions = type;
      if (getter != null || setter != null) {
         accessors = new Method[]{getter, setter};
         if (value != null)
            setValue(value);
      }
      else if (source == null)
         standalone = true;
      else if (source instanceof Package) {
         this.value = (V)Package.getPackage(source + "." + key);
         if (this.value == null) {
            try { this.value = (V)Class.forName(source + "." + key); }
            catch (Exception e) {}
         }
      }
      else
         setAccessors();
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
      if (standalone)
         return key+"="+value;
      String c = (source == null) ? "null" : source.getClass().getName();
      return key+"@"+c;
   }

   public K getKey() {
      return (url != null) ? (K)url.toString() : key;
   }

   public Class getType() {
      if (restrictions instanceof Class)
         return (Class)restrictions;
      if (type == null) {
         try {
            if (!isFound())
               type = (Class<V>)Object.class;
            else if (accessors[GETTER] != null)
               type = (Class<V>)accessors[GETTER].getReturnType();
            else if (accessors[SETTER] != null) {
               Class[] types = accessors[SETTER].getParameterTypes();
               type = (Class<V>)types[types.length-1];
            }
            else if (value != null)
               type = (Class<V>)value.getClass();
            else
               type = (Class<V>)Object.class;
         }
         catch (Exception e) { type = (Class<V>)Object.class; }
      }
      return type;
   }

   public Object getSource() {
      return (url != null) ? url : source;
   }

   public V getValue() {
      if (standalone || value != null)
         return value;
      else if (url != null) {
         Stream s = new Stream(url);
         value = s.read(null);
         s.close();
         return value;
      }
      else if (accessors == null || accessors[GETTER] == null)
         return null;
      Method method = accessors[GETTER];
      Object[] params = getParams(method, GETTER, null);
      Object target = method.getDeclaringClass().equals(Class.class)
                     ? source.getClass() : source;
      try { return (V)Action.getAction(method).execute(target, params); }
      catch (Exception e) { return null; }
   }

   public V setValue(V o) {
      V old = (url != null) ? value : getValue();
      if (!isValid(o))
         return old;
      else if (standalone) {
         value = o;
         type = (Class<V>)((o == null) ? Object.class : o.getClass());
         return old;
      }
      else if (url != null) {
         value = null;
         Stream s = new Stream(url);
         s.write(o);
         s.close();
         return old;
      }
      else if (accessors == null || accessors[SETTER] == null)
         return old;
      Method method = accessors[SETTER];
      Object[] params = getParams(method, SETTER, o);
      Object target = method.getDeclaringClass().equals(Class.class)
                     ? source.getClass() : source;
      try { Action.getAction(method).execute(target, params); }
      catch (Exception e) {}
      return old;
   }

   public Object getRestrinctions() { return restrictions; }

   public void setRestrictions(Object r) { restrictions = r; }

   public boolean isReadable() {
      return standalone || value != null || (accessors != null && accessors[GETTER] != null);
   }

   public boolean isWritable() {
      return standalone || (accessors != null && accessors[SETTER] != null);
   }

   public boolean isFound() {
      return standalone || value != null || accessors != null;
   }

   public boolean isValid(Object v) {
      if (restrictions == null)
         return true;
      else if (restrictions instanceof Class)
         return v == null || ((Class)restrictions).isAssignableFrom(v.getClass());
      Action a = Action.getAction(restrictions);
      if (a != null) {
         try { return Converter.toBoolean(a.execute(this, new Object[]{v})); }
         catch (Exception e) { return false; }
      }
      else
         return Converter.toCollection(restrictions).contains(v);
   }

   public static Object getFieldValue(Object src, Object name) {
      try {
         Class type = (src instanceof Class) ? (Class)src : src.getClass();
         return type.getField(name.toString()).get(src);
      }
      catch (Exception e) { return e; }
   }

   public static Object setFieldValue(Object src, Object name, Object value) {
      try {
         Class type = (src instanceof Class) ? (Class)src : src.getClass();
         Field f = type.getField(name.toString());
         f.set(src, Converter.convert(value, f.getType()));
         return f.get(src);
      }
      catch (Exception e) { return e; }
   }

   public static Object getMapProperty(Map map, String property) {
      if (map.containsKey(property))
         return map.get(property);
      switch (universalProperties.indexOf(property)) {
         case CLASS_PROPERTY:
            return map.getClass();
         case PROPERTIES_PROPERTY:
            Set keys = new TreeSet(map.keySet());
            keys.addAll(universalProperties);
            return keys;
         case LENGTH_PROPERTY:
            return map.size();
         case CONTENT_PROPERTY:
            return map.entrySet();
         case HASH_PROPERTY:
            return map.hashCode();
         default:
            return map.get(property);
      }
   }

   public static Object getCollectionProperty(Collection c, String property) {
      switch (universalProperties.indexOf(property)) {
         case CLASS_PROPERTY:
            return c.getClass();
         case PROPERTIES_PROPERTY:
            return new TreeSet(universalProperties);
         case LENGTH_PROPERTY:
            return c.size();
         case CONTENT_PROPERTY:
            return c;
         case HASH_PROPERTY:
            return c.hashCode();
         default:
            return null;
      }
   }

   public static Object getArrayProperty(Object a, String property) {
      switch (universalProperties.indexOf(property)) {
         case CLASS_PROPERTY:
            return a.getClass();
         case PROPERTIES_PROPERTY:
            return new TreeSet(universalProperties);
         case LENGTH_PROPERTY:
            return Array.getLength(a);
         case CONTENT_PROPERTY:
            return a;
         case HASH_PROPERTY:
            return a.hashCode();
         default:
            return null;
      }
   }

   public static Object getIndexProperty(Collection c, int index) {
      if (index < 0 || c == null)
         return null;
      Iterator i = c.iterator();
      for (int at = 0; i.hasNext(); at++) {
         Object obj = i.next();
         if (at == index)
            return obj;
      }
      return null;
   }

   public static Collection getProperties(Object obj) {
      Class type = (obj == null) ? null : obj.getClass();
      return getClassProperties(type).keySet();
   }

   public static Map<String,Method[]> getClassProperties(Class type) {
      if (type == null)
         return new TreeMap<String,Method[]>();

      Map<String,Method[]> props = properties.get(type);
      if (props == null) {
         props = new TreeMap<String,Method[]>();
         properties.put(type, props);

         Map<String,Method> methods = methodsMap.get(type);
         if (methods == null) {
            methods = new TreeMap<String,Method>();
            methodsMap.put(type, methods);
         }

         Map<String,Method> statics = staticMethodsMap.get(type);
         if (statics == null) {
            statics = new TreeMap<String,Method>();
            staticMethodsMap.put(type, statics);
         }

         if (staticFieldsMap.get(type) == null) {
            Map<String,Method[]> fields = new TreeMap<String,Method[]>();
            staticFieldsMap.put(type, fields);
            for (Field f : type.getFields()) {
                  String name = f.getName();
                  if (Modifier.isStatic(f.getModifiers()))
                     fields.put(name, fieldAccessors);
                  else
                     props.put(name, fieldAccessors);
            }
         }

         for (Method m : type.getMethods()) {
               String name = m.getName();
               if (Modifier.isStatic(m.getModifiers())) {
                  statics.put(name, m);
                  continue;
               }

               if (name.startsWith("get") && name.length() > 3
                                          && Character.isUpperCase(name.charAt(3))
                                          && m.getParameterTypes().length == 0)
                  addMethod(props, name, 3, m, null);
               else if(name.startsWith("is") && name.length() > 2
                                          && Character.isUpperCase(name.charAt(2))
                                          && m.getParameterTypes().length == 0)
                  addMethod(props, name, 2, m, null);
               else if(name.startsWith("set") && name.length() > 3
                                          && Character.isUpperCase(name.charAt(3))
                                          && m.getParameterTypes().length == 1)
                  addMethod(props, name, 3, null, m);
               // TODO: repare flaw cases of several methods with the same name
               methods.put(name, m);
         }

         if (Map.class.isAssignableFrom(type))
            addGeneric(props, "getMapProperty", Map.class);
         else if (Collection.class.isAssignableFrom(type))
            addGeneric(props, "getCollectionProperty", Collection.class);
         else if (type.isArray())
            addGeneric(props, "getArrayProperty", Object.class);
         else {
            for (int p = 0; p < universalProperties.size(); p++) {
               for (String name : universalMethodNames.get(p)) {
                  try {
                     Method m = Property.class.getMethod(name, new Class[]{Object.class});
                     addMethod(props, universalProperties.get(p), -1, m, null);
                     break;
                  }
                  catch (Exception e) {}
               }
            }
         }
      }

      return props;
   }

   private static void addMethod(Map<String,Method[]> props, String name, int index, Method getter, Method setter) {
      if (index > 0)
         name = name.substring(index, index+1).toLowerCase() + name.substring(index+1);
      Method[] methods = props.get(name);
      if (methods == null) {
         methods = new Method[ACCESSORS];
         props.put(name, methods);
      }
      if (getter != null)
         methods[GETTER] = getter;
      if (setter != null)
         methods[SETTER] = setter;
   }

   private static void addGeneric(Map<String,Method[]> props, String name, Class param) {
      try {
         Method method = Property.class.getMethod(name, new Class[]{param,String.class});
         for (String property : universalProperties) {
            Method[] methods = props.get(property);
            if (methods == null) {
               methods = new Method[ACCESSORS];
               props.put(property, methods);
            }
            methods[GETTER] = method;
         }
      }
      catch (Exception e) {}
   }

   private void setAccessors() {
      Class type = source.getClass();
      if (source instanceof Map) {
         try {
            accessors = new Method[]{
               Property.class.getMethod("getMapProperty", new Class[]{Map.class,String.class}),
               type.getMethod("put", new Class[]{Object.class,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (source instanceof Class) {
         Class c = (Class)source;
         String name = key.toString();
         getClassProperties(c);
         value = (V)staticMethodsMap.get(c).get(name);
         if (value != null)
            return;
         accessors = staticFieldsMap.get(c).get(name);
         if (accessors == null)
            value = (V)methodsMap.get(type).get(name);
      }
      else if (source instanceof Context || source instanceof javax.servlet.ServletRequest
                                         || source instanceof javax.servlet.http.HttpSession) {
         try {
            accessors = new Method[]{
               type.getMethod("getAttribute", new Class[]{String.class}),
               type.getMethod("setAttribute", new Class[]{String.class,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (source instanceof Folder) {
         try {
            accessors = new Method[]{
               type.getMethod("getFile", new Class[]{String.class}),
               type.getMethod("setFile", new Class[]{String.class,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (source instanceof File && ((File)source).isDirectory()) {
         source = new Folder((File)source);
         try {
            accessors = new Method[]{
               type.getMethod("getFile", new Class[]{String.class}),
               type.getMethod("setFile", new Class[]{String.class,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (key instanceof CharSequence) {
         String name = key.toString();
         accessors = getClassProperties(type).get(name);
         if (accessors == null)
            value = (V)methodsMap.get(type).get(name);
      }
      else if (!(key instanceof Number))
         return; //TODO: lookout for array props or javax.name or others
      else if (source instanceof List) {
         try {
            accessors = new Method[]{
               type.getMethod("get", new Class[]{Integer.TYPE}),
               type.getMethod("set", new Class[]{Integer.TYPE,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (source instanceof Collection) {
         try {
            accessors = new Method[]{
               Property.class.getMethod("getIndexProperty", new Class[]{Collection.class,Integer.TYPE}),
               null
            };
         }
         catch (Exception e) {}
      }
      else if (source.getClass().isArray()) {
         try {
            accessors = new Method[]{
               Array.class.getMethod("get", new Class[]{Object.class,Integer.TYPE}),
               Array.class.getMethod("set", new Class[]{Object.class,Integer.TYPE,Object.class})
            };
         }
         catch (Exception e) {}
      }
      else if (source instanceof CharSequence) {
         try {
            accessors = new Method[]{
               type.getMethod("charAt", new Class[]{Integer.TYPE}),
               null
            };
         }
         catch (Exception e) {}
      }
   }

   private Object[] getParams(Method method, int type, Object value) {

      /**
      * il faut voir les differentes combinaisons possibles
      *
      * instance getProperty()  i 0
      * instance get(property)  i 1 p
      * static getProperty(source)   s 1 s
      * static get(source,property)  s 2 s p
      * Class instance getField(source) i 1 s
      * Property static getProperty(source) s 1 s
      *
      * instance setProperty(value)    i 1 v
      * instance set(property,value)    i 2 p v
      * static setProperty(source,value)   s 2 s v
      * static set(source,property,value)   s 3 s p v
      * Class instance setField(source,value)  i 2 s v
      * Property static setProperty(source,value)  s 2 s v
      */
      int np = method.getParameterTypes().length;
      boolean isStatic = Modifier.isStatic(method.getModifiers());
      switch (type) {
         case GETTER:
            switch (np) {
               case 0:
                  return new Object[0];
               case 1:
                  if (isStatic)
                     return new Object[]{source};
                  else if (method.getDeclaringClass().equals(Class.class))
                     return new Object[]{source};
                  else
                     return new Object[]{key};
               case 2:
                  return new Object[]{source, key};
               default:
                  throw new RuntimeException("Cannot call a getter with "+np+" parameters");
            }
         case SETTER:
            switch (np) {
               case 0:
                  throw new RuntimeException("Cannot call a setter without parameter");
               case 1:
                  return new Object[]{value};
               case 2:
                  if (isStatic)
                     return new Object[]{source, value};
                  else if (method.getDeclaringClass().equals(Class.class))
                     return new Object[]{source, value};
                  else
                     return new Object[]{key, value};
               case 3:
                  return new Object[]{source, key, value};
               default:
                  throw new RuntimeException("Cannot call a setter with "+np+" parameters");
            }
         default:
            throw new RuntimeException("No such type of method: "+type);
      }
   }

   public static Method[] getFieldAccessors() {
      Method[] accessors = new Method[ACCESSORS];
      try {
         Class[] types = new Class[]{Object.class, Object.class};
         accessors[GETTER] = Property.class.getMethod("getFieldValue", types);
         types = new Class[]{Object.class, Object.class,Object.class};
         accessors[SETTER] = Property.class.getMethod("setFieldValue", types);
      }
      catch (Exception e) {
         Context.exception(e, "Property.getFieldAccessors()");
      }
      return accessors;
   }
}
