package org.regadou.nalasys;

import java.util.*;
import java.lang.reflect.Method;

public class Entity implements Map<String,Object> {

   private List<Property<String,Object>> properties = new ArrayList<Property<String,Object>>();
   private Object source;
   private boolean inited;

   public Entity() { this(null); }

   public Entity(Object source) {
      this.source = source;
      buildEntries();
      this.inited = true;
   }

   public String toString() {
      return String.valueOf(source);
   }

   public Object getSource() { return source; }

   public Object get(Object key) {
      Entry<String,Object> p = getProperty(key);
      return (p == null) ? null : p.getValue();
   }

   public Object put(String key, Object value) {
      Object old = null;
      Property p = getProperty(key);
      if (p != null) {
         old = p.getValue();
         p.setValue(value);
      }
      return old;
   }

   public void putAll(Map map) {
      if (map != null) {
         for (Object key : map.keySet()) {
            Property p = getProperty(key);
            if (p != null)
               p.setValue(map.get(key));
         }
      }
   }

    public boolean containsKey(Object key) {
       return getProperty(key) != null;
    }

    public boolean containsValue(Object value) {
       for (Property p : properties) {
          Object pv = p.getValue();
          if (value == pv || (pv != null && pv.equals(value)))
             return true;
       }
       return false;
    }

    public Set<Entry<String,Object>> entrySet() {
        return new LinkedHashSet<Entry<String,Object>>(properties);
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    public Set<String> keySet() {
       Set<String> keySet = new LinkedHashSet<String>();
       for (Property<String,Object> p : properties)
          keySet.add(p.getKey());
       return keySet;
    }

    public int size() {
       return properties.size();
    }

   public Collection values() {
     List values = new ArrayList();
     for (Entry<String,Object> p : properties)
        values.add(p.getValue());
     return values;
   }

   public void clear() {
      throw new UnsupportedOperationException("Cannot modify the property list of an Entity");
   }

   public Object remove(Object key) {
      throw new UnsupportedOperationException("Cannot modify the property list of an Entity");
   }

   public boolean equals(Object that) {
      return (that instanceof Entity && source.equals(((Entity)that).source)) || source.equals(that);
   }

   public Property getProperty(Object key) {
      for (Property p : properties) {
         if (key != null) {
            if (key.equals(p.getKey()))
               return p;
         }
         else if (p.getKey() == null)
            return p;
      }
      return null;
   }

   public void setProperty(Object e) {
      if (inited)
         throw new UnsupportedOperationException("Cannot modify the property list of an Entity");
      if (e == null)
         return;
      else if (e instanceof char[])
         e = new String((char[])e);
      else if (e instanceof byte[])
         e = new String((byte[])e);

      Object[] a;
      if (e instanceof Collection)
         a = ((Collection)e).toArray();
      else if (e.getClass().isArray())
         a = Converter.toArray(e);
      else if (e instanceof Property) {
         Property p = (Property)e;
         Object k = p.getKey();
         for (Property old : properties) {
            if ((k != null && k.equals(old.getKey())) || old.getKey() == k) {
               properties.remove(old);
               break;
            }
         }

         properties.add(p);
         return;
      }
      else if (e instanceof Entry)
         a = new Object[]{((Entry)e).getKey(), ((Entry)e).getValue()};
      else if (e instanceof CharSequence) {
         String txt = e.toString().trim();
         if (txt.equals(""))
            return;
         int eq = txt.indexOf('=');
         int dp = txt.indexOf(':');
         if (dp >= 0 && (eq < 0 || dp < eq))
               eq = dp;

         if (eq < 0)
            a = new Object[]{txt.trim(), true};
         else
            a = new Object[]{txt.substring(0, eq).trim(), txt.substring(eq+1).trim()};
      }
      else {
         Map m = (e instanceof Map) ? (Map)e : new Entity(e);
         Object name = m.get("name");
         a = (name == null) ? new Object[]{e} : new Object[]{name, e};
      }

      String key;
      Object value;
      switch (a.length) {
         case 0:
            return;
         case 1:
            key = "";
            value = a[0];
            break;
         default:
            key = (a[0] == null) ? "" : a[0].toString().trim();
            value = a[1];
      }

      if (key.equals("")) {
         if (value == null)
            return;
         else if (value instanceof CharSequence)
            key = "name";
         else if (value instanceof Number)
            key = "number";
         else {
            String[] parts = value.getClass().getName().split("\\.");
            key = parts[parts.length-1];
         }
      }
      put(key, value);
   }

   private void buildEntries() {
      Class type = source.getClass();
      Map<String,Method[]> props = Property.getClassProperties(type);
      for (Entry<String,Method[]> entry : props.entrySet()) {
         Method[] m = entry.getValue();
         setProperty(new Property(source, entry.getKey(), null, m[0], m[1]));
      }
   }
}
