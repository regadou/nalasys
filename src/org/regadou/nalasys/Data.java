package org.regadou.nalasys;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class Data implements Serializable, Comparable, Iterable  {
   private static final int DIM_LEVEL=0, DIM_ENERGY=1, DIM_TIME=2, DIM_SPACE=3,
                            DIM_TYPE=4, DIM_PLUS=5, DIM_ZERO=6, DIM_MINUS=7;
   private static final int LEV_NO=0,  LEV_QUA=1, LEV_DIR=2, LEV_POS=3,
                            LEV_REL=4, LEV_IDE=5, LEV_VIS=6, LEV_ALL=7;
   private static final Class[][] concepts = new Class[64][10];
   private static final Map<Class,Integer> classes = new LinkedHashMap<Class,Integer>();
   private static final Class[] types = {Void.class,       Number.class,    Action.class,  Map.class,
                                         Collection.class, String.class,    Perception.class,  Data.class};
   private static final String[] levels = {"nothing", "quantity","direction","position",
                                           "relation","identity","vision",   "all"};
   private static final String[] typeNames = {"nothing", "number", "action",     "entity",
                                              "group",   "text",   "perception", "all"};
   public static final Collection<Class> stringables = Arrays.asList(new Class[]{
      String.class, CharSequence.class, char[].class, byte[].class,
      File.class, URI.class, URL.class, Database.class,
      Data.class, Number.class, Boolean.class,
      Class.class, Throwable.class
   });

   public static final Data ALL = new Data() {
      private int myConceptId = types.length-1;
      public Class getType() { return types[myConceptId%types.length]; }
      public Class getConcept() { return concepts[myConceptId][0]; }
      public Object getValue() { return Double.POSITIVE_INFINITY; }
      public void setValue(Object src) {}
   };

   static {
      for (int t = 0; t < types.length; t++) {
         concepts[DIM_TYPE*types.length + t][0] = types[t];
         for (int i = 0; i < 2; i++)
            concepts[(DIM_PLUS+i)*types.length + t][0] = Operator.class;
      }
      concepts[DIM_TIME*types.length + LEV_POS][0] = Date.class;
      // mettre ici les autres classes: Duration, Sequence, GPS, size, mass, color
   }

   private int conceptId;      // 1 -> 64 (or Enum)
   private Object value;       // depends on typeId => Numeric, Action, Entity, Group, Property, Matrix
   public String uri;
   public String creator;
   public Date created;
   public String destroyer;
   public Date destroyed;
   public List<Map<Date,Map<String,Object>>> history;

   public Data() {
      setValue(null);
   }

   public Data(Object src) {
      setValue(src);
   }

   public boolean isEmpty() {
      return empty(value, conceptId%types.length);
   }

   public Class getType() {
      if (conceptId <= 0 || conceptId >= concepts.length)
         conceptId = conceptId(value);
      return types[conceptId%types.length];
   }

   public Class getConcept() {
      if (conceptId <= 0 || conceptId >= concepts.length)
         conceptId = conceptId(value);
      return concepts[conceptId][0];
   }

   public Object getValue() {
      return Converter.getValue(value);
   }

   public void setValue(Object src) {
      if (conceptId <= 0 || conceptId >= concepts.length)
         conceptId = conceptId(src);
      value = Converter.convert(src, concepts[conceptId][0]);
   }

   public String toString() {
      return Converter.toString(value);
   }

   public Iterator iterator() {
      return new DataIterator(this);
   }

   public boolean equals(Object that) { return compareTo(that) == 0; }

   public int compareTo(Object that) {
      return compare(this, that);
   }

   public static Data data(Object src) {
      return (src instanceof Data) ? (Data)src : new Data(src);
   }

   public static byte[] concept(Object src) {
      return null;
   }

   public static boolean stringable(Object src) {
      if (src == null)
         return false;
      for (Class cl : Converter.getTypeClasses(src.getClass(), true)) {
         if (stringables.contains(cl))
            return true;
      }
      return false;
   }

   public static boolean empty(Object value, int typeId) {
      switch (typeId) {
         case LEV_NO:
            return (value == null) ? true : empty(value, conceptId(value)%types.length);
         case LEV_QUA:
            return Converter.toNumber(value).doubleValue() == 0;
         case LEV_DIR:
            return value != null;
         case LEV_POS:
         case LEV_REL:
         case LEV_VIS:
            return Converter.toNumber(new Property(value, "length").getValue(), 0).doubleValue() == 0;
         case LEV_IDE:
            return (value == null) ? true : value.toString().trim().equals("");
         case LEV_ALL:
            return false;
         default:
            return empty(value, conceptId(value)%types.length);
      }
   }

   public static int compare(Object o1, Object o2) {
      if (o1 == o2 || (o1 != null && o1.equals(o2)) || (empty(o1,LEV_NO) && empty(o2,LEV_NO)))
         return 0;
      else if (o1 instanceof Class || o2 instanceof Class) {
         try {
            Class c1 = (o1 instanceof Class) ? (Class)o1 : Class.forName(o1.toString());
            Class c2 = (o2 instanceof Class) ? (Class)o2 : Class.forName(o2.toString());
            if (c1 == c2)
               return 0;
            else if ((c1.isInterface() || Modifier.isAbstract(c1.getModifiers()))
                     && c1.isAssignableFrom(c2))
               return 0;
            else if ((c2.isInterface() || Modifier.isAbstract(c2.getModifiers()))
                     && c2.isAssignableFrom(c1))
               return 0;
            return c1.toString().compareToIgnoreCase(c2.toString());
         }
         catch (Exception e) {}
      }
      else if (o1 instanceof Number || o2 instanceof Number
            || o1 instanceof Boolean || o2 instanceof Boolean) {
         Number n1 = Converter.toNumber(o1, null);
         if (n1 == null)
            n1 = Converter.toNumber(new Property(o1,"length").getValue(), 0);
         Number n2 = Converter.toNumber(o2, null);
         if (n2 == null)
            n2 = Converter.toNumber(new Property(o2,"length").getValue(), 0);
         double dif = n1.doubleValue() - n2.doubleValue();
         return (dif > 0) ? 1 : ((dif < 0) ? -1 : 0);
      }
      else if (o1 == null)
         return (o2 == null) ? 0 : -1;
      else if (o2 == null)
         return 1;
      // else if is stringable
      // else if is collection or array
      // else map compare

      return o1.toString().compareToIgnoreCase(o2.toString());
   }

   public static boolean contains(Map parent, Map child) {
       if (child == null || child.isEmpty())
           return true;
       else if (parent == null || parent.isEmpty())
           return false;
       for (Object key : child.keySet()) {
           if (Data.compare(parent.get(key), child.get(key)) != 0)
               return false;
       }
       return true;
   }

   public static Class commonType(Class c1, Class c2) {
      List<Class> classes = Arrays.asList(Converter.getTypeClasses(c1, true));
      for (Class c : Converter.getTypeClasses(c2, true)) {
         if (c == Object.class)
            continue;
         else if(classes.contains(c))
            return c;
      }
      return null;
   }

   public static int conceptId(Object src) {
      if (src == null)
         return LEV_NO;
      else if (src instanceof char[])
         return DIM_TYPE*types.length + LEV_IDE;
      Class c = src.getClass();
      if (c.isArray())
         return DIM_TYPE*types.length + LEV_REL;
      Integer id = classes.get(c);
      if (id == null) {
         for (Map.Entry<Class,Integer> entry : classes.entrySet()) {
            if (entry.getKey().isAssignableFrom(c) || c.isAssignableFrom(entry.getKey()))
               return entry.getValue();
         }
         return DIM_TYPE*types.length + LEV_POS;
      }
      return id;
   }
}

class DataIterator implements Iterator<Data> {

   Iterator iterator;

   public DataIterator(Data data) {
      iterator = Converter.toCollection((data==null)?null:data.getValue()).iterator();
   }

   public boolean hasNext() { return iterator.hasNext(); }

   public Data next() { return Converter.toData(iterator.next()); }

   public void remove() { iterator.remove(); }
}