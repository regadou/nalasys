package org.regadou.nalasys;

import java.io.*;
import java.net.*;

public class Stream {

   public static enum Status { CLOSE, READ, WRITE, READ_WRITE };
   public static final int defaultBufferSize = 1024;

   private Object src;
   private String mimetype;
   private InputStream inputStream;
   private OutputStream outputStream;

   public Stream(String src) {
      this(src, null);
   }

   public Stream(String src, String mimetype) {
      src = (src == null) ? "" : src.trim();
      try {
         this.src = new URL(src);
      }
      catch (Exception e) {
         File f = new File(src);
         try { f = f.getCanonicalFile(); }
         catch (Exception e2) { Context.exception(e, "new Stream("+src+","+mimetype+")"); }
         this.src = f;
      }
      setMimetype(mimetype);
   }

   public Stream(File f) {
      this(f, null);
   }

   public Stream(File f, String mimetype) {
      if (f == null)
         f = new File("");
      try { f = f.getCanonicalFile(); }
      catch (Exception e) { Context.exception(e, "new Stream("+f+","+mimetype+")"); }
      src = f;
      setMimetype(mimetype);
   }

   public Stream(URL u) {
      this(u, null);
   }

   public Stream(URL u, String mimetype) {
      if (u == null)
         Context.exception(new RuntimeException("url for StreamInput is null"), "new Stream("+u+","+mimetype+")");
      else
         src = u;
      setMimetype(mimetype);
   }

   public Stream(URI u) {
      this(u, null);
   }

   public Stream(URI u, String mimetype) {
      this((u == null) ? "" : u.toString(), mimetype);
      setMimetype(mimetype);
   }

   public Stream(InputStream i, String name) {
      this(i, null, name, null);
   }

   public Stream(InputStream i, String name, String mimetype) {
      this(i, null, name, mimetype);
   }

   public Stream(OutputStream o, String mimetype) {
      this(null, o, null, mimetype);
   }

   public Stream(OutputStream o, String name, String mimetype) {
      this(null, o, name, mimetype);
   }

   public Stream(InputStream i, OutputStream o, String name, String mimetype) {
      inputStream = i;
      outputStream = o;
      if (name == null || name.trim().equals("")) {
         if (i == null) {
            if (o == null)
               throw new RuntimeException("Both input and output stream are null");
            else
               src = o.toString();
         }
         else if (o == null)
            src = i.toString();
         else
            src = i + " -> " + o;
      }
      else
         src = name;
      setMimetype(mimetype);
   }

   public Stream(Reader reader, Writer writer) {
      this(readerToInputStream(reader), writerToOutputStream(writer), null, null);
   }

   public Stream(Reader reader, Writer writer, String name) {
      this(readerToInputStream(reader), writerToOutputStream(writer), name, null);
   }

   public Stream(Reader reader, Writer writer, String name, String mimetype) {
      this(readerToInputStream(reader), writerToOutputStream(writer), name, mimetype);
   }

   public Stream(byte[] bytes) {
      this(bytes, null, null);
   }

   public Stream(byte[] bytes, String name) {
      this(bytes, name, null);
   }

   public Stream(byte[] bytes, String name, String mimetype) {
      inputStream = new ByteArrayInputStream(bytes);
      outputStream = new ByteArrayOutputStream();
      src = name;
      if (name != null && mimetype == null)
      setMimetype(mimetype);
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
      return getName();
   }

   public boolean open(Status status) {
      URLConnection connection = null;
      if (status == null || status == Status.CLOSE) {
         try {
            URL u;
            if (src instanceof URL)
               u = (URL)src;
            else if (src instanceof File)
               u = ((File)src).toURI().toURL();
            else {
               Context.exception(new RuntimeException("Cannot open "+src), "Stream.open("+status+")");
               return false;
            }
            connection = u.openConnection();
            mimetype = connection.getContentType();
            return true;
         }
         catch (Exception e) {
            Context.exception(new RuntimeException("url for StreamInput is null"), "Stream.open("+status+")");
            return false;
         }
      }
      if ((status == Status.READ || status == Status.READ_WRITE)
                                 && inputStream == null) {
         try {
               if (src instanceof URL) {
                  connection = ((URL)src).openConnection();
                  mimetype = connection.getContentType();
                  inputStream = connection.getInputStream();
               }
               else if (src instanceof File) {
                  File f = (File)src;
                  if (f.isDirectory())
                     inputStream = new FolderInputStream(f, mimetype);
                  else
                     inputStream = new FileInputStream(f);
               }
               else
                  Context.exception(new RuntimeException("Cannot open "+src+" for reading"), "Stream.open("+status+")");
         }
         catch (Exception e) {  Context.exception(e, "Stream.open("+status+")"); }
      }
      if ((status == Status.WRITE || status == Status.READ_WRITE)
                                 && outputStream == null) {
         try {
               if (src instanceof URL) {
                  if (connection == null) {
                     connection = ((URL)src).openConnection();
                     mimetype = connection.getContentType();
                  }
                  outputStream = connection.getOutputStream();
               }
               else if (src instanceof File) {
                  File f = (File)src;
                  if (f.isDirectory())
                     Context.exception(new RuntimeException("Writing directories is not supported"), "Stream.open("+status+")");
                  else
                     outputStream = new FileOutputStream(f);
               }
               else
                  Context.exception(new RuntimeException("Cannot open "+src+" for writing"), "Stream.open("+status+")");
         }
         catch (Exception e) { Context.exception(e, "Stream.open("+status+")"); }
      }
      return isOpen();
   }

