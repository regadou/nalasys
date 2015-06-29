package org.regadou.nalasys;

import java.io.*;
import java.util.*;

public class Matrix implements Serializable {

   private Map[] dimensions;
   private int[] sizes;
   private Data[] data;
   private Object value;
   private Class type;

// constructeur principal
   public Matrix(Object[] dimensions, Object data) {
      if (dimensions == null)
         dimensions = new Map[0];
      this.dimensions = new Map[dimensions.length];
      this.sizes = new int[dimensions.length];
      this.data = new Data[initDimensions(dimensions)];
      setData(Converter.toCollection(data).toArray());
   }

   public void setData(Object[] data) {
      if (data == null || data.length == 0) {
         for (int d = 0; d < this.data.length; d++) {
            this.data[d] = null;
         }
      }
      else if (data.getClass().getComponentType().isArray()) {
         // what if each element is also an array ?

      }
      else {
         int n = Math.min(data.length, this.data.length);
         for (int d = 0; d < n; d++) {
            setValue(getPositions(d), data[d]);
         }
      }
   }

   public int getDimensions() {
      return dimensions.length;
   }

   public Map getDimension(int d) {
      return (d >= dimensions.length || d < 0) ? null : dimensions[d];
   }

   public int getSize() {
   // could be the sum of all values
      return data.length;
   }

   public int getSize(int s) {
      return (s >= sizes.length || s < 0) ? 0 : sizes[s];
   }

   public Class getType() {
      return type;
   }

   public Object getValue(int[] positions) {
      return getValue(getIndex(positions));
   }

   public Object getValue(int index) {
      return (index < 0 || index >= this.data.length) ? null : this.data[index];
   }

   public void setValue(int[] positions, Object value) {
      setValue(getIndex(positions), value);
   }

   public void setValue(int index, Object value) {
      setValue(true, index, getValue(index), value);
   }

   public boolean addValue(int[] positions, Object value) {
      return addValue(getIndex(positions), value);
   }

   public boolean addValue(int index, Object value) {
      setValue(false, index, getValue(index), value);
      return true;
   }

   public boolean isEmpty(){
      return dimensions == null || dimensions.length == 0 ||
            (dimensions.length == 1 && Converter.toData(dimensions[0]).isEmpty());
   }

   public Number toNumber() {
    // c'est quoi l'équation de la valeur quantitative d'une matrice
      return null;
   }

   public Object[] toArray() {
      // we should split data array by number of dimensions
      // also check if data is null while there are several dimensions
      return data;
   }

// useful private methods
   private int initDimensions(Object[] dimensions) {
      int total = 1;
      for (int d = 0; d < dimensions.length; d++) {
         setDimension(d, dimensions[d]);
         total *= this.sizes[d];
      }
      return total;
   }

   private void setDimension(int d, Object value) {
      if (value == null) {
         this.dimensions[d] = null;
         this.sizes[d] = 1;
      }
      else if (value instanceof Number) {
         this.dimensions[d] = null;
         this.sizes[d] = Math.max(1, ((Number)value).intValue());
      }
      else if (value instanceof Data)
         setDimension(d, ((Data)value).getValue());
      else {
         this.dimensions[d] = Converter.toMap(value); // transforme l'objet en map de clés
         this.sizes[d] = Math.max(1, this.dimensions[d].size());  // chaque mapping est le nom de la clé avec sa valeur
      }
   }

   private int getIndex(int[] positions) {
      return -1;
   }

   private int[] getPositions(int index) {
      return null;
   }

   private Object getValue() {
      return value;
   }

   private void setValue(Object src) {
      value = src;
   }

   private void setValue(boolean clear, int index, Object src, Object dst) {
   }


// other constructors

   public Matrix() { this(new Object[0], null); }

   public Matrix(Map dimension) { this(new Object[]{dimension}, null); }

   public Matrix(Class c) { this(new Object[0], null); type = c; }

   public Matrix(Expression e) { this(new Object[0], null); value = e; }

   public Matrix(Date d) { this(new Object[0], null); value = d; }

   public Matrix(Object[] data) { this(new Object[]{null}, data); }

   public Matrix(Object[][] data) { this(new Object[]{null, null}, data); }

   public Matrix(Object[][][] data) { this(new Object[]{null, null, null}, data); }

   public Matrix(Object[][][][] data) { this(new Object[]{null, null, null, null}, data); }

   public Matrix(Object[][][][][] data) { this(new Object[]{null, null, null, null, null}, data); }

   public Matrix(int x) { this(new Object[]{new Integer(x)}, null); }

   public Matrix(int x, Object data) { this(new Object[]{new Integer(x)}, data); }

   public Matrix(int x, int y) { this(new Object[]{new Integer(x), new Integer(y)}, null); }

   public Matrix(int x, int y, Object data) { this(new Object[]{new Integer(x), new Integer(y)}, data); }

   public Matrix(int x, int y, int z) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z)}, null); }

   public Matrix(int x, int y, int z, Object data) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z)}, data); }

   public Matrix(int x, int y, int z, int w) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z), new Integer(w)}, null); }

   public Matrix(int x, int y, int z, int w, Object data) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z), new Integer(w)}, data); }

   public Matrix(int x, int y, int z, int a, int b) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z), new Integer(a), new Integer(b)}, null); }

   public Matrix(int x, int y, int z, int a, int b, Object data) { this(new Object[]{new Integer(x), new Integer(y), new Integer(z), new Integer(a), new Integer(b)}, data); }

   public String toString() {
      if (value != null)
         return value.toString();
      else
         return Converter.toString(toArray());
   }

   public boolean equals(Object obj) {
      return new HashSet(Arrays.asList(toArray())).equals(new HashSet(Converter.toCollection(obj)));
   }
}

