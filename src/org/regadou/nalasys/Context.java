package org.regadou.nalasys;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.text.*;
import javax.script.*;

public final class Context implements ScriptContext {
   public static final String version = "0.3";
   public static final String defaultPrompt= "\n? ";
   public static final String defaultCharset = "UTF-8";
   public static final int USER_SCOPE = ENGINE_SCOPE/2;
   public static final int CONTEXT_SCOPE = USER_SCOPE-10;
   public static final int SYSTEM_SCOPE = USER_SCOPE+10;
   public static final String[] initParams = "server,users".split(",");
   public static final int serverParam=0, usersParam=1;

   private static class UserConfig {
      File file;
      long loaded;
      Map templates;
   };
   private static UserConfig userConfig;
   private static Map initTemplate;
   private static Map users;
   private static final Set<Context> sessions = new LinkedHashSet<Context>();
   private static final Bindings systemBindings = new SimpleBindings();
   private static ThreadLocal currentContext = new ThreadLocal() {
      protected synchronized Object initialValue() { return null; }
   };

   public static boolean init(Map params) {
      try {
         if (initTemplate != null)
            return false;
         else if (params == null)
            initTemplate = new LinkedHashMap();
         else {
            initTemplate = params;
            for (int p = 0; p < initParams.length; p++) {
               Object value = params.get(initParams[p]);
               if (value != null) {
                  switch (p) {
                     case serverParam:
                        TcpServer srv = TcpServer.listen(value.toString());
                        if (srv != null)
                           System.err.println(srv+" started ...");
                        else if (Main.class.getName().equals(params.get(Main.consoleKey)))
                           throw new RuntimeException("\n ***** Error while initing main context ***** \n");
                        break;
                     case usersParam:
                        Stream str = new Stream(value.toString());
                        Object data = Mimetype.getMimetype("text/json").load(str.getInputStream());
                        users = Converter.toMap(data);
                        str.close();
                        System.err.println("Found "+users.size()+" users in "+value);
                        break;
                  }
               }
            }
            for (Object e : params.entrySet()) {
               Map.Entry entry = (Map.Entry)e;
               Object key = entry.getKey();
               if (key == null)
                  continue;
               Class[] types;
               String path = key.toString();
               int par = path.indexOf('(');
               if (par > 0) {
                  String[] parts = path.split(")")[0].substring(par+1).split(",");
                  types = new Class[parts.length];
                  for (int p = 0; p < parts.length; p++)
                     types[p] = Converter.convert(parts[p], Class.class);
               }
               else
                  types = null;
               int last = path.lastIndexOf('.');
               if (last > 0) {
                  Class cl = null;
                  String name = null;
                  try {
                     cl = Converter.toClass(path.substring(0, last).trim());
                     name = path.substring(last+1).trim();
                     if (params == null) {
                        Field f = cl.getField(name);
                        f.set(null, Converter.convert(entry.getValue(), f.getType()));
                     }
                     else {
                        Method m = cl.getMethod(name, types);
                        Object[] values = Converter.toArray(entry.getValue());
                        if (types.length > values.length) {
                           List lst = new Group(values);
                           while (lst.size() < types.length)
                              lst.add(null);
                           values = lst.toArray();
                        }
                        for (int v = 0; v < values.length; v++)
                           values[v] = Converter.convert(values[v], types[v]);
                        m.invoke(null, values);
                     }
                  }
                  catch (Exception ex) { currentContext().getWriter().write("Exception accessing "+name+"@"+cl+": "+e); }
               }
            }
         }

         return true;
      }
      catch (Exception e) {
         exception(e, "Context.init("+params+")");
         return false;
      }
   }

   public static boolean login(String user, String password) {
      if (users == null)
         return false;
      Context cx;
      Object cfg = users.get(user);
      if (cfg == null)
         return false;
      else if (cfg instanceof String) {
         if (!cfg.equals(password))
            return false;
         cx = currentContext();
      }
      else if (cfg instanceof Map) {
         Map cfgmap = (Map)cfg;
         Object pass = cfgmap.get("password");
         if (pass != null && !pass.equals(password))
            return false;
         cx = currentContext();
         Map cxmap = new Entity(cx);
         for (Object key : cfgmap.keySet())
            cxmap.put(key, cfgmap.get(key));
      }
      else
         throw new RuntimeException("Bad configuration for user "+user+": "+cfg);

      if (cx.username == null)
         cx.username = user;
      cx.identified = true;
      return true;
   }

   private static <T extends Object> T getDefaultProperty(Class<T> type, String key, Object defaultValue) {
      Object value;
      if (initTemplate != null) {
         if (initTemplate.containsKey(key))
            value = initTemplate.get(key);
         else
            value = defaultValue;
      }
      else
         value = defaultValue;
      return (value == null) ? null : Converter.convert(value, type);
   }

   public static Context currentContext() {
      return currentContext(true);
   }

