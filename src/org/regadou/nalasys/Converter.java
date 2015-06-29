package org.regadou.nalasys;

import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.net.*;
import javax.script.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.naming.OperationNotSupportedException;

public final class Converter {

   // configurable global properties
   public static String[] defaultClassPackages = "java.lang,java.util".split(",");
   public static String[] javaClassProtocols = "java,class,jvm".split(",");
   public static char[] textSplitters = ",;\t .-_|/\\".toCharArray();

   private static final String typeChars = "0naegtvA";
   private static final Class[] typeLevels = new Class[]{
      Object.class,     Number.class, Action.class, Map.class,
      Collection.class, String.class, Object.class, Object.class
   };
   private static final List<Class> stringables = Arrays.asList(new Class[]{
      CharSequence.class, File.class, URI.class, URL.class,
      Number.class, Boolean.class, Character.class, Locale.class
   });
   private static final String[] DATE_PARTS = {"yyyy","MM","dd","HH","mm","ss","SSS"};
   private static final char[] DATE_SEPS = {'-','-',' ',':',':','.',' '};


   public static <T extends Object> T convert(Object obj, Class<T> dst) {
      if (dst != null && dst.isInstance(obj))
         return (T)obj;
      else if (dst == Void.class || dst == Void.TYPE)
         return null;
      else if (obj instanceof char[])
         return convert(new String((char[])obj), dst);
      else if (obj instanceof byte[])
         return convert(new String((byte[])obj), dst);
      else if (dst == null) {
         if (obj == null || obj.toString().trim().equals(""))
            return null;
         else if (obj instanceof CharSequence)
            return (T) obj.toString();
         else if (obj instanceof char[])
            return (T) new String((char[])obj);
         else if (obj instanceof byte[])
            return (T) new String((byte[])obj);
         else if (obj.getClass().isArray())
            return (T) newInstance(null, Arrays.asList(toArray(obj)));
         else if (obj instanceof Collection)
            return (T) newInstance(null, (Collection)obj);
         else if (obj instanceof Map)
            return (T) newInstance(null, (Map)obj);
         else
            return (T)obj;
      }
      else if (dst.isArray())
         return (T)toArray(obj, dst.getComponentType());
      else if (dst.equals(Object.class))
         return (T) obj;
      else if (dst.equals(Void.class))
         return (T) obj;
      else if (dst.isPrimitive()) {
         if (Character.TYPE.equals(dst)) {
            char value;
            if (obj == null)
               value = '\u0000';
            else if (obj instanceof Number)
               value = (char) ((Number)obj).intValue();
            else if (obj instanceof Boolean)
               value = ((Boolean)obj).booleanValue() ? '\u0001' : '\u0000';
            else {
               String txt = obj.toString();
               value = txt.equals("") ? '\u0000' : txt.charAt(0);
            }
            return (T) new Character(value);
         }
         else if(Boolean.TYPE.equals(dst)) {
            if (obj == null || obj.toString().trim().equals(""))
               return (T)Boolean.FALSE;
            else {
               Number n = toNumber(obj, null);
               return (T) ((n == null) ? Boolean.TRUE : Boolean.valueOf(n.doubleValue() != 0));
            }
         }
         else {
            Number n = toNumber(obj, null);
            if (n == null)
               n = new Integer(0);
            if(Byte.TYPE.equals(dst))
               return (T) new Byte(n.byteValue());
            else if(Short.TYPE.equals(dst))
               return (T) new Short(n.shortValue());
            else if(Integer.TYPE.equals(dst))
               return (T) new Integer(n.intValue());
            else if(Long.TYPE.equals(dst))
               return (T) new Long(n.longValue());
            else if(Float.TYPE.equals(dst))
               return (T) new Float(n.floatValue());
            else if(Double.TYPE.equals(dst))
               return (T) new Double(n.doubleValue());
            else
               return (T) n;
         }
      }

      Class src = (obj == null) ? null : obj.getClass();
      scanClass(src);
      scanClass(dst);
      Converter c = findConverter(src, dst);

      if (c != null)
         return (T)execute(c.action, new Object[]{obj});
      else if (obj == null) {
         try { return dst.newInstance(); }
         catch (Exception e) {
            Context.currentContext().exception(e, "Converter.convert("+obj+","+dst+")");
            return null;
         }
      }
      else if (obj instanceof Collection)
         return (T) newInstance(dst, (Collection)obj);
      else if (obj instanceof Map)
         return (T) newInstance(dst, (Map)obj);
      else if (src.isArray())
         return (T) newInstance(dst, Arrays.asList(toArray(obj)));
      else
         return (T) newInstance(dst, Collections.singleton(obj));
   }

   public static void addConverter(Converter conv) {
      addConverter(conv, conv.source, conv.target);
   }