   public boolean close() {
      Object error = null;
      if (inputStream != null) {
         try {
            inputStream.close();
            inputStream = null;
         }
         catch (Exception e) {}
      }
      if (outputStream != null) {
         try {
            outputStream.close();
            outputStream = null;
            return true;
         }
         catch (Exception e) {}
      }
      return error != null;
   }

   public boolean isOpen() {
      return inputStream != null || outputStream != null;
   }

   public String getName() {
      return src.toString();
   }

   public String getMimetype() {
      return mimetype;
   }

   public void setMimetype(String mimetype) {
      if (mimetype != null && mimetype.indexOf('/') > 0)
         this.mimetype = mimetype.trim();
      else {
         if (src instanceof URL)
            open(Status.CLOSE);
         else if (src instanceof File)
            this.mimetype = Mimetype.getMimetype((File)src).toString();
         else
            this.mimetype = Mimetype.defaultMimetype;
      }
   }

   public <T extends Object> T read(Class<T> type) {
      if (!open(Status.READ))
         return null;
      try {
         Mimetype m = Mimetype.getMimetype(this);
         if (type == null || type == Object.class)
            return (T)m.load(inputStream, mimetype);
         else if (CharSequence.class.isAssignableFrom(type))
            return (T)new String(getBytes(inputStream));
         else if (type == byte[].class)
            return (T)getBytes(inputStream);
         else if (type == char[].class)
            return (T)new String(getBytes(inputStream)).toCharArray();
         else
            return Converter.convert(m.load(inputStream, mimetype), type);
      }
      catch (Exception e) { return Converter.convert(Context.exception(e, "Stream.read("+type+")"), type); }
   }

   public boolean write(Object obj) {
      try {
         if (!open(Status.WRITE))
            return false;
         Mimetype.getMimetype(this).save(outputStream, obj, mimetype);
         return true;
      }
      catch (Exception e) {
         Context.exception(e, "Stream.write("+obj+")");
         return false;
      }
   }

   public boolean copyTo(Stream dst) throws IOException {
      return copy(this, dst);
   }

   public boolean copyFrom(Stream src) throws IOException {
      return copy(src, this);
   }

   public Status getStatus() {
      if (inputStream != null)
         return (outputStream == null) ? Status.READ : Status.READ_WRITE;
      else if (outputStream != null)
         return Status.WRITE;
      else
         return Status.CLOSE;
   }

   public InputStream getInputStream() {
      open(Status.READ);
      return inputStream;
   }

   public OutputStream getOutputStream() {
      open(Status.WRITE);
      return outputStream;
   }

   public void setInputStream(InputStream inputStream) {
      this.inputStream = inputStream;
   }

   public void setOutputStream(OutputStream outputStream) {
      this.outputStream = outputStream;
   }

   protected void finalize() { close(); }

   public static boolean copy(Stream src, Stream dst) throws IOException {
      if (src == null || dst == null)
         throw new IOException("one of source or target stream is null");
      String stype = src.getMimetype();
      if (stype == null)
         stype = Mimetype.defaultMimetype;
      String dtype = dst.getMimetype();
      if (dtype == null)
         dtype = Mimetype.defaultMimetype;
      Class type = stype.equalsIgnoreCase(dtype) ? byte[].class : null;
      return dst.write(src.read(byte[].class));
   }

   public static int copy(InputStream input, OutputStream output) throws IOException {
      if (input == null || output == null)
         return 0;
      byte[] bytes = new byte[defaultBufferSize];
      int done = 0;
      for (int got = 0; got >= 0;) {
         got = input.read(bytes);
         if (got > 0) {
            output.write(bytes, 0, got);
            done += got;
         }
      }
      return done;
   }

   public static byte[] getBytes(InputStream input) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      copy(input, buffer);
      buffer.flush();
      return buffer.toByteArray();
   }

   public static InputStream readerToInputStream(Reader reader) {
      return null;
   }

   public static OutputStream writerToOutputStream(Writer writer) {
      return null;
   }
}

