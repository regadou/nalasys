package org.regadou.nalasys;

import java.sql.*;
import java.util.*;
import java.io.*;

public class Database {

   public static final int databaseConnectionRetries = 3;
   public static final String SPLITURL = "\\?";
	public static enum MetaData { PRODUCT, CATALOG, TABLE, COLUMN, PRIMARY, IMPORTED, EXPORTED };
   public static final Map<String,String> separatorsMap = new LinkedHashMap<String,String>();
   static {
      separatorsMap.put("in", ", ");
      separatorsMap.put("between", " and ");
      separatorsMap.put("and", " or ");
      separatorsMap.put("or", " and ");
   }

   private static final Map<String,Database> databaseMap = new TreeMap<String,Database>();
   private static final Map<String,Vendor> vendorMap = new TreeMap<String,Vendor>();
   static {
      for (Vendor v : new Vendor[]{
            new Vendor("derby",      true,  "org.apache.derby.jdbc.EmbeddedDriver",         "",   ""),
            new Vendor("hsqldb",     false, "org.hsqldb.jdbcDriver",                        "",   ""),
            new Vendor("mysql",      true,  "com.mysql.jdbc.Driver",                        "`",  "`"),
            new Vendor("odbc",       true,  "sun.jdbc.odbc.JdbcOdbcDriver",                 "",   ""),
            new Vendor("oracle",     false, "oracle.jdbc.driver.OracleDriver",              "",   ""),
            new Vendor("postgresql", true,  "org.postgresql.Driver",                        "\"", "\""),
            new Vendor("access",     true,  "org.regadou.jmdb.MDBDriver",                   "[",  "]"),
            new Vendor("sqlserver",  true,  "com.microsoft.sqlserver.jdbc.SQLServerDriver", "\"", "\""),
            new Vendor("sqlite",     true,  "org.sqlite.JDBC",                              "\"", "\""),
      }) {
         vendorMap.put(v.getName(), v);
      }
   };

   public static Database openDatabase() throws Exception {
      return Context.currentContext().getDatabase();
   }

   public static Database openDatabase(String url) throws Exception {
      String name = url.split(SPLITURL)[0];
      Database db = databaseMap.get(name);
      if (db == null) {
         db = new Database(url);
         databaseMap.put(name, db);
      }
      return db;
   }

   private Connection con;
   private Statement st;
   private String src, delimiters[];
   private Vendor vendor;
   private Map<String,Map<String,Map<String,Object>>> tablesColumns = new LinkedHashMap<String,Map<String,Map<String,Object>>>();

   private Database(String url) {
      src = url;
      if (src == null || src.trim().equals("") || !src.startsWith("jdbc:"))
         throw new RuntimeException("Invalid url "+src);
      try {
         String name = src.split(":")[1];
         vendor = vendorMap.get(name);
         Class.forName(vendor.getDriver()).newInstance();
         delimiters = new String[]{vendor.getQuoteBegin(), vendor.getQuoteEnd()};
      }
      catch (Exception e) {
         throw new RuntimeException("Could not load JDBC driver for "+url, e);
      }
   }

   @org.codehaus.jackson.annotate.JsonValue
   public String toString() {
      return getUrl();
   }

   public boolean equals(Object obj) {
      if (!(obj instanceof Database))
         return false;
      return src.equalsIgnoreCase(((Database)obj).src);
   }

   public String getUrl() {
      return src.split(SPLITURL)[0];
   }

   public String getName() {
      return getUrl();
   }

   public Connection getConnection() {
      if (con == null) {
         try { open(); }
         catch (Exception e) { Context.exception(e, "Database.getConnection()"); }
      }
      return con;
   }

   public void open() throws IOException {
      String checking = null;
      try {
           checking = "connection";
           if (con != null && con.isClosed()) {
              con = null;
              st = null;
           }
           checking = "statement";
           if (st != null && st.isClosed())
              st = null;
           checking = null;
      }
      catch (Throwable t) {
         if ("connection".equals(checking))
            con = null;
         st = null;
      }

      try {
           if (con == null) {
               String txt = src;
               if (!vendor.isPassword())
               {
                  Object user = "", pass = "";
                  int i = txt.indexOf('?');
                  if (i > 0) {
                     Map params = Converter.convert(src.substring(i+1).split("\\&"), Map.class);
                     user = params.get("user");
                     if (user == null) user = "";
                     pass = params.get("password");
                     if (pass == null) pass = "";
                     txt = txt.substring(0,i);
                  }
                  con = DriverManager.getConnection(txt, user.toString(), pass.toString());
               }
               if (con == null)
                  con = DriverManager.getConnection(txt);
          }
          if (st == null) {
              try {
                 st = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
              }
              catch (Exception e) {
                 st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
              }
          }
      }
      catch (Exception e) {
         throw new IOException("Connection to " + getUrl() + " failed: " + e.toString(), e);
      }
   }