   public static void addConverter(Converter conv, Class src, Class dst) {
      if (converters.isEmpty()) {
         converters.put(Converter.class, new LinkedHashMap<Class,Converter>());
         scanClass(Converter.class);
      }

      Map<Class,Converter> map = converters.get(dst);
      if (map == null) {
         map = new LinkedHashMap<Class,Converter>();
         converters.put(dst, map);
      }
      else if (map.get(src) != null || map.get(Object.class) != null)
         return;
      map.put(src, conv);
   }

   public static Class[] getTypeClasses(Class type, boolean addThis) {
      if (type == null)
         return new Class[]{Object.class};
      Set<Class> lst = new LinkedHashSet<Class>();

      for (Class c = type; c != null; c = c.getSuperclass()) {
         if (addThis || c != type)
            lst.add(c);
         for (Class i : c.getInterfaces()) {
            lst.add(i);
         }
      }
/** TODO: how to create an instance reliably as param for field.get(Object)
      try {
          java.lang.reflect.Field f = c.getField("TYPE");
          if (f.getType().equals(Class.class))
              lst.add((Class)f.get(f));
      }
      catch (Exception e) {}
**/
      return lst.toArray(new Class[lst.size()]);
   }

   public static <T extends Object> T newInstance(Class<T> type, Map src) {
      if (src == null)
         src = Collections.EMPTY_MAP;

      if (type == null) {
         Object c = src.get("class");
         if (c == null) {
            c = src.get("type");
            if (c != null)
               type = toClass(c);
         }
         else if (c instanceof Class)
            type = (Class)c;
         else
            type = toClass(c);

         if (type == null) {
            Map dst = new LinkedHashMap();
            for (Object e : src.entrySet()) {
               Map.Entry entry = (Map.Entry)e;
               dst.put(entry.getKey(), convert(entry.getValue(), null));
            }
            return (T)dst;
         }
         else if (src.size() == 1)
            return (T)type;
      }

      Object dst;
      try {
         dst = type.newInstance();
         Map map = new Entity(dst);
         for (Object e : src.entrySet()) {
            Map.Entry entry = (Map.Entry)e;
            map.put(entry.getKey(), convert(entry.getValue(), null));
         }
      }
      catch (Exception e) { dst = null; }

      return (T)dst;
   }

   public static <T extends Object> T newInstance(Class<T> type, Collection src) {
      List dst;
      if (src == null)
         dst = Collections.EMPTY_LIST;
      else {
         dst = new ArrayList();
         for (Object e : src)
            dst.add(convert(e, null));
      }
      if (type == null) {
         if (dst.isEmpty())
            return null;
         Object first = dst.remove(0);
         if (first instanceof Class)
            type = (Class)first;
         else {
            dst.add(0, first);
            return (T) dst;
         }
      }
      if (type.equals(Object.class) || type.equals(Void.class))
         return (T)dst;

      Object[] a = dst.toArray();
      Object c = getAction(new Object[]{type, a});
      try { return (T) ((Constructor)c).newInstance(a); }
      catch (Exception e) { return null; }
   }

   public static Object getValue(Object src) {
      if (src instanceof Map.Entry)
         return getValue(((Map.Entry)src).getValue());
      else if (src instanceof Collection) {
         Collection c = (Collection)src;
         switch (c.size()) {
            case 0:
               return null;
            case 1:
               return getValue(c.iterator().next());
            default:
               return src;
         }
      }
      else if (src instanceof char[])
         return new String((char[])src);
      else if (src != null && src.getClass().isArray()) {
         switch (Array.getLength(src)) {
            case 0:
               return null;
            case 1:
               return getValue(Array.get(src, 0));
            default:
               return src;
         }
      }
      else if (src instanceof Data)
         return getValue(((Data)src).getValue());
      else
         return src;
   }

   public static Object getPathValue(Object[] path) {
      if (path == null || path.length == 0)
         return null;
      else if (path.length == 1)
         return  path[0];

      Object value = getValue(path[0]);
      if (value == null)
         return null;
      Object property;
      int last = path.length - 1;
      for (int i = 1; i < last; i++) {
         property = getValue(path[i]);
         if (property instanceof Map)
            value = selectValues(value, (Map)property);
         else
            value = new Property(value, property).getValue();
         if (value == null)
            return null;
      }
      property = getValue(path[last]);
      if (property instanceof Map)
         return selectValues(value, (Map)property);
      else
         return new Property(value, property);
   }

