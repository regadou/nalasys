package org.regadou.nalasys;

import java.util.*;
import java.awt.Image;

public class Perception {

   private Object data = null;
   private byte[] type = null;

   public Perception() {
      this(null);
   }

   public Perception(Object src) {
      data = src;
      type = Data.concept(src);
   }

   public Object getData() {
      return data;
   }

   public void setData(Object data) {
      this.data = data;
      this.type = Data.concept(data);
   }

   public byte[] getType() {
      return type;
   }

   public void setType(byte[] type) {
      this.type = type;
   }
}