   public void close() {
        if (st != null) {
            try { st.close(); }
            catch (Exception e) {}
            st = null;
        }
        if (con != null) {
            try { con.close(); }
            catch (Exception e) {}
            con = null;
        }
        databaseMap.remove(getUrl());
   }

   protected void finalize() { close(); }

   public Iterator query(String q) throws SQLException, IOException {
      open();
      String table = null;
      String[] parts = q.toLowerCase().split("from");
      for (int i = 1; i < parts.length; i++) {
         String previous = parts[i-1];
         String current = parts[i];
         if (previous.equals("") || previous.charAt(previous.length()-1) > 32
            || current.equals("") || current.charAt(0) > 32)
            continue;
         table = current.trim().split(",")[0].trim();
         int length = table.length();
         for (int c = 0; c < length; c++) {
            if (table.charAt(c) <= 32) {
               table = table.substring(0, c);
               break;
            }
         }
      }
      return new ResultSetIterator(st.executeQuery(q), table, this);
   }

   public int execute(String q) throws SQLException, IOException {
      open();
      return st.executeUpdate(q);
   }

   public String getVendor() {
      try {
			List<Map<String,Object>> props = getMetaData(MetaData.PRODUCT, null);
			if (props == null || props.isEmpty())
				return "Unknown";
			else
				return props.get(0).get("name").toString();
		}
      catch (Exception e) { throw new RuntimeException(e.toString()); }
	}

   public String[] getDelimiters() {
      return delimiters;
   }

   public String getDatabase() {
      String[] parts = getUrl().split("/");
      return parts[parts.length-1];
   }

   public String[] getDatabases() {
      try {
		   List<Map<String,Object>> recs = getMetaData(MetaData.CATALOG, null);
         if (recs == null || recs.isEmpty())
            return null;
         String lst[] = new String[recs.size()];
         for (int i = 0; i < recs.size(); i++) {
            Map obj = recs.get(i);
            lst[i] = obj.get("table_cat").toString();
         }
         return lst;
      }
      catch (Exception e) { throw new RuntimeException(e.toString()); }
   }

   public String[] getTables() throws SQLException, IOException {
      List<Map<String,Object>> recs = getMetaData(MetaData.TABLE, getDatabase());
      List lst = new ArrayList();
      for (Map rec : recs) {
         String type = rec.get("table_type").toString().toLowerCase();
         if (type.equals("table"))
            lst.add(rec.get("table_name"));
      }
      return (String[])(lst.toArray(new String[lst.size()]));
   }

   public List<Map<String,Object>> getTableData() throws SQLException, IOException {
      return getMetaData(MetaData.TABLE, getDatabase());
   }

   public Map<String,Map<String,Object>> getColumns(String table) throws SQLException, IOException {
      String name = null;
      if (table == null || table.equals(""))
         return null;
      for (String txt : getTables()) {
         if (txt.equalsIgnoreCase(table)) {
            name = txt;
            break;
         }
      }
      if (name == null)
         return null;
      Map<String,Map<String,Object>> cols = tablesColumns.get(name);
      if (cols != null)
         return cols;

      List<Map<String,Object>> recs = getMetaData(MetaData.COLUMN, getDatabase()+","+table);
      if (recs == null || recs.isEmpty())
         return null;

      cols = new LinkedHashMap<String,Map<String,Object>>();
      for (Map<String,Object> rec : recs) {
         String txt = rec.get("column_name").toString();
         cols.put(txt, rec);
      }

      tablesColumns.put(name, cols);
      return cols;
   }

   public String getPrimaryKey(String table) throws IOException, SQLException {
      List<Map<String,Object>> recs = getMetaData(MetaData.PRIMARY, table);
      String key = null;
      if (recs != null) {
         int pos = 0;
         for (Map rec : recs) {
            String thiskey = rec.get("COLUMN_NAME").toString();
            int thispos = ((Number)rec.get("KEY_SEQ")).intValue();
            if (key == null || thispos < pos) {
               key = thiskey;
               pos = thispos;
            }
         }
      }

      return key;
   }