   public static Probability getValueMatch(Object value, Map filter) {
      if (filter == null || filter.isEmpty())
         return Probability.TRUE;
      Map map = null;
      List lst = null;
      if (value == null)
         map = Collections.EMPTY_MAP;
      else if (value instanceof Collection)
         lst = (value instanceof List) ? (List)value : new ArrayList((Collection)value);
      else if (value.getClass().isArray())
         lst = toList(value);
      else
         map = toMap(value);

      float total = filter.size();
      int got = 0;
      for (Object e : filter.entrySet()) {
         Map.Entry entry = (Map.Entry)e;
         Object key = entry.getKey();
         Object v1 = entry.getValue();
         Object v2;
         if (key == null) {
            if (lst != null)
               v2 = lst.isEmpty() ? null : lst.get(0);
            else
               v2 = map.get(null);
         }
         else if (lst != null) {
            if (key.equals("length") || key.equals("size") || key.equals("count"))
               v2 = lst.size();
            else {
               Number n = toNumber(key, null);
               if (n == null)
                  v2 = null;
               else {
                  int i = n.intValue();
                  if (i != n.floatValue() || i < 0 || i >= lst.size())
                     v2 = null;
                  else
                     v2 = lst.get(i);
               }
            }
         }
         else if (key instanceof Number) {
            float f = ((Number)key).floatValue();
            v2 = map.get(f);
            if (v2 == null && f == 0)
               v2 = map;
         }
         else
            v2 = map.get(key);
         if (Data.compare(v1, v2) == 0)
            got++;
      }

      return (got == total) ? Probability.TRUE : new Probability(got/total);
   }

   public static Collection selectValues(Object src, Map filter) {
      while (src instanceof Map.Entry || src instanceof Data) {
         if (src instanceof Map.Entry)
            src = ((Map.Entry)src).getValue();
         else
            src = ((Data)src).getValue();
      }
      if (src instanceof Database) {
         if (filter == null || filter.isEmpty())
            return Collections.singletonList(src);
         try { return ((Database)src).getRows(filter); }
         catch (Exception e) { return toCollection(Context.exception(e, "selectValues("+filter+","+src+")")); }
      }
      else if (src instanceof File) {
         File file = (File)src;
         if (file.isDirectory())
            src = file.listFiles();
      }

      Collection lst = (src instanceof Collection) ? (Collection)src : new Group(src);
      if (filter == null || filter.isEmpty())
         return lst;

      List results = new ArrayList();
      for (Object value : lst) {
         Probability p = getValueMatch(value, filter);
         if (p.equals(true))
            results.add(value);
      }
      return results;
   }

   public static int insertValues(Object src, Map filter, Object data) {
      if (data == null)
         return 0;
      if (filter == null)
         filter = Collections.EMPTY_MAP;
      if (src instanceof Database) {
         Map row = toMap(data);
         if (row.isEmpty())
            return 0;
         Object table = filter.get("table");
         if (table == null) {
            table = filter.get("class");
            if (table == null) {
               table = row.get("table");
               if (table == null) {
                  table = row.get("class");
                  if (table == null)
                     throw new RuntimeException("Cannot find table name for insert");
               }
            }
         }
         try { return ((Database)src).putRow(table.toString(), row, null); }
         catch (Exception e) {
            Context.exception(e, "Converter.insertValues("+src+","+filter+","+data+")");
            return 0;
         }
      }
      else if (src instanceof File) {
         Collection files = selectValues(src, filter);
         int nb = 0;
         for (Object x : files) {
            if (x instanceof File) {
               Stream s = new Stream((File)x);
               String type = s.getMimetype();
               Mimetype.getMimetype(type).save(s.getOutputStream(), data, type);
               s.close();
               nb++;
            }
         }
         return nb;
      }

      int result = 0;
      Collection lst = selectValues(src, filter);
      if (data instanceof Collection) {
         Collection c = (Collection)data;
         result = c.size();
         lst.addAll(c);
      }
      else if (data.getClass().isArray()) {
         Collection c = toCollection(data);
         result = c.size();
         lst.addAll(c);
      }
      else {
         lst.add(data);
         result = 1;
      }

      return result;
   }

   public static int updateValues(Object src, Map filter, Object data) {
      if (data == null)
         return 0;
      if (filter == null)
         filter = Collections.EMPTY_MAP;
      if (src instanceof Database) {
         Map row = toMap(data);
         Object table = filter.get("table");
         if (table == null) {
            table = filter.get("class");
            if (table == null) {
               table = row.get("table");
               if (table == null) {
                  table = row.get("class");
                  if (table == null)
                     throw new RuntimeException("Cannot find table name for update");
               }
            }
         }
         Database db = (Database)src;
         try { return (db.putRow(table.toString(), row, db.getCondition(table.toString(), filter))); }
         catch (Exception e) {
            Context.exception(e, "Converter.updateValues("+src+","+filter+","+data+")");
            return 0;
         }
      }
      else if (src instanceof File) {
         Collection files = selectValues(src, filter);
         int nb = 0;
         for (Object x : files) {
            if (x instanceof File) {
               Stream s = new Stream((File)x);
               String type = s.getMimetype();
               Mimetype.getMimetype(type).save(s.getOutputStream(), data, type);
               s.close();
               nb++;
            }
         }
         return nb;
      }

      Collection lst = selectValues(src, filter);
      Collection other = (data instanceof Collection || data.getClass().isArray())
                       ? toCollection(data) : Collections.singletonList(data);

      return other.size();
   }

