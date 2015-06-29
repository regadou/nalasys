package org.regadou.nalasys;

import java.io.*;
import java.net.*;

public class Folder extends File {

   public Folder() {
      this(new File(".").getAbsoluteFile().toURI());
   }

   public Folder(String f) {
      this(new File(f).toURI());
   }

   public Folder(File f) {
      this(f.toURI());
   }

   public Folder(URI u) {
      super(u);
      if (!exists())
         mkdirs();
      else if (!isDirectory())
         throw new RuntimeException(getPath()+" is not a directory");
   }

   public File getFile(String name) {
      if (name == null)
         return null;
      File[] files = listFiles();
      for (File file : files) {
         if (file.getName().equals(name))
            return file.isDirectory() ? new Folder(file) : file;
      }
      return null;
   }

   public void setFile(String name, Object data) {
      if (name == null)
         return;
      String path = getPath();
      if (!path.endsWith(separator))
         path += separator;
      path += name;
      File file = new File(path);
      if (data == null) {
         if (file.exists())
            file.delete();
      }
      else if (!file.exists()) {
         if (data instanceof File) {
            File newfile = (File)data;
            if (newfile.isDirectory())
               newfile.mkdirs();
            else {
               try { newfile.createNewFile(); }
               catch (Exception e) { Context.exception(e, "Folder.setFile("+name+","+data+")"); }
            }
         }
         else {
            Stream s = new Stream(file);
            s.write(data);
            s.close();
         }
      }
      else {
         Stream s = new Stream(file);
         s.write(data);
         s.close();
      }
   }
}