   public List<String[]> getForeignKeys(String table) {
      List<String[]> keys = new ArrayList<String[]>();
      try {
         List<Map<String,Object>> recs = getMetaData(MetaData.IMPORTED, table);
         for (Map rec : recs)
            keys.add(new String[]{rec.get("pktable_name").toString(), rec.get("pkcolumn_name").toString(),
                                  rec.get("fktable_name").toString(), rec.get("fkcolumn_name").toString()});
         recs = getMetaData(MetaData.EXPORTED, table);
         for (Map rec : recs)
            keys.add(new String[]{rec.get("pktable_name").toString(), rec.get("pkcolumn_name").toString(),
                                  rec.get("fktable_name").toString(), rec.get("fkcolumn_name").toString()});
      } catch (Exception e) {}
      return keys;
   }

   public List<Map<String,Object>> getRows(String q) throws SQLException, IOException {
      List<Map<String,Object>> lst = new ArrayList<Map<String,Object>>();
      Iterator<Map<String,Object>> i = query(q);
      while (i.hasNext())
         lst.add(i.next());
      return lst;
   }

   public List<Map<String,Object>> getRows(ResultSet rs) throws SQLException {
      List<Map<String,Object>> recs = new ArrayList<Map<String,Object>>();
      while (rs.next())
         recs.add(getRow(rs, null));
      return recs;
   }

