package org.regadou.nalasys;

import java.util.*;

public final class Complex extends Number {
   public static boolean alwaysComputeComplex = false;
   public static final int NO_ANGLE=0, RAD_ANGLE=1, DEG_ANGLE=2;

	private double real, imag;

   public Complex() { this(0, 0); }

   public Complex(String val) throws NumberFormatException {
     if (val == null) {
        real = imag = 0;
        return;
     }

     val = val.toLowerCase();
     int i = val.indexOf("i");
     if (i < 0) {
	     real = Double.parseDouble(val);
	     imag = 0;
        return;
     }
     else if (i + 1 == val.length())
        val += '1';

     if (i == 0) {
	     real = 0;
	     imag = Double.parseDouble(val.substring(1));
     }
     else {
	     real = (i == 1) ? 0 : Double.parseDouble(val.substring(0, i-1));
	     imag = Double.parseDouble(val.substring(i+1));
	     char sign = val.charAt(i-1);
	     switch (sign) {
           case '+':
              break;
           case '-':
              imag *= -1;
              break;
           default:
              throw new NumberFormatException("Unknown sign "+sign+" before imaginary part in "+val);
	     }
     }
   }

   public Complex(Number n) {
      real = (n == null) ? 0 : n.doubleValue();
      imag = (n instanceof Complex) ? ((Complex)n).imaginaryValue() : 0;
   }

   public Complex(double r, double i) { this(r, i, NO_ANGLE); }

   public Complex(double r, double a, int u) {
      switch (u)
      {
      case RAD_ANGLE:
         real = r * Math.cos(a);
         imag = r * Math.sin(a);
         break;
      case DEG_ANGLE:
         real = r * Math.cos(a*Math.PI/180);
         imag = r * Math.sin(a*Math.PI/180);
         break;
      default:
    		real = r;
    		imag = a;
      }
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
     String txt = "";
     if (imag != 0)
     {
	     if (imag > 0)
		     txt = "+i"+imag;
	     else
	     {
		     txt = "-i"+(-imag);
	     }
     }
     return real+txt;
   }

   public String toString(int decimals) {
     String txt = "";
     if (imag != 0)
     {
	     if (imag > 0)
		     txt = "+i"+decimals(imag, 2);
	     else
		     txt = "-i"+decimals(-imag, 2);
     }
     return decimals(real, 2)+txt;
   }

   public byte byteValue() { return (byte)real; }

   public double doubleValue() { return real; }

   public float floatValue() { return (float)real; }

   public int intValue() { return (int)real; }

   public long longValue() { return (long)real; }

   public short shortValue() { return (short)real; }

   public double imaginaryValue() { return imag; }

   public Double[] toArray() { return new Double[]{new Double(real), new Double(imag)}; }

   public Map<String,Double> toMap() {
      Map<String,Double> map = new LinkedHashMap<String,Double>();
      map.put("r", real);
      map.put("i", imag);
      return map;
   }

   public boolean isEmpty() {
      return real == 0 && imag == 0;
   }

   public double length() { return Math.sqrt(real*real + imag*imag); }

   public double degrees() { return Math.atan2(imag,real)*180/Math.PI; }

   public double radians() { return Math.atan2(imag,real); }

   public static Number add(Number n1, Number n2) {
      if (n1 == null)
         n1 = new Probability();
      if (n2 == null)
         n2 = new Probability();
      if ((n1 instanceof Probability) && (n2 instanceof Probability))
         return ((Probability)n1).or((Probability)n2);
      return number(realpart(n1) + realpart(n2), imagpart(n1) + imagpart(n2));
   }

   public static Number substract(Number n1, Number n2) {
      if (n1 == null)
         n1 = new Probability();
      if (n2 == null)
         n2 = new Probability();
      if ((n1 instanceof Probability) && (n2 instanceof Probability))
         return ((Probability)n1).or(((Probability)n2).not());
      return number(realpart(n1) - realpart(n2), imagpart(n1) - imagpart(n2));
   }