   public static int deleteValues(Object src, Map filter) {
      if (filter == null)
         filter = Collections.EMPTY_MAP;
      if (src instanceof Database) {
         Object table = filter.get("table");
         if (table == null) {
            table = filter.get("class");
            if (table == null)
               throw new RuntimeException("Cannot find table name for delete");
         }
         Database db = (Database)src;
         try { return (db.execute("delete from "+table+db.getCondition(table.toString(), filter))); }
         catch (Exception e) {
            Context.exception(e, "Converter.deleteValues("+src+","+filter+")");
            return 0;
         }
      }
      else if (src instanceof File) {
         Collection files = selectValues(src, filter);
         int nb = 0;
         for (Object x : files) {
            if (x instanceof File) {
               File file = (File)x;
               if (file.delete())
                  nb++;
            }
         }
         return nb;
      }
      else if (src == null)
         return 0;
      else if ((src instanceof Collection || src.getClass().isArray())) {
         Collection main = toCollection(src);
         Collection todelete = selectValues(src, filter);
         Object[] array = null;
         int deleted = 0;
         for (Object obj : todelete) {
            try {
               if (main.remove(obj))
                  deleted++;
            }
            catch (UnsupportedOperationException e) {
               if (array == null)
                  array = main.toArray();
               int i = Arrays.binarySearch(array, obj);
               if (i >= 0) {
                  array[i] = null;
                  deleted++;
               }
            }
         }
         return deleted;
      }
      else
         return 0;
   }

   public static boolean isStringable(Class type) {
      if (type == null)
         return false;
      for (Class c : getTypeClasses(type, true)) {
         if (stringables.contains(c))
            return true;
      }
      return false;
   }

   public static String toString(Object src) {
      if (src == null)
         return "";
      else if (src instanceof CharSequence)
         return src.toString();
      else if (src instanceof Character)
         return src.toString();
      else if (src instanceof Byte)
         return src.toString();
      else if (src instanceof char[])
         return new String((char[])src);
      else if (src instanceof byte[])
         return new String((byte[])src);
      else if (src.getClass().isArray()) {
         StringBuilder buffer = new StringBuilder("(");
         int n = Array.getLength(src);
         for (int i = 0; i < n; i++) {
            if (i > 0)
               buffer.append(" ");
            buffer.append(toString(Array.get(src, i)));
         }
         return buffer.append(")").toString();
      }
      else if (src instanceof Collection)
         return toString(((Collection)src).toArray());
      else if (src instanceof Map)
         return toString(((Map)src).entrySet().toArray());
      else if (src instanceof Map.Entry) {
         Map.Entry e = (Map.Entry)src;
         return toString(e.getKey())+"="+toString(e.getValue());
      }
      else if (src instanceof Class)
         return ((Class)src).getName();
      else if (src instanceof File || src instanceof URL || src instanceof URI)
         return src.toString();
      else if (src instanceof Database)
         return ((Database)src).getUrl();
      else if (src instanceof Locale)
         return ((Locale)src).getLanguage();
      else if (src instanceof Number)
         return src.toString();
      else if (src instanceof Boolean)
         return new Probability((Boolean)src).toString();
      else if (src instanceof Data)
         return toString(((Data)src).getValue());
      else
         return toString(new Entity(src));
   }

   public static CharSequence toCharSequence(Object src) {
       return toString(src);
   }

   public static Number toNumber(Object src) {
       Number n = toNumber(src, null);
       return (n == null) ? new Probability(0) : n;
   }

   public static Number toNumber(Object src, Number defaultValue) {
      if (src instanceof Number)
         return (Number)src;
      else if (src instanceof Boolean)
         return new Probability((Boolean)src);
      else if (src == null)
         return defaultValue;
      else if (src instanceof char[])
         return toNumber(new String((char[])src), defaultValue);
      else if (src instanceof byte[])
         return toNumber(new String((byte[])src), defaultValue);
      else if (src.getClass().isArray())
         return Array.getLength(src);
      else if (src instanceof Collection)
         return ((Collection)src).size();
      else if (src instanceof Map)
         return ((Map)src).size();
      else if (src instanceof javax.xml.datatype.Duration)
         return ((javax.xml.datatype.Duration)src).getTimeInMillis(new Date(0));
      else if (src instanceof Map.Entry)
         return toNumber(((Map.Entry)src).getValue(), defaultValue);
      else if (src instanceof Data)
         return toNumber(((Data)src).getValue(), defaultValue);
      else {
         try {
            String txt = src.toString().toLowerCase().trim();
            if (txt.length() == 0)
               return defaultValue;
            else if(txt.charAt(txt.length() - 1) == '%')
               return new Probability(txt);
            else if (txt.indexOf('i') >= 0 && txt.length() > 1)
               return new Complex(txt);
            else if (txt.indexOf('.') >= 0 || txt.indexOf('e') >= 0)
               return new Double(txt);
            else if (txt.startsWith("0x"))
               return Long.parseLong(txt.substring(2), 16);
            else if (txt.startsWith("x"))
               return Long.parseLong(txt.substring(1), 16);
            else if (txt.endsWith("h"))
               return Long.parseLong(txt.substring(0, txt.length()-1), 16);
            else
               return new Long(txt);
         }
         catch (Exception e) {
            return defaultValue;
         }
      }
   }

