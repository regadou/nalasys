package org.regadou.nalasys;


public class Probability extends Number {

   public static final float MIN_VALUE = 0;
   public static final float MAX_VALUE = 1;
   public static float MIN_TRUE = 0.5f;
   public static final Probability TRUE = new Probability(true);
   public static final Probability FALSE = new Probability(false);
   private float value = 0;
   private Float positive;

   public Probability() {
   	this(false);
   }

   public Probability(boolean v) {
		value = v ? 1 : 0;
	}

   public Probability(Boolean v) {
		value = (v == null) ? 0 : (v.booleanValue() ? 1 : 0);
	}

   public Probability(Number n) {
		value = (n == null) ? 0 : n.floatValue();
		if (value < 0)
			value = 0;
		else if (value > 1)
			value = 1;
	}

   public Probability(String v) throws NumberFormatException {
      if (v == null)
         value = 0;
      else {
         v = v.trim();
         float pct = 1;
         if (v.endsWith("%")) {
            pct = 100;
            v = v.substring(0, v.length()-1);
         }
         value = Float.parseFloat(v) / pct;
         if (value < 0)
         	value = 0;
         else if (value > 1)
            value = 1;
      }
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
		return Math.round(value*10000)/100+"%";
	}

   public boolean equals(Object o) {
      float that = Converter.toNumber(o, 0).floatValue();
      return that == this.value;
   }

   public float getPositive() {
      if (positive == null)
         positive = MIN_TRUE;
      return positive;
   }

   public void setPositive(float v) {
      positive = v;
   }

   public byte byteValue() { return (value >= MIN_TRUE) ? (byte)1 : (byte)0; }

   public int intValue() { return (value >= MIN_TRUE) ? 1 : 0; }

   public long longValue() { return (value >= MIN_TRUE) ? 1L : 0L; }

   public short shortValue() { return (value >= MIN_TRUE) ? (short)1 : (short)0; }

   public boolean booleanValue() { return value >= getPositive(); }

   public double doubleValue() { return (double)value; }

   public float floatValue() { return value; }

   public Probability not() { return new Probability(1 - value); }

   public Probability and(Probability p) {
      float pv = (p == null) ? 0 : p.value;
      return new Probability(this.value * pv);
   }

   public Probability or(Probability p) {
      float pv = (p == null) ? 0 : p.value;
      return new Probability(this.value + pv - this.value * pv);
   }
}