   public static Number multiply(Number n1, Number n2) {
      double r1 = realpart(n1);
      double r2 = realpart(n2);
      double i1 = imagpart(n1);
      double i2 = imagpart(n2);
      return number(r1*r2 - i1*i2, r1*i2 + r2*i1);
   }

   public static Number divide(Number n1, Number n2) {
      double r1 = realpart(n1);
      double r2 = realpart(n2);
      double i1 = imagpart(n1);
      double i2 = imagpart(n2);

      double r = r1*r2 + i1*i2;
      double i = r1*i2 + r2*i1;
      double d = r2*r2 + i2*i2;
      if (d == 0)
         return number((r < 0) ? Double.NEGATIVE_INFINITY : ((r > 0) ? Double.POSITIVE_INFINITY : 0),
                       (i < 0) ? Double.NEGATIVE_INFINITY : ((i > 0) ? Double.POSITIVE_INFINITY : 0));
      else
         return number(r/d, i/d);
   }

   public static Number ratio(Number n1, Number n2) {
      // TODO: calcul douteux si un des nombres est complexe
      double r = Math.IEEEremainder(realpart(n1), realpart(n2));
      double i = (n1 instanceof Complex || n2 instanceof Complex)
               ? Math.IEEEremainder(imagpart(n1), imagpart(n2)) : 0;
      return number(r, i);
   }

   public static Number exponant(Number n1, Number n2) {
      double r1 = realpart(n1);
      double r2 = realpart(n2);
      double i1 = imagpart(n1);
      double i2 = imagpart(n2);
      if (i1 == 0 && i2 == 0)
         return number(Math.pow(r1, r2), 0);
      else {
         // TODO: calcul douteux si un des nombres est complexe
         return number(Math.pow(r1, r2),
                       Math.pow(i1, i2));
      }
   }

   public static Number logarithm(Number n1, Number n2) {
      double r1 = realpart(n1);
      double r2 = realpart(n2);
      double i1 = imagpart(n1);
      double i2 = imagpart(n2);
      if (i1 == 0 && i2 == 0)
         return number(Math.log(r1) / Math.log(r2), 0);
      else {
         // TODO: calcul douteux si un des nombres est complexe
         return number(Math.log(r1) / Math.log(r2),
                       Math.log(i1) / Math.log(i2));
      }
   }

   public static Number sqrt(Number n) {
      if (n == null)
         return 0;
      else if (n instanceof Complex) {
         double x = ((Complex)n).doubleValue();
         double y = ((Complex)n).imaginaryValue();
         double r = Math.sqrt(x * x + y * y);
         double ang = Math.atan2(y, x) / 2;
         return new Complex(r * Math.cos(ang), r * Math.sin(ang));
      }
      else if (n.doubleValue() < 0) {
         return new Complex(0, Math.sqrt(0 - n.doubleValue()));
      }
      else
         return Math.sqrt(n.doubleValue());
   }

   public static double realpart(Number n) {
      return (n == null) ? 0 : n.doubleValue();
   }

   public static double imagpart(Number n) {
      return (n == null) ? 0 : ((n instanceof Complex) ? ((Complex)n).imaginaryValue() : 0);
   }

   public static Number number(double r, double i) {
      if (alwaysComputeComplex)
         return new Complex(r, i);
      else if (i == 0) {
         if (r == (long)r)
            return new Long((long)r);
         else
            return new Double(r);
      }
      else
         return new Complex(r, i);
   }

   public static String decimals(double n, int d) {
      if (n == Double.POSITIVE_INFINITY)
         return "INF";
      else if (n == Double.NEGATIVE_INFINITY)
         return "-INF";
      else if (Double.isNaN(n))
         return "NaN";
      String[] parts = String.valueOf(n).split("\\.");
      StringBuilder txt = new StringBuilder(parts[0]);
      if (d > 0)
         txt.append(".");
      char[] chars = (parts.length < 2) ? new char[0] : parts[1].toCharArray();
      for (int i = 0; i < d; i++) {
          txt.append((i >= chars.length) ? '0' : chars[i]);
      }
      int p = new String(chars).toLowerCase().indexOf('e');
      if (p > 0)
         txt.append(new String(chars, p, chars.length-p));
      return txt.toString();
   }
}