   public static boolean toBoolean(Object src) {
      if (src instanceof Boolean)
         return (Boolean)src;
      else if (src == null)
         return false;
      else if (src instanceof Number)
         return ((Number)src).intValue() != 0;
      else if (src.getClass().isArray())
         return Array.getLength(src) > 0;
      else if (src instanceof Collection)
         return !((Collection)src).isEmpty();
      else if (src instanceof Map)
         return !((Map)src).isEmpty();
      else if (src instanceof Map.Entry)
         return toBoolean(((Map.Entry)src).getValue());
      else {
         Number n = toNumber(src, null);
         if (n != null)
            return n.intValue() != 0;
         String txt = src.toString().trim().toLowerCase();
         switch (txt.length()) {
            case 0:
               return false;
            case 1:
               switch (txt.charAt(0)) {
                  case 't':
                  case 'y':
                  case 'o':
                  case 'v':
                     return true;
                  case 'f':
                  case 'n':
                     return false;
               }
            default:
               return txt.equals("true");
         }
      }
   }

   public static Action toAction(Object src) {
       return toAction(src, true);
   }

   public static Action toAction(Object src, boolean first) {
       if (src instanceof Action)
          return (Action)src;
       else if (src instanceof Map.Entry)
          return toAction(((Map.Entry)src).getValue(), true);
       Action a = Action.getAction(src);
       if (a != null)
          return a;
       else if (!first)
          return null;
       else
          return toAction(Context.currentContext().getAttribute(src.toString()), false);
   }

   public static Object[] toArray(Object src) {
      if (src == null)
         return new Object[0];
      else if (src.getClass().isArray()) {
         if (!src.getClass().getComponentType().isPrimitive())
            return (Object[])src;
         int len = Array.getLength(src);
         Object[] dst = new Object[len];
         for (int i = 0; i < len; i++)
            dst[i] = Array.get(src, i);
         return dst;
      }
      else
         return toCollection(src).toArray();
   }

   public static<T extends Object> T[] toArray(Object src, Class<T> type) {
      Object[] a = toArray(src);
      if (type == null || type.equals(Object.class) || type.isAssignableFrom(a.getClass().getComponentType()))
         return (T[])a;
      T[] dst = (T[])Array.newInstance(type, a.length);
      for (int i = 0; i < a.length; i++)
         dst[i] = convert(a[i], type);
      return dst;
   }

   public static Collection toCollection(Object src) {
      if (src instanceof Collection)
         return (Collection)src;
      else if (src == null)
         return new ArrayList();
      else if (src.getClass().isArray())
         return Arrays.asList(toArray(src));
      else if (src instanceof Map)
         return ((Map)src).entrySet();
      else if (src instanceof Map.Entry)
         return toCollection(((Map.Entry)src).getValue());
      else if (src instanceof Dimension) {
         Dimension d = (Dimension)src;
         return Arrays.asList(new Object[]{d.width, d.height});
      }
      else if (src instanceof Point2D) {
         Point2D p = (Point2D)src;
         return Arrays.asList(new Object[]{p.getX(), p.getY()});
      }
      else if (src instanceof Rectangle) {
         Rectangle r = (Rectangle)src;
         return Arrays.asList(new Object[]{r.x, r.y, r.width, r.height});
      }
      else if (src instanceof Iterator) {
          List dst = new ArrayList();
          Iterator i = (Iterator)src;
          while (i.hasNext())
              dst.add(i.next());
          return dst;
      }
      else if (src instanceof Enumeration) {
          List dst = new ArrayList();
          Enumeration e = (Enumeration)src;
          while (e.hasMoreElements())
              dst.add(e.nextElement());
          return dst;
      }
      else if (src instanceof File && ((File)src).isDirectory())
         return Arrays.asList(((File)src).listFiles());
      else {
         List lst = new ArrayList();
         lst.add(src);
         return lst;
      }
   }

   public static List toList(Object src) {
      if (src instanceof List)
         return (List)src;
      else if (src == null)
         return new ArrayList();
      else if (src instanceof Collection)
         return new ArrayList((Collection)src);
      else if (src instanceof Map.Entry)
         return toList(((Map.Entry)src).getValue());
      else
         return new ArrayList(Arrays.asList(toArray(src)));
   }