   public List<Map<String,Object>> getRows(Map filter) throws SQLException, IOException {
      if (filter == null || filter.isEmpty())
         return new ArrayList<Map<String,Object>>();
      String table = null;
      Map<String,Map<String,Object>> cols = null;
      for (Object key : filter.keySet()) {
         if ("class".equals(key) || "table".equals(key)) {
            table = String.valueOf(filter.get(key));
            cols = getColumns(table);
            if (cols != null)
               break;
         }
      }

      if (cols == null)
         throw new RuntimeException("Unknown table "+table);

      String conditions = "";
      String oplst = "<==>=!=&&||";
      for (Object key : filter.keySet()) {
         if (key == null)
            continue;
         if (cols.get(key) == null)
            continue;
         Object value = filter.get(key);
         String operator = "=";
         if (value == null)
            operator = "is";
         else if (value instanceof CharSequence) {
            String txt = value.toString().trim();
            int i = txt.indexOf('(');
            if (i >= 0) {
               operator = txt.substring(0, i);
               int last = txt.lastIndexOf(')');
               if (last > i)
                  value = txt.substring(i, last).split(",");
               else
                  value = txt.substring(i);
            }
            else {
               int oplen = 0;
               i = oplst.indexOf(txt.charAt(0));
               if (i >= 0) {
                  char next = txt.charAt(1);
                  operator = null;
                  switch (oplst.charAt(i)) { // "<==>=!=&&||"
                     case '<':
                        oplen = (next == '=' || next == '>') ? 2 : 1;
                        break;
                     case '>':
                        oplen = (next == '=') ? 2 : 1;
                        break;
                     case '=':
                        oplen = (next == '=') ? 2 : 1;
                        operator = "=";
                        break;
                     case '!':
                        oplen = (next == '=') ? 2 : 1;
                        if (oplen == 1)
                           operator = "not";
                        break;
                     case '&':
                        oplen = (next == '&') ? 2 : 1;
                        operator = "and";
                        break;
                     case '|':
                        oplen = (next == '|') ? 2 : 1;
                        operator = "or";
                        break;
                  }
                  if (operator == null)
                     operator = txt.substring(0,oplen);
                  value = txt.substring(oplen);
               }
            }
            if (operator.equals(""))
               operator = (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) == 2)
                        ? "between" : "in";
         }

         String separator = separatorsMap.get(operator);
         conditions += (conditions.equals("") ? " where " : " and ")
                     + key + " " + operator + " " + sqlvalue(value, separator);
      }
      return getRows("select * from "+table+conditions);
   }

   public Map<String,Object> getRow(ResultSet rs, String table) throws SQLException {
      ResultSetMetaData meta = rs.getMetaData();
      int nc = meta.getColumnCount();
      Map<String,Object> row = new LinkedHashMap<String,Object>();
      for (int c = 1; c <= nc; c++) {
         String col = meta.getColumnName(c).toLowerCase();
         Object val = rs.getObject(c);
         row.put(col, val);
      }
      return row;
   }

   public int putRow(String table, Map row, String condition) throws SQLException, IOException {
      boolean insert = (condition == null);
      if (!insert) {
         condition = condition.trim();
         if (condition.toLowerCase().startsWith("where"))
            condition = " "+condition;
         else if (!condition.equals(""))
            condition = " where "+condition;
      }

      Map<String,Map<String,Object>> cols = getColumns(table);
      String sql1 = insert ? "insert into "+table+" (" : "update "+table+" set ";
      String sql2 = insert ? ") values ("              : condition;
      String sql3 = insert ? ")"                       : "";
      String separator = null;

      int nf = 0;
      for (Object key : row.keySet()) {
         Map<String,Object> col = cols.get(key);
         if (col == null)
            continue;
         else if (insert) {
            if (separator == null)
               separator = ", ";
            else {
               sql1 += separator;
               sql2 += separator;
            }
            sql1 += key;
            sql2 += sqlvalue(row.get(key), col);
         }
         else {
            if (separator == null)
               separator = ", ";
            else
               sql1 += separator;
            sql1 += key + " = " + sqlvalue(row.get(key), col);
         }
         nf++;
      }

      if (nf == 0)
         return  0;
      return execute(sql1+sql2+sql3);
   }

   public String getCondition(String table, Map map) throws SQLException, IOException {
      if (map == null || map.isEmpty())
         return "";
      Map<String,Map<String,Object>> cols = getColumns(table);
      String sql = "";
      for (Object key : map.keySet()) {
         if (key == null)
            continue;
         Map<String,Object> col = cols.get(key.toString());
         if (col == null)
            continue;
         else if (sql.equals(""))
            sql = " where ";
         else
            sql += " and ";
         sql += key + " = " + sqlvalue(map.get(key), col);
      }
      return sql;
   }


   private static final List<String> numericBooleanVendors = Arrays.asList(new String[]{"oracle", "derby"});
   public String sqlvalue(Object val) {
      return sqlvalue(val, null, null);
   }

   public String sqlvalue(Object val, String separator) {
      return sqlvalue(val, separator, null);
   }

   public String sqlvalue(Object val, Map<String,Object> col) {
      return sqlvalue(val, null, col);
   }

   public String sqlvalue(Object val, String separator, Map<String,Object> col) {
      if (val == null)
         return "null";
      else if (val instanceof Number)
         return val.toString();
      else if (val instanceof Boolean) {
         if (numericBooleanVendors.contains(vendor.getName().toLowerCase()))
            return ((Boolean)val).booleanValue() ? "1" : "0";
         else
            return val.toString();
      }
      else if (val instanceof java.util.Date)
         return "{ts '"+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date)val)+"'}";
      else if (val.getClass().isArray()) {
         StringBuilder sql = new StringBuilder("");
         int n = java.lang.reflect.Array.getLength(val);
         for (int i = 0; i < n; i++) {
            if (i == 0) {
               if (separator == null)
                  separator = ", ";
            }
            else
               sql.append(separator);
            sql.append(sqlvalue(java.lang.reflect.Array.get(val, i), separator, col));
         }
         return "(" + sql + ")";
      }
      else if (val instanceof Iterable || val instanceof Iterator || val instanceof Enumeration)
         return sqlvalue(new Group(val).toArray(), separator, col);
      else if (val instanceof Map)
         return sqlvalue(((Map)val).values().toArray(), separator, col);
      else {
         String txt = val.toString().replace("'", "''");
         if (col != null) {
            if (isTimeColumn(col))
               txt = txt.toLowerCase().replace('t', ' ').replace('z', ' ').split("\\.")[0].trim();
            else if (isNumberColumn(col)) {
               try { return new Double(txt).toString(); }
               catch (Exception e) { return "0"; }
            }
         }
         return "'" + txt + "'";
      }
   }

   private boolean isTimeColumn(Map<String,Object> col) {
      for (Map.Entry<String,Object> entry : col.entrySet()) {
         if (entry.getKey().toLowerCase().contains("type")) {
            Object value = entry.getValue();
            if (value == null)
               continue;
            String type = value.toString().toLowerCase();
            if (type.contains("date") || type.contains("time"))
               return true;
         }
      }
      return false;
   }

   private boolean isNumberColumn(Map<String,Object> col) {
      for (Map.Entry<String,Object> entry : col.entrySet()) {
         if (entry.getKey().toLowerCase().contains("type")) {
            Object value = entry.getValue();
            if (value == null)
               continue;
            String type = value.toString().toLowerCase();
            if (type.contains("int") || type.contains("float") || type.contains("num") || type.contains("double"))
               return true;
         }
      }
      return false;
   }

   public List<Map<String,Object>> getMetaData(MetaData md, String param) throws SQLException, IOException {
		if (md == null)
			return new ArrayList<Map<String,Object>>();
      Set<String> messages = new LinkedHashSet<String>();
      int tries = 0;
      do {
         tries++;
         try {
            open();
            DatabaseMetaData dmd = con.getMetaData();
            if (dmd == null) {
               String q;
               switch (md)
               {
               case PRODUCT:
                  q = "system product";
                  break;
               case CATALOG:
                  q = "system catalog";
                  break;
               case TABLE:
                  q = "system table";
                  break;
               case COLUMN:
                  if (param == null)
                     return new ArrayList<Map<String,Object>>();
                  String params[] = param.split(",");
                  if (params.length < 2)
                     return new ArrayList<Map<String,Object>>();
                  q = "select * from "+params[1];
                  break;
               default:
                  return new ArrayList<Map<String,Object>>();
               }
               st.execute(q);
               return getRows(st.getResultSet());
            }
            else {
               switch (md)
               {
               case PRODUCT:
                  return Collections.singletonList(Collections.singletonMap("name", (Object)dmd.getDatabaseProductName()));
               case CATALOG:
                  return getRows(dmd.getCatalogs());
               case TABLE:
                  return getRows(dmd.getTables(param,null,null,null));
               case COLUMN:
                  if (param == null)
                     return new ArrayList<Map<String,Object>>();
                  String params[] = param.split(",");
                  if (params.length < 2)
                     return new ArrayList<Map<String,Object>>();
                  String db = params[0];
                  String tab = params[1];
                  return getRows(dmd.getColumns(db,null,tab,null));
               case PRIMARY:
                  return getRows(dmd.getPrimaryKeys(null, null, param));
               case IMPORTED:
                  return getRows(dmd.getImportedKeys(null, null, param));
               case EXPORTED:
                  return getRows(dmd.getExportedKeys(null, null, param));
               }
            }
         }
         catch (Exception e) {
            String message;
            if (tries < databaseConnectionRetries) {
               message = e.getMessage();
               if (message == null || message.trim().equals(""))
                  message = e.getClass().getName();
               messages.add(message);
               close();
            }
            else {
               message = "";
               for (String m : messages)
                   message += m + "\n";
               throw new RuntimeException(message, e);
            }
         }
      }
      while (tries < databaseConnectionRetries);

		return new ArrayList<Map<String,Object>>();
	}
}