   public static Context currentContext(boolean create) {
      Context cx = (Context) currentContext.get();
      if (cx == null && create)
         cx = new Context();
      else if (cx != null)
         cx.last = new Date().getTime();
      return cx;
   }

   protected static void currentContext(Context cx) {
      if (currentContext.get() != null)
         throw new RuntimeException("context already set on this thread");
      else if (cx == null)
         throw new NullPointerException("context is null");
      currentContext.set(cx);
      cx.last = new Date().getTime();
   }

   public static Object exception(Throwable t, String msg) {
      return exception(null, t, msg);
   }

   public static Object exception(Context cx, Throwable t, String msg) {
      if (cx == null)
         cx = currentContext();
      if (t == null)
         t = new NullPointerException();
      Writer out = cx.getErrorWriter();
      if (out == null) {
         out = cx.getWriter();
         if (out == null)
            out = new OutputStreamWriter(System.err);
      }
      try {
         if (msg != null) {
            out.write(msg+":\n");
            out.flush();
         }
         PrintWriter pw = new PrintWriter(out);
         t.printStackTrace(pw);
         pw.flush();
         return t;
      }
      catch (Exception e) {
         throw new RuntimeException("Exception trying to print exception "+t, e);
      }
   }

   public static void closeCurrentContext() {
      Context cx = (Context) currentContext.get();
      if (cx != null)
         cx.close();
   }

   public static class PropertyBindings extends Entity implements Bindings {
      public PropertyBindings(Object obj) {
         super(obj);
      }
   }

   public static Map[] currentSessions() {
      DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      List<Map> maps = new ArrayList<Map>();
      for (Context cx : sessions) {
         Map map = new LinkedHashMap();
         maps.add(map);
         map.put("hash", cx.hashCode());
         map.put("username", (cx.username == null && !cx.identified) ? "<anonymous>" : cx.username);
         map.put("source", cx.source);
         map.put("login", cx.identified);
         map.put("started", formatter.format(new Date(cx.started)));
         map.put("last", formatter.format(new Date(cx.last)));
         map.put("locale", cx.locale);
         map.put("owner", (cx.owner == null) ? "" : cx.owner.toString());
      }
      return maps.toArray(new Map[maps.size()]);
   }


   // management of a context instance
   private String username;
   private String source;
   private long started;
   private long last;
   private Object owner;
   private Locale locale;
   private String charset;
   private File folder;
   private Database database;
   private boolean network;
   private boolean identified;
   private Reader reader;
   private Writer writer;
   private Writer error;
   private Map<Integer,Bindings> scopes = new TreeMap<Integer,Bindings>();

   private Context() {
      started = last = new Date().getTime();
      currentContext(this);
      init(null);
      Map cx = new Entity(this);
      for (Object key : initTemplate.keySet())
         cx.put(key, initTemplate.get(key));
      sessions.add(this);
      setBindings(new PropertyBindings(this), CONTEXT_SCOPE);
      setBindings(systemBindings, SYSTEM_SCOPE);
      setBindings(new SimpleBindings(), USER_SCOPE);
   }

   public String toString() {
      String u = (username == null) ? "" : " user="+username;
      return "[Context "+getSource()+u+"]";
   }

   public boolean isIdentified() {
      return identified;
   }

   public long getStarted() {
      return started;
   }

   public long getLast() {
      return last;
   }

   public String getUsername() {
      return username;
   }

   public String getSource() {
      if (source == null)
         source = getDefaultProperty(String.class, "name", Thread.currentThread().getName());
      return source;
   }

   protected void setSource(String name) {
      this.source = (name == null || name.trim().equals("")) ? null : name.trim();
   }

   protected Object getOwner() {
      return owner;
   }

   protected void setOwner(Object owner) {
      this.owner = owner;
   }

   public Locale getLocale() {
      if (locale == null)
         locale = getDefaultProperty(Locale.class, "locale", new Locale(System.getProperty("user.language")));
      return locale;
   }

   public void setLocale(Locale locale) {
      this.locale = locale;
   }

   public String getCharset() {
      if (charset == null || charset.trim().equals(""))
         charset = getDefaultProperty(String.class, "charset", defaultCharset);
      return charset;
   }

   public void setCharset(String charset) {
      this.charset = charset;
   }

   public Database getDatabase() {
      if (database == null)
         database = getDefaultProperty(Database.class, "database", null);
      return database;
   }

   public void setDatabase(Database database) {
      this.database = database;
   }

   public File getFolder() {
      if (folder == null || !folder.isDirectory())
         folder = (folder != null) ? folder.getParentFile() : getDefaultProperty(File.class, "folder", null);
      if (folder != null && !folder.isAbsolute()) {
         try { folder = folder.getCanonicalFile(); }
         catch (Exception e) { exception(e, "Context.getFolder()"); }
      }
      return folder;
   }

   public void setFolder(File folder) {
      this.folder = folder;
   }

   public boolean isNetwork() { return network; }

   public void setNetwork(boolean n) { network = n; }

