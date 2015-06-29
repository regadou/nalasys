package org.regadou.nalasys;

import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

public class Group extends ArrayList {

   public static final String openers = "([{";
   public static final String closers = ")]}";

   public static String[] split(String txt, char splitter) {
      if (txt == null || txt.equals(""))
         return new String[0];
      int n = txt.length();
      List<String> parts = new ArrayList<String>();
      for (int at = 0; at < n;) {
         int i = txt.indexOf(splitter, at);
         if (i < 0)
            i = n;
         parts.add(txt.substring(at,i));
         at = i+1;
      }
      return parts.toArray(new String[parts.size()]);
   }

   private Class type;

   public Group(String txt, char[] splitters) {
      if (txt == null || txt.equals(""))
         return;
      else if (splitters == null || splitters.length == 0)
         add(txt);
      else {
         String[] parts = null;
         for (char c : splitters) {
            String[] p = split(txt, c);
            if (parts == null || p.length > parts.length)
               parts = p;
         }
         addAll(Arrays.asList(parts));
      }
   }

   public Group(String txt, String splitter) {
      if (txt == null || txt.equals(""))
         return;
      else if (splitter == null || splitter.equals(""))
         add(txt);
      else
         addAll(Arrays.asList(txt.split(splitter)));
   }

   public Group() {
      this(new Object[0], null);
   }

   public Group(Object src) {
      this(src, null);
   }

   public Group(Object src, Class type) {
      super();
      this.type = (type == Object.class) ? null : type;
      Iterator i = null;
      if (src == null)
         ;
      else if(src instanceof Enumeration) {
         Enumeration e = (Enumeration)src;
         while (e.hasMoreElements())
            add(e.nextElement());
      }
      else if (src instanceof Iterator)
         i = (Iterator)src;
      else if (src instanceof Iterable)
         i = ((Iterable)src).iterator();
      else if (src.getClass().isArray()) {
         int n = Array.getLength(src);
         for (int e = 0; e < n; e++)
            add(Array.get(src, e));
      }
      else
         add(src);

      if (i != null) {
         while (i.hasNext())
            add(i.next());
      }
   }

   public Class getType() {
      return type;
   }

   public void setType(Class type) {
      if (type == null || type == Object.class)
         this.type = null;
      else if (isEmpty())
         this.type = type;
      else {
         type = Data.commonType(type, getCommonType());
         if (type != null)
            this.type = type;
      }
   }

   public Object get(int index) {
      if (index < 0 || index >= size())
         return null;
      return super.get(index);
   }

   public boolean add(Object e) {
      return addAt(-1, e);
   }

   public void add(int index, Object e) {
      addAt(index, e);
   }

   public boolean addAt(int index, Object e) {
      if (index < 0)
         index = size();
      while (index > size()) {
         if (!add(Converter.convert(null, type)))
            return false;
      }
      if (type != null)
         e = Converter.convert(e, type);
      super.add(index, e);
      return true;
   }

   public boolean addAll(Collection c) {
      return addAll(size(), c);
   }

   public boolean addAll(int index, Collection c) {
      if (c == null || c.isEmpty())
         return false;
      boolean changed = false;
      Iterator i = c.iterator();
      while (i.hasNext()) {
          add(index, i.next());
          changed = true;
          if (index >= 0)
             index++;
      }
      return changed;
   }

   public Object set(int index, Object e) {
      Object old = get(index);
      if (index < 0 || index >= size())
         add(index, e);
      else {
         if (type != null)
            e = Converter.convert(e, type);
         super.set(index, e);
      }
      return old;
   }

   public Class getCommonType() {
      Class t = null;
      Iterator i = iterator();
      while (i.hasNext()) {
         Object e = i.next();
         if (e == null)
            continue;
         else if (t == null)
            t = e.getClass();
         else {
            t = Data.commonType(t, e.getClass());
            if (t == null || t == Object.class)
               return null;
         }
      }
      return t;
   }

   public String toString() {
      return super.toString();
   }
}
