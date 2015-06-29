package org.regadou.nalasys;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.net.*;
import java.awt.image.RenderedImage;
import javax.imageio.ImageIO;
import javax.sound.midi.*;
import javax.swing.text.html.*;
import org.codehaus.jackson.map.*;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

public class Mimetype {

   private static interface Processor {
      public Object load(String mimetype, InputStream input);
      public boolean save(String mimetype, OutputStream output, Object obj);
   }

   private Class[] classes;
   private String[] mimes;
   private String[] extensions;
   private Processor processor;

   private Mimetype() {}

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
      return mimes[0];
   }

   public Object load(InputStream input) {
      return load(input, null);
   }

   public Object load(InputStream input, String mimetype) {
      if (input == null)
         return new byte[0];
      if (mimetype == null)
         mimetype = mimes[0];
      if (processor != null)
         return processor.load(mimetype, input);
      byte[] bytes;
      try { bytes = Stream.getBytes(input); }
      catch (Exception e) { bytes = e.toString().getBytes(); }
      if (mimetype.startsWith("text/"))
         return new String(bytes);
      else
         return bytes;
   }

   public boolean save(OutputStream output, Object obj) {
      return save(output, obj, null);
   }

   public boolean save(OutputStream output, Object obj, String mimetype) {
      if (output == null)
         return false;
      if (mimetype == null)
         mimetype = mimes[0];
      if (processor != null)
         return processor.save(mimetype, output, obj);
      try {
         byte[] bytes = (obj instanceof byte[]) ? (byte[])obj : Converter.toString(obj).getBytes();
         output.write(bytes);
         return true;
      }
      catch (Exception e) { return false; }
   }

   private static final Map<Class,Mimetype> classesMap = new LinkedHashMap<Class,Mimetype>();
   private static final Map<String,Mimetype> mimesMap = new LinkedHashMap<String,Mimetype>();
   private static final Map<String,Mimetype> extsMap = new LinkedHashMap<String,Mimetype>();

   public static final String defaultExtension = "txt";
   public static final List<String> majorTypes = Arrays.asList("text,image,audio,video,model,application,*".split(","));
   public static final String defaultMimetype = "text/plain";
   public static final String folderMimetype = "inode/directory";
   public static final String[] mimetypePrefixes = {
      "text/", "text/x-", "application/", "application/x-"
   };

   private static final ObjectMapper jsonMapper = new ObjectMapper();
   static {
      jsonMapper.setDateFormat(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
      jsonMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
   }

   public static ObjectMapper getJsonMapper() {
      return jsonMapper;
   }

   public static String getFullPath(String path) {
      path = (path == null) ? "" : path.trim();
      if (!path.startsWith(File.separator) && getDriveLetter(path) == 0) {
         String folder = Context.currentContext().getFolder().toString();
         if (!folder.endsWith(File.separator))
               folder += File.separator;
         path = folder + path;
      }
      return path;
   }

   public static String[] getMimetypeParts(String type) {
      String[] parts = null;
      if (type != null && !type.trim().equals("")) {
         parts = type.trim().toLowerCase().split("/");
         switch (parts.length) {
            case 1:
               if (majorTypes.contains(parts[0]))
                  parts = new String[]{parts[0], "*"};
               else
                  parts = new String[]{"*", parts[0].replace("x-", "")};
               break;
            default:
               parts = new String[]{parts[0], parts[1]};
            case 2:
               if (majorTypes.contains(parts[0]))
                  parts[1] = parts[1].replace("x-", "");
               else
                  parts = null;
         }
      }
      if (parts == null)
         parts = new String[]{"text", "*"};
      return parts;
   }

   public static Mimetype getMimetype(Stream s) {
      Mimetype m = mimesMap.get(s.getMimetype());
      if (m == null) {
         String src = s.getName();
         if (src != null) {
            int slash = src.lastIndexOf('/');
            int bslash = src.lastIndexOf('\\');
            if (slash < 0 || bslash > slash)
               slash = bslash;
            if (slash >= 0)
               src = src.substring(slash+1);
            int dot = src.lastIndexOf('.');
            if (dot > 0)
               m = extsMap.get(src.substring(dot+1).toLowerCase());
         }
      }
      return (m == null) ? mimesMap.get(defaultMimetype) : m;
   }

   public static Mimetype getMimetype(Class type) {
      if (type == null)
         return null;
      for (Map.Entry<Class,Mimetype> entry : classesMap.entrySet()) {
         if (entry.getKey().isAssignableFrom(type))
            return entry.getValue();
      }
      return null;
   }

   public static Mimetype getMimetype(String type) {
      return mimesMap.get(type);
   }

   public static Mimetype getMimetype(File file) {
      try { return getMimetype(file.toURI().toURL()); }
      catch (Exception e) { return mimesMap.get(defaultMimetype); }
   }

   public static Mimetype getMimetype(URI uri) {
      try { return getMimetype(uri.toURL()); }
      catch (Exception e) { return mimesMap.get(defaultMimetype); }
   }

   public static Mimetype getMimetype(URL url) {
      Mimetype mime = null;
      try {
         if (!url.getProtocol().equals("file")) {
            URLConnection c = url.openConnection();
            mime = mimesMap.get(c.getContentType());
            c.getInputStream().close();
         }
         else {
            File file = new File(url.getPath());
            if (file.isDirectory())
               mime = mimesMap.get(folderMimetype);
            else {
               String path = file.toString();
               int i = path.indexOf('?');
               if (i >= 0)
                  path = path.substring(0, i);
               i = path.lastIndexOf('/');
               if (i >= 0)
                  path = path.substring(i+1);
               i = path.lastIndexOf('\\');
               if (i >= 0)
                  path = path.substring(i+1);
               i = path.lastIndexOf('.');
               if (i >= 0)
                   mime = extsMap.get(path.substring(i+1).toLowerCase());
            }
         }
      }
      catch (Exception e) {}
      return (mime == null) ? mimesMap.get(defaultMimetype) : mime;
   }

   public static Mimetype getMimetypeFromExtension(String ext) {
      return extsMap.get(ext);
   }

   private static char getDriveLetter(String path) {
      if (path == null)
         return 0;
      else if (path.indexOf(':') == 1)
         return path.substring(0,1).toUpperCase().charAt(0);
      else
         return 0;
   }

   private static void add(Class[] classes, String[] mimes, String[] exts) {
      add(classes, mimes, exts, null);
   }

   private static void add(Class[] classes, String[] mimes, String[] exts, Processor proc) {
      Mimetype m = new Mimetype();
      m.classes = classes;
      m.mimes = mimes;
      m.extensions = exts;
      m.processor = proc;
      for (Class c : classes)
         classesMap.put(c, m);
      for (String t : mimes)
         mimesMap.put(t, m);
      for (String e : exts)
         extsMap.put(e, m);
   }

   static {
      add(new Class[]{CharSequence.class, char[].class},
            new String[]{"text/plain"},
            new String[]{"txt"}
      );
      add(new Class[]{byte[].class},
            "application/octet-stream,text/binary".split(","),
            "bin,so,exe,dll,class".split(",")
      );
      add(new Class[]{java.awt.Image.class},
            "image/png,image/jpeg,image/gif,image/svg+xml,image/x-ms-bmp,image/x-portable-pixmap,image/x-xpixmap".split(","),
            "png,jpg,jpeg,jpe,gif,bmp,ppm,xpm".split(","),
            new Processor() {
               public Object load(String mimetype, InputStream input) {
                  if (input == null)
                     return null;
                  try {
                     String[] parts = Mimetype.getMimetypeParts(mimetype);
                     if (parts[1].startsWith("svg"))
                        throw new RuntimeException("loading svg image object is not supported yet");
                     else
                        return ImageIO.read(input);
                  }
                  catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
               public boolean save(String mimetype, OutputStream output, Object obj) {
                  if (output == null || obj == null)
                     return false;
                  try {
                     String[] parts = Mimetype.getMimetypeParts(mimetype);
                     String format = parts[1].equals("") ? "png" : parts[1];
                     if (obj instanceof RenderedImage) {
                        ImageIO.write((RenderedImage)obj, format, output);
            //TODO: what to do with SVG data
                     }
                     else
                        throw new RuntimeException("Converting "+obj.getClass().getName()+" to "+mimetype+" is not supported yet");
                     return true;
                  }
                  catch (Exception e) { return false; }
               }
            }
      );
      add(new Class[]{javax.sound.midi.Sequence.class, javax.sound.sampled.AudioInputStream.class},
            "audio/mp3,audio/wav,audio/wma,audio/midi".split(","),
            "mp3,wav,wma,mid,midi".split(","),
            new Processor() {
               public Object load(String mimetype, InputStream input) {
                  if (input == null)
                     return null;
                  try {
                     String[] parts = Mimetype.getMimetypeParts(mimetype);
                     if (parts[1].startsWith("mid"))
                        return MidiSystem.getSequence(input);
                     else
                        throw new RuntimeException("loading "+mimetype+" audio object is not supported yet");
                  }
                  catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
               public boolean save(String mimetype, OutputStream output, Object obj) {
                  if (output == null || obj == null)
                     return false;
                  try {
                     String[] parts = Mimetype.getMimetypeParts(mimetype);
                     String format = parts[1].equals("") ? "mid" : parts[1];
                     if (obj instanceof Sequence && format.startsWith("mid"))
                        MidiSystem.write((Sequence)obj, 1, output);
                     else // what to do with wav|mp3 data
                        throw new RuntimeException("Converting "+obj.getClass().getName()+" to "+mimetype+" is not supported yet");
                     return true;
                  }
                  catch (Exception e) { return false; }
               }
            }
      );
      add(new Class[]{},
            "video/mp4,video/mpg,video/flv,video/wmv,video/avi,video/quicktime,application/x-shockwave-flash".split(","),
            "mp4,mpg,flv,wmv,avi,mov,swf".split(",")
      );
      add(new Class[]{},
            "model/x3d+xml,model/x3d+vrml,model/x3d+binary,model/vrml,model/3ds,model/dae,model/obj".split(","),
            "x3d,wrl,3ds,dae,obj".split(",")
      );
      add(new Class[]{javax.swing.text.html.HTMLDocument.class},
            "text/html,application/xhtml+xml".split(","),
            "html,htm,xhtml".split(","),
            new Processor() {
               public Object load(String mimetype, InputStream input) {
                  if (input == null)
                     return null;
                  try {
                     HTMLEditorKit kit = new HTMLEditorKit();
                     HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
                     doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
                     kit.read(new InputStreamReader(input), doc, 0);
                     return doc;
                  }
                  catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
               public boolean save(String mimetype, OutputStream output, Object obj) {
                  if (output == null)
                     return false;
                  try {
                     StringBuilder buffer = new StringBuilder("<html><body>\n");
                     getHtml(buffer, obj);
                     output.write(buffer.append("</body></html>\n").toString().getBytes());
                     return true;
                  }
                  catch (Exception e) { return false; }
               }
               private void getHtml(StringBuilder buffer, Object obj) {
                  if (obj == null)
                     ;
                  else if (obj instanceof char[])
                     buffer.append(new String((char[])obj));
                  else if (obj instanceof byte[])
                     buffer.append(new String((byte[])obj));
                  else if (obj.getClass().isArray()) {
                     buffer.append("<ul>\n");
                     int n = Array.getLength(obj);
                     for (int i = 0; i < n; i++) {
                        buffer.append("<li>\n");
                        getHtml(buffer, Array.get(obj, i));
                        buffer.append("</li>\n");
                     }
                     buffer.append("</ul>\n");
                  }
                  else if (obj instanceof Map.Entry) {
                     Map.Entry e = (Map.Entry)obj;
                     buffer.append(e.getKey()).append(" = ");
                     getHtml(buffer, e.getValue());
                  }
                  else if (obj instanceof Map)
                     getHtml(buffer, ((Map)obj).entrySet().toArray());
                  else if (obj instanceof Collection)
                     getHtml(buffer, ((Collection)obj).toArray());
                  else if (obj instanceof Class)
                     getHtml(buffer, ((Class)obj).getName());
                  else if (obj instanceof Database)
                     getHtml(buffer, ((Database)obj).getUrl());
                  else if (obj instanceof Data)
                     getHtml(buffer, ((Data)obj).getValue());
                  else if (Converter.isStringable(obj.getClass()))
                     buffer.append(obj.toString());
                  else
                     getHtml(buffer, new Entity(obj));
               }
            }
      );
      add(new Class[]{Document.class},
            new String[]{"application/xml", "text/xml"},
            new String[]{"xml"},
            new Processor() {
               public Object load(String mimetype, InputStream input) {
                  if (input == null)
                     return null;
                  try {
                     SAXBuilder builder = new SAXBuilder();
                     return builder.build(input);
                  }
                  catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
               public boolean save(String mimetype, OutputStream output, Object obj) {
                  if (output == null || obj == null)
                     return false;
                  try {
                     if (obj instanceof Document) {
                        new XMLOutputter().output((Document)obj, output);
                        return true;
                     }
                     return false;
                  }
                  catch (Exception e) { return false; }
               }
            }
      );
      add(new Class[]{},
            new String[]{"text/json", "application/json"},
            new String[]{"json"},
            new Processor() {
               public Object load(String mimetype, InputStream input) {
                  try {
                     String txt = new String(Stream.getBytes(input)).trim();
                     if (txt.equals(""))
                        return null;
                     Class type;
                     switch (txt.charAt(0)) {
                        case '{':
                           type = Map.class;
                           break;
                        case '[':
                           type = List.class;
                           break;
                        case '"':
                           type = String.class;
                           break;
                        case 't':
                        case 'f':
                           type = Boolean.class;
                           break;
                        case 'n':
                           type = Object.class;
                           break;
                        default:
                           type = Number.class;
                     }
                     return jsonMapper.readValue(txt, type);
                  }
                  catch (Exception e) { return Context.exception(e, "JSON.load("+input+")"); }
               }
               public boolean save(String mimetype, OutputStream output, Object obj) {
                  try {
                     jsonMapper.writeValue(output, obj);
                     return true;
                  }
                  catch (Exception e) {
                     Context.exception(e, "JSON.save("+output+","+obj+")");
                     return false;
                  }
               }
            }
      );
      add(new Class[]{Collection.class},
            new String[]{"text/csv"},
            new String[]{"csv"},
            new Processor() {
               public final char[] splitters = new char[]{',', ';', '\t'};
               public Object load(String mimetype, InputStream input) {
                  if (input == null)
                     return new Object[0][];
                  try {
                     List<Object[]> lines = new ArrayList<Object[]>();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                     String  line;
                     char[] separator = new char[1];
                     while ((line = reader.readLine()) != null)
                        lines.add(getLineColumns(line, separator));
                     return lines.toArray(new Object[lines.size()][]);
                  }
                  catch (Exception e) {
                     return new Object[][]{{e}};
                  }
               }

               public boolean save(String mimetype, OutputStream output, Object obj) {
                  if (output == null)
                     return false;
                  try {
                     output.write(print(obj));
                     return true;
                  }
                  catch (Exception e) { return false; }
               }

               private Object[] getLineColumns(String line,  char[] separator) {
                  if (line == null)
                     return new Object[0];
                  line = line.trim();
                  if (line.equals(""))
                     return new Object[0];

                  char[] chars = line.toCharArray();
                  List cells = new ArrayList();
                  char instr = 0;
                  int start = 0;

                  for (int i = 0; i < chars.length; i++) {
                     char c = chars[i];
                     if (instr > 0) {
                        if (c == instr)
                           instr = 0;
                        continue;
                     }

                     switch (c) {
                        case '"':
                        case '\'':
                           instr = c;
                           break;
                        case ',':
                        case ';':
                        case '\t':
                           if (separator[0] == 0)
                              separator[0] = c;
                           else if (separator[0] != c)
                              continue;
                           addCell(cells, chars, start, i);
                     }
                  }

                  if (start >= 0)
                     addCell(cells, chars, start, chars.length);
                  return cells.toArray();
               }

               private void addCell(List cells, char[] chars, int start, int end) {
                  String txt = new String(chars,start,end-start).trim();
                  Object value;
                  if (txt.equals(""))
                     value = txt;
                  else if (txt.startsWith("'") || txt.startsWith("\"")) {
                     if (txt.charAt(0) == txt.charAt(txt.length()-1))
                        value = txt.substring(1,txt.length()-1);
                     else
                        value = txt.substring(1);
                  }
                  else
                     value = txt;
                  cells.add(value);
               }

               private byte[] print(Object obj) {
                  Object[] rows;
                  if (obj == null)
                     return new byte[0];
                  else if (obj instanceof CharSequence)
                     return obj.toString().getBytes();
                  else if (obj instanceof char[])
                     return new String((char[])obj).getBytes();
                  else if (obj instanceof byte[])
                     return (byte[])obj;
                  else if (obj.getClass().isArray())
                     rows = Converter.toArray(obj);
                  else if (obj instanceof Iterable)
                     rows = new Group(obj).toArray();
                  else if (obj instanceof Number || obj instanceof Boolean)
                     return obj.toString().getBytes();
                  else
                     rows = new Object[]{obj};

                  if (rows.length == 0)
                     return new byte[0];
                  List fields = null;
                  List<Object[]> dst = new ArrayList<Object[]>();
                  char[] splitter = new char[]{'\0'};
                  Object first = rows[0];
                  if (first == null)
                     dst.add(new Object[0]);
                  else if (first instanceof CharSequence)
                     dst.add(splitString(first.toString(), splitter));
                  else if (first instanceof char[])
                     dst.add(splitString(new String((char[])first), splitter));
                  else if (first instanceof byte[])
                     dst.add(splitString(new String((byte[])first), splitter));
                  else if (first.getClass().isArray())
                     dst.add(Converter.toArray(obj));
                  else if (obj instanceof Iterable)
                     dst.add(new Group(obj).toArray());
                  else if (obj instanceof Number || obj instanceof Boolean)
                     dst.add(new Object[]{obj.toString()});
                  else {
                     Map map = Converter.toMap(first);
                     fields = new ArrayList(map.keySet());
                     dst.add(map.values().toArray());
                  }

                  for (int r = 1; r < rows.length; r++) {
                     Object row = rows[r];
                     if (fields != null) {
                        List cells = new ArrayList();
                        Map map = Converter.toMap(row);
                        for (Object f : fields)
                           cells.add(map.get(f));
                        for (Object key : map.keySet()) {
                           if (!fields.contains(key)) {
                              fields.add(key);
                              cells.add(map.get(key));
                           }
                        }
                        dst.add(cells.toArray());
                     }
                     else if (row == null)
                        dst.add(new String[0]);
                     else if (row instanceof CharSequence)
                        dst.add(splitString(row.toString(), splitter));
                     else if (row instanceof char[])
                        dst.add(splitString(new String((char[])row), splitter));
                     else if (row instanceof byte[])
                        dst.add(splitString(new String((byte[])row), splitter));
                     else if (row.getClass().isArray())
                        dst.add(Converter.toArray(row));
                     else if (row instanceof Iterable)
                        dst.add(new Group(row).toArray());
                     else if (row instanceof Number || row instanceof Boolean)
                        dst.add(new Object[]{row.toString()});
                     else
                        dst.add(Converter.toMap(row).values().toArray());
                  }

                  if (fields != null)
                     dst.add(0, fields.toArray());

                  StringBuilder buffer = new StringBuilder();
                  for (Object[] row : dst) {
                     if (buffer.length() > 0)
                        buffer.append("\n");
                     String sep = null;
                     for (Object cell : row) {
                        if (sep == null) {
                           if (splitter[0] == 0)
                              splitter[0] = splitters[0];
                           sep = splitter[0] + "";
                        }
                        else
                           buffer.append(sep);
                        String txt = getString(cell);
                        if (txt.indexOf(splitter[0]) >= 0) {
                           char pad = (txt.indexOf('\'') >= 0) ? '"' : '\'';
                           txt = pad + txt + pad;
                        }
                        buffer.append(txt);
                     }
                  }

                  return buffer.toString().getBytes();
               }

               private String[] splitString(String txt, char[] splitter) {
                  if (splitter[0] != 0)
                     return txt.split(splitter[0]+"");
                  String[] parts = null;
                  for (char c : splitters) {
                     String[] p = txt.split(c+"");
                     if (parts == null || p.length > parts.length) {
                        parts = p;
                        if (p.length > 1)
                           splitter[0] = c;
                     }
                  }
                  return parts;
               }

               private String getString(Object obj) {
                  if (obj == null)
                     return "";
                  else if (obj instanceof char[])
                     return new String((char[])obj);
                  else if (obj instanceof byte[])
                     return new String((byte[])obj);
                  else if (obj.getClass().isArray()) {
                     StringBuilder buffer = new StringBuilder("(");
                     int n = Array.getLength(obj);
                     for (int i = 0; i < n; i++) {
                        if (i > 0)
                           buffer.append(",");
                        buffer.append(getString(Array.get(obj, i)));
                     }
                     return buffer.append(")").toString();
                  }
                  else if (obj instanceof Collection)
                     return getString(((Collection)obj).toArray());
                  else if (obj instanceof Entity)
                     return getString(((Entity)obj).getSource());
                  else if (obj instanceof Map)
                     return getString(((Map)obj).values().toArray());
                  else
                     return obj.toString();
               }
            }
      );
      add(new Class[]{},
            new String[]{"application/pdf"},
            new String[]{"pdf"}
      );
      add(new Class[]{},
            "application/msword,application/vnd.ms-excel,application/vnd.ms-powerpoint,application/x-msaccess".split(","),
            "doc,xls,ppt,mdb".split(",")
      );
      add(new Class[]{java.util.zip.ZipFile.class},
            "application/x-zip-compressed,application/java-archive,application/x-gtar".split(","),
            "zip,jar,tgz,gz".split(",")
      );
      add(new Class[]{File.class},
            new String[]{Mimetype.folderMimetype},
            "d,dir".split(",")
      );
   }
}