   public Reader getReader() {
      if (reader == null)
         reader = getDefaultProperty(Reader.class, "reader", null);
      return reader;
   }

   public void setReader(Reader r) {
      this.reader = r;
   }

   public Writer getWriter() {
      if (writer == null)
         writer = getDefaultProperty(Writer.class, "writer", null);
      return writer;
   }

   public void setWriter(Writer w) {
      this.writer = w;
   }

   public Writer getErrorWriter() {
      if (error == null) {
         error = getDefaultProperty(Writer.class, "error", null);
         if (error == null)
            error = new OutputStreamWriter(System.err);
      }
      return error;
   }

   public void setErrorWriter(Writer e) {
      this.error = e;
   }

   public List<Integer> getScopes() {
      return new ArrayList<Integer>(scopes.keySet());
   }

   public Object getAttribute(String name) {
      for (Map.Entry<Integer,Bindings> entry : scopes.entrySet()) {
         if (entry.getValue().containsKey(name))
               return entry.getValue().get(name);
      }
      return null;
   }

   public Object getAttribute(String name, int scope) {
      Bindings b = scopes.get(scope);
      return (b == null) ? null : b.get(name);
   }

   public Set<String> getAttributes(int scope) {
      Bindings b = scopes.get(scope);
      return (b == null) ? new HashSet<String>() : b.keySet();
   }

   public int getAttributesScope(String name) {
      for (Map.Entry<Integer,Bindings> entry : scopes.entrySet()) {
         if (entry.getValue().containsKey(name))
               return entry.getKey();
      }
      return -1;
   }

   public Map.Entry getAttributeEntry(String name) {
      for (Map.Entry<Integer,Bindings> entry : scopes.entrySet()) {
         Bindings map = entry.getValue();
         if (map.containsKey(name))
            return new Property(map, name);
      }
      return null;
   }

   public Object removeAttribute(String name) {
      Map<Integer,Object> map = new TreeMap<Integer,Object>();
      for (Map.Entry<Integer,Bindings> entry : scopes.entrySet()) {
         if (entry.getValue().containsKey(name))
               map.put(entry.getKey(), entry.getValue().remove(name));
      }
      switch (map.size()) {
         case 0:
               return null;
         case 1:
               return map.values().toArray()[0];
         default:
               return map;
      }
   }

   public Object removeAttribute(String name, int scope) {
      Bindings b = scopes.get(scope);
      return (b == null) ? null : b.remove(name);
   }

   public void setAttribute(String name, Object value) {
      getBindings(USER_SCOPE).put(name, value);
   }

   public void setAttribute(String name, Object value, int scope) {
      getBindings(scope).put(name, value);
   }

   public Bindings getBindings(int scope) {
      Bindings b = scopes.get(scope);
      if (b == null) {
         b = new SimpleBindings();
         scopes.put(scope, b);
      }
      return b;
   }

   public void setBindings(Bindings bindings, int scope) {
      if (bindings == null)
         scopes.remove(scope);
      else
         scopes.put(scope, bindings);
   }

   public void configure(Map props) {
      Map cx = new Entity(this);
      if (props == null) {
         for (Map.Entry e : (Set<Map.Entry>)cx.entrySet())
            cx.put(e.getKey(), null);
      }
      else
         cx.putAll(props);
   }

   public Object console() {
      return console(null);
   }

   public Object console(String prompt) {
      if (userConfig != null && !userConfig.templates.isEmpty() && username == null) {
         try {
            getWriter().write("username: ");
            writer.flush();
            if (!(getReader() instanceof BufferedReader))
               reader = new BufferedReader(reader);
            String user = ((BufferedReader)reader).readLine();
            if (user == null)
               return null;
            getWriter().write("password: ");
            writer.flush();
            String password = ((BufferedReader)reader).readLine();
            if (password == null)
               return null;
            if (!login(user, password))
               return null;
         }
         catch (Exception e) {
            e.printStackTrace(System.err);
         }
      }
      Object result = null;
      if (prompt == null)
         prompt = defaultPrompt;
      while (getReader() != null && getWriter() != null) {
         try {
            writer.write(prompt);
            writer.flush();
            if (!(reader instanceof BufferedReader))
               reader = new BufferedReader(reader);
            String txt = ((BufferedReader)reader).readLine();
            if (txt != null && !txt.trim().equals("")) {
               result = new Expression(txt).getValue();
               writer.write(Converter.toString(result)+"\n");
               writer.flush();
            }
         }
         catch (IOException e) {
            e.printStackTrace(System.err);
            break;
         }
         catch (Exception e) {
            e.printStackTrace(new PrintWriter(writer));
         }
      }
      return result;
   }

   public void close() {
      sessions.remove(this);
      setDatabase(null);
      setReader(null);
      setWriter(null);
      setErrorWriter(null);
      scopes = null;
      username = source = null;
      identified = false;
      if (currentContext.get() == this)
         currentContext.set(null);
   }

   protected void finalize() { close(); }
}

