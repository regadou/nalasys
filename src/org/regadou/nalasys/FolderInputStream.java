package org.regadou.nalasys;

import java.io.*;
import java.util.*;

public class FolderInputStream extends ByteArrayInputStream {

   private File file;
   private String mimetype;
   private int levels = 1;
   private Comparator<File> comparator = new Comparator<File>() {
       public int compare(File f1, File f2) {
           if (f1 == null)
               return (f2 == null) ? 0 : -1;
           else if (f2 == null)
               return 1;
           try {
               return f1.getCanonicalPath().compareToIgnoreCase(f2.getCanonicalPath());
           }
           catch (Exception e) {
               return f1.toString().compareToIgnoreCase(f2.toString());
           }
       }
       public boolean equals(Object obj) {
           return this == obj;
       }
   };

   public FolderInputStream() {
      this(new File("."), null);
   }

   public FolderInputStream(String path) {
      this(new File(path), null);
   }

   public FolderInputStream(String path, String mimetype) {
      this(new File(path), mimetype);
   }

   public FolderInputStream(File file) {
      this(file, null);
   }

   public FolderInputStream(File file, String mimetype) {
      super(new byte[0]);
      if (file == null)
         file = new File(".");
      else if (!file.isAbsolute()) {
         try { file = file.getCanonicalFile(); }
         catch (Exception e) { throw new RuntimeException(e); }
      }

      if (!file.isDirectory())
         throw new RuntimeException(file+" is not a folder");
      this.file = file;
      setMimetype(mimetype);
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() { return file.toString(); }

   public boolean equals(Object obj) {
      return (obj == null) ? false : getName().equals(obj.toString());
   }

   public String getName() { return file.toString(); }

   public String getMimetype() {
      return mimetype;
   }

   public final void setMimetype(String m) {
      mimetype = (m == null) ? Mimetype.defaultMimetype : m.trim();
      resetBuffer();
   }

   public int getLevels() {
      return levels;
   }

   public void setLevels(int levels) {
      this.levels = levels;
      resetBuffer();
   }

   public Comparator<File> getComparator() {
      return comparator;
   }

   public void setComparator(Comparator<File> comparator) {
      this.comparator = comparator;
      resetBuffer();
   }

   public Map getFileInfo() {
      return getFileInfo(file, levels);
   }

   private Map getFileInfo(File f, int level) {
      Map map = new LinkedHashMap();
      map.put("name", f.getName());
      map.put("parent", f.getParent());
      map.put("date", new Date(f.lastModified()));
      map.put("length", f.length());
      map.put("type", Mimetype.getMimetype(f).toString());
      if (f.isDirectory() && level > 0) {
         level--;
         List files = new ArrayList();
         map.put("files", files);
         List<File> src = Arrays.asList(f.listFiles());
         Set<File> set;
         if (comparator == null)
            set = new LinkedHashSet<File>(src);
         else {
            set = new TreeSet<File>(comparator);
            set.addAll(src);
         }
         for (File c : set)
            files.add(getFileInfo(c, level));
      }
      return map;
   }

   private void resetBuffer() {
      Map info = getFileInfo();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      new Stream(bytes, file.toString(), mimetype).write(info);
      buf = bytes.toByteArray();
      pos = mark = 0;
      count = buf.length;
   }
}