   public static Map toMap(Object src) {
      if (src instanceof Map)
         return (Map)src;
      else if (src instanceof Map.Entry)
         return toMap(new Object[]{src});
      else if (src instanceof Data)
         return toMap(((Data)src).getValue());
      else if (src == null)
         return new LinkedHashMap();
      else if (src instanceof char[])
         return toMap(new String((char[])src));
      else if (src instanceof byte[])
         return toMap(new String((byte[])src));
      else if (src.getClass().isArray()) {
         Map map = new LinkedHashMap();
         int n = Array.getLength(src);
         for (int i = 0; i < n; i++) {
            Object o = Array.get(src, i);
            if (o == null)
               continue;
            else if (o instanceof Collection || o.getClass().isArray()) {
               Object[] a = toArray(o);
               for (int j = 0; j < a.length; j+=2) {
                  Object value = (j+1 >= a.length) ? null : a[j+1];
                  map.put(a[j], value);
               }
            }
            else {
               Map.Entry e = toMapEntry(o);
               map.put(e.getKey(), e.getValue());
            }
         }
         return map;
      }
      else if (src instanceof Collection)
         return toMap(((Collection)src).toArray());
      else if (src instanceof CharSequence)
         return toMap(new Group(src.toString(), "&,".toCharArray()));
      else if (src instanceof Iterator)
         return toMap(new Group((Iterator)src));
      else if (src instanceof Enumeration)
         return toMap(new Group((Enumeration)src));
      else
         return new Entity(src);
   }

   public static Map.Entry toMapEntry(Object src) {
      if (src instanceof Map.Entry)
         return (Map.Entry)src;
      else if (src instanceof Map) {
         Map map = (Map)src;
         return map.isEmpty() ? null : (Map.Entry) map.entrySet().iterator().next();
      }
      else if (src instanceof Collection)
         return toMapEntry(toMap((Collection)src));
      else if (src == null)
         return null;
      else if (src instanceof char[])
         return toMapEntry(new String((char[])src));
      else if (src instanceof byte[])
         return toMapEntry(new String((byte[])src));
      else if (src.getClass().isArray())
         return toMapEntry(toMap(toArray(src)));
      else if (src instanceof URL)
         return new Property((URL)src);
      else if (src instanceof URI)
         return new Property((URI)src);
      else if (src instanceof File)
         return new Property((File)src);
      else
         return new Property(src.toString());
   }

   public static Class toClass(Object src) {
      if (src instanceof Class)
         return (Class)src;
      else if (src instanceof Number) {
         int n = ((Number)src).intValue();
         return (n >= 0 && n < typeLevels.length) ? typeLevels[n] : null;
      }
      else if (src == null)
         return Object.class;
      else if (src instanceof Map.Entry)
         return toClass(((Map.Entry)src).getValue());
      else {
         if (src instanceof char[])
            src = new String((char[])src);
         else if (src instanceof byte[])
            src = new String((byte[])src);
         String txt = src.toString().trim();
         int i = txt.indexOf(':');
         if (i > 0) {
            String proto = txt.substring(0,i).trim().toLowerCase();
            for (String javaproto : javaClassProtocols) {
               if (proto.equals(javaproto)) {
                  txt = txt.substring(i+1).trim();
                  break;
               }
            }
         }

         switch (txt.length()) {
            case 0:
               return Object.class;
            default:
               try { return Class.forName(txt); }
               catch (Exception e) {
                  String name = "." + txt.substring(0,1).toUpperCase() + txt.substring(1);
                  for (String p : defaultClassPackages) {
                     try { return Class.forName(p+name); }
                     catch (Exception e2) {}
                  }
               }
            case 1:
               int n = typeChars.indexOf(txt.charAt(0));
               return (n >= 0 && n < typeLevels.length) ? typeLevels[n] : null;
         }
      }
   }

   public static URI toUri(Object src) {
      try {
         if (src instanceof URI)
            return (URI)src;
         else if (src instanceof URL)
            return ((URL)src).toURI();
         else if (src instanceof File)
            return ((File)src).toURI();
         else if (src instanceof CharSequence) {
            Object obj = new Property(src.toString()).getSource();
            return (obj instanceof URL) ? ((URL)obj).toURI() : null;
         }
         else if (src instanceof char[])
            return toUri(new String((char[])src));
         else if (src instanceof byte[])
            return toUri(new String((byte[])src));
         else {
            Map map = toMap(src);
            StringBuilder buffer = new StringBuilder();
            Iterator i = map.entrySet().iterator();
            while (i.hasNext()) {
               Map.Entry e = (Map.Entry)i.next();
               buffer.append((buffer.length() == 0) ? "./?" : "&")
                     .append(URLEncoder.encode(toString(e.getKey()), Servlet.defaultEncoding))
                     .append(URLEncoder.encode(toString(e.getValue()), Servlet.defaultEncoding));
            }
            return new URI(buffer.toString());
         }
      }
      catch (Exception e) { return null; }
   }