class Vendor {
   private String name;
   private boolean password;
   private String driver;
   private String quoteBegin;
   private String quoteEnd;

   public Vendor(String name, boolean password, String driver, String quoteBegin, String quoteEnd) {
      this.name = name;
      this.password = password;
      this.driver = driver;
      this.quoteBegin = (quoteBegin == null) ? "" : quoteBegin;
      this.quoteEnd = (quoteEnd == null) ? "" : quoteEnd;
   }

   public Vendor(Map properties) {
      this.name = getString(properties.get("name"));
      this.password = Converter.toBoolean(properties.get("password"));
      this.driver = getString(properties.get("driver"));;
      this.quoteBegin = getString(properties.get("quoteBegin"));
      this.quoteEnd = getString(properties.get("quotedEnd"));
   }

   public String toString() {
      return "[DatabaseVendor "+name+"]";
   }

   public boolean equals(Object o) {
      try { return (o instanceof Vendor && ((Vendor)o).name.equals(name)); }
      catch (Exception e) { return false; }
   }

   public String getDriver() {
      return driver;
   }

   public String getName() {
      return name;
   }

   public boolean isPassword() {
      return password;
   }

   public String getQuoteBegin() {
      return quoteBegin;
   }

   public String getQuoteEnd() {
      return quoteEnd;
   }

   private String getString(Object value) {
      if (value == null)
         return "";
      else
         return value.toString().trim();
   }
}

class ResultSetIterator implements Iterator<Map<String,Object>> {

   private ResultSet rs;
   private String table;
   private Database db;
   private boolean hasNextCalled, isEnd;

   public ResultSetIterator(ResultSet rs, String table, Database db) {
      this.rs = rs;
      this.table = table;
      this.db = db;
   }

   public boolean hasNext() {
      try {
         if (!hasNextCalled) {
            isEnd = !rs.next();
            hasNextCalled = true;
         }
         return !isEnd;
      }
      catch (Exception e) {
         Context.exception(e, "Iterator.hasNext()");
         return false;
      }
   }

   public Map<String,Object> next() {
      try {
         if (isEnd || (!hasNextCalled && !hasNext()))
            throw new NoSuchElementException("End of the iterator has been reached");
         hasNextCalled = false;
         return db.getRow(rs, table);
      }
      catch (Exception e) {
         Context.exception(e, "Iterator.next()");
         return null;
      }
   }

   public void remove() {
      throw new UnsupportedOperationException("Cannot remove a row from a result set");
   }
}