   public static Database toDatabase(Object src) {
      if (src == null) {
         try { return Database.openDatabase(); }
         catch (Exception e) {
            Context.exception(e, "Converter.toDatabase()");
            return null;
         }
      }
      else if (src instanceof Database)
         return (Database)src;
      else {
         try { return Database.openDatabase(src.toString()); }
         catch (Exception e) {
            Context.exception(e, "Converter.toDatabase("+src+")");
            return null;
         }
      }
   }

   public static Data toData(Object src) {
      return (src instanceof Data) ? (Data)src : new Data(src);
   }

   public static Image toImage(Object src) {
      if (src instanceof Image)
         return (Image)src;
      else if (src == null)
         return new BufferedImage(0, 0, BufferedImage.TYPE_4BYTE_ABGR);
      else {
         String[] lines = src.toString().split("\n");
         int max = 0;
         for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].trim();
            max = Math.max(max, lines[i].length());
         }
         int fontsize = 10;
         int w = max * fontsize;
         int h = lines.length * fontsize;
         BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
         Graphics g = image.getGraphics();
         g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontsize));
         g.setColor(new Color(255,255,255));
         g.drawRect(0, 0, w, h);
         g.setColor(new Color(0,0,0));
         for (int i = 0; i < lines.length; i++)
            g.drawString(lines[i], fontsize/2, i*fontsize);
         return image;
      }
   }

   public static Color toColor(Object src) {
      if (src instanceof Color)
         return (Color)src;
      else if (src instanceof Collection) {
         Object[] a = ((Collection)src).toArray();
         int[] c = new int[4];
         int len = a.length;
         for (int i = 0; i < len; i++) {
            Number n = toNumber(a[i], null);
            c[i] = (n == null || n.intValue() < 0 || n.intValue() > 255) ? ((i < 3) ? 0 : 255) : n.intValue();
         }
         return new Color(c[0], c[1], c[2], c[3]);
      }
      else if(src == null)
         return new Color(0,0,0);
      else if(src instanceof char[])
         return toColor(new String((char[])src));
      else if (src instanceof byte[])
         return toColor(new String((byte[])src));
      else if (src.getClass().isArray())
         return toColor(Arrays.asList(toArray(src)));
      else if (src instanceof Map.Entry)
         return toColor(((Map.Entry)src).getValue());
      else {
         String txt = src.toString();
         while (txt.startsWith("#"))
            txt = txt.substring(1);
         try {
            Integer[] array = new Integer[4];
            switch (txt.length()) {
               case 3:
                  txt += "f";
               case 4:
                  for (int i = 0; i < array.length; i++)
                     array[i] = Integer.parseInt(txt.substring(i,i+1), 16);
                  return toColor(array);
               case 6:
                  txt += "ff";
               case 8:
                  for (int i = 0; i < array.length; i+=2)
                     array[i] = Integer.parseInt(txt.substring(i,i+2), 16);
                  return toColor(array);
               default:
                  return null;
            }
         }
         catch (Exception e) { return null; }
      }
   }

   public static Date toDate(Object src) {
      if (src instanceof Date)
         return (Date)src;
      else if(src instanceof Calendar)
         return ((Calendar)src).getTime();
      else if (src == null)
         return new Date();
      else if (src instanceof Number)
         return new Date(((Number)src).longValue());
      else if (src instanceof char[])
         return toDate(new String((char[])src));
      else if (src instanceof byte[])
         return toDate(new String((byte[])src));
      else if (src instanceof CharSequence) {
         String date = src.toString().trim();
         if (date.equals(""))
            return new Date();
         String format = "";
         for (int p = 0; p < DATE_PARTS.length; p++) {
            format += DATE_PARTS[p];
            try {
                return new java.text.SimpleDateFormat(format).parse(date);
            }
            catch (Exception e) {
               format += DATE_SEPS[p];
            }
         }
         return null;
      }
      else if (src instanceof Map.Entry)
         return toDate(((Map.Entry)src).getValue());
      else
         return toDate(src.toString());
   }

   public static Context toContext(Object obj) {
       Map src;
       ScriptContext cx = null;
       Context current = Context.currentContext();
       if (obj instanceof Context) {
           if (current.equals(obj))
               return current;
           cx = (Context)obj;
           src = new Entity(obj);
       }
       else if (obj instanceof ScriptContext) {
           cx = (Context)obj;
           src = new Entity(obj);
       }
       else if (obj == null)
           src = Collections.EMPTY_MAP;
       else if (obj instanceof Map)
           src = (Map)obj;
       else
           src = toMap(obj);

       Map dst = new Entity(current);
       for (Map.Entry entry : (Set<Map.Entry>)src.entrySet()) {
           Object value = entry.getValue();
           if (value != null)
              dst.put(entry.getKey(), value);
       }

       if (cx != null) {
           for (int s : cx.getScopes())
               current.setBindings(cx.getBindings(s), s);
       }
       return current;
   }

   private String name;
   private Class source;
   private Class target;
   private Object action;

   public Converter(Class source, Class target, Method method) {
      this.source = source;
      this.target = target;
      this.action = method;
   }

   public Converter(Class source, Class target, Constructor cons) {
      this.source = source;
      this.target = target;
      this.action = cons;
   }

   public Converter(Class source, Class target, Class methodClass, String methodName) {
      this.source = source;
      this.target = target;
      if (methodClass == null)
         methodClass = target;
      try { this.action = methodClass.getMethod(methodName, source); }
      catch (Exception e) { Context.currentContext().exception(e, "new Converter("+source+","+target+","+methodClass+","+methodName+")"); }
   }

   public Converter(Map map) {
      if (map == null || map.isEmpty())
         return;
      try {
         name = toString(map.get("name"));
         Object val = map.get("source");
         source = (val == null) ? null : toClass(val);
         val = map.get("target");
         target = (val == null) ? null : toClass(val);
         if (source == null && target == null)
            return;
         String txt = toString(map.get("method"));
         if (txt == null || txt.equals("new") || txt.equals("")) {
            if (target == null)
               return;
            action = getAction(target);
         }
         else
            action = getAction(new Object[]{target, txt, null});
      }
      catch (Exception e) {
         Context.currentContext().exception(e, "new Converter("+map+")");
      }
   }

   public String getName() { return name; }

   public void setName(String n) { name = n; }

   public String toString() {
      String txt = (name != null && !name.trim().equals("")) ? name
                 : (((source == null) ? "null" : source.getName())
                        + " -> " +
                    ((target == null) ? "null" : target.getName()));
      return "[Converter "+txt+"]";
   }

   public boolean equals(Object obj) {
      if (!(obj instanceof Converter))
         return false;
      Converter c = (Converter)obj;
      if (source == null) {
         if (c.source != null)
            return false;
      }
      else if (c.source == null || !source.equals(c.source))
         return false;

      if (target == null) {
         if (c.target != null)
            return false;
      }
      else if (c.target == null || !target.equals(c.target))
         return false;

      return true;
   }

   /****************** private fields and methods ****************************/

   private static Map scannedClasses = Collections.synchronizedMap(new LinkedHashMap());
   private static Map<Class,Map<Class,Converter>> converters = Collections.synchronizedMap(new LinkedHashMap<Class,Map<Class,Converter>>());

   private static Class getPrimitive(Class src) {
      if (src == null)
         return null;
      try {
         Field f = src.getField("TYPE");
         if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(Class.class))
            return (Class)f.get(null);
      }
      catch (Exception e) {}
      return null;
   }

   private static Converter findConverter(Class src, Class dst) {
      Converter c = getConverter(src, dst);
      if (c == null) {
         Class[] srcClasses = getTypeClasses(src, false);
         for (Class klass : srcClasses) {
            c = getConverter(klass, dst);
            if (c != null) {
               addConverter(c, src, dst);
               return c;
            }
         }
         if (src != null)
            c = getConverter(Object.class, dst);
         if (c == null) {
            Class primitive = getPrimitive(dst);
            if (primitive != null)
               return findConverter(src, primitive);
         }
      }
      return c;
   }

   private static Converter getConverter(Class src, Class dst) {
      Map<Class,Converter> map = converters.get(dst);
      if (map == null)
         return null;
      return map.get(src);
   }

   private static void scanClass(Class type) {
      if (type == null || scannedClasses.get(type) != null)
         return;
      scannedClasses.put(type, type);

      for (Constructor cons : type.getConstructors()) {
         Class[] params = cons.getParameterTypes();
         switch (params.length) {
            case 0:
               addConverter(new Converter(null, type, cons));
               break;
            case 1:
               if (!type.equals(params[0]) && !params[0].isPrimitive())
                   addConverter(new Converter(params[0], type, cons));
         }
      }

      for (Method method : type.getMethods()) {
         String name = method.getName();
         Class dst = method.getReturnType();
         Class[] params = method.getParameterTypes();
         if (name.length() > 2 && name.startsWith("to") && Character.isUpperCase(name.charAt(2))) {
            if (Modifier.isStatic(method.getModifiers())) {
               switch (params.length) {
                  case 0:
                     addConverter(new Converter(null, dst, method));
                     break;
                  case 1:
                     addConverter(new Converter(params[0], dst, method));
                     break;
               }
            }
            else if (params.length == 0)
               addConverter(new Converter(type, dst, method));
         }
      }

      Class[] lst = getTypeClasses(type, false);
      for (int c = 0; c < lst.length; c++)
         scanClass(lst[c]);
   }

   private static Object execute(Object action, Object[] params) {
     try {
       if (action instanceof Method)
           return ((Method)action).invoke(null, params);
       else if (action instanceof Constructor)
           return ((Constructor)action).newInstance(params);
       else
           return params;
     }
     catch (Exception e) { return e; }
   }

   private static Object getAction(Object src) {
       if (src instanceof Method)
           return src;
       else if (src instanceof Constructor)
           return src;
       else // look for array with class target method name params for constructor
           return null;
   }
}
