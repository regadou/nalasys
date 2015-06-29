package org.regadou.nalasys;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.http.*;

public class Servlet extends HttpServlet {

   public static final String defaultMimetype = "application/json";
   public static final String defaultEncoding = "utf8";
   public static final String formEncoding = "application/x-www-form-urlencoded";
   public static final String multiPartEncoding = "multipart/form-data";
   public static final String jsonStarters = "\"{[tfn0123456789-";

   public String toString() {
      return "[Servlet "+getName()+"]";
   }

   public String getServletInfo() {
      return "Nalasys servlet by Regis Dehoux";
   }

   public String getName() {
      return getServletName();
   }

   public void doGet(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
      doRequest(httpRequest, httpResponse);
   }

   public void doPost(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
      doRequest(httpRequest, httpResponse);
   }

   public void doPut(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
      doRequest(httpRequest, httpResponse);
   }

   public void doDelete(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
      doRequest(httpRequest, httpResponse);
   }

   public void doRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
      OutputStream output = response.getOutputStream();
      try {
         Context cx = Context.currentContext();
         List properties = new Group(cx);
         String path = request.getPathInfo();
         if (path != null) {
            while (path.startsWith("/"))
               path = path.substring(1);
            if (!path.equals(""))
               properties.addAll(Arrays.asList(path.split("/")));
         }

         DataHolder data = new DataHolder();
         Map params = getParameters(request, data);
         String type = (String)params.remove("content-type");
         if (type == null) {
            type = request.getHeader("accept");
            if (type == null)
               type = defaultMimetype;
            else
               type = type.split(",")[0].split(";")[0];
         }
         if (type.startsWith("*"))
            type = defaultMimetype;

         Mimetype m = Mimetype.getMimetype(type);
         if (m == null) {
            type = defaultMimetype;
            m = Mimetype.getMimetype(type);
         }

         if (data.data == null) {
            data.data = params.remove("data");
            if (data.data != null) {
               data.mimetype = type;
               if (data.data instanceof String) {
                  String txt = data.data.toString().trim();
                  if (txt.length() > 0 && jsonStarters.indexOf(txt.charAt(0)) >= 0) {
                     Stream s = new Stream(txt.getBytes(), defaultMimetype);
                     data.data = Mimetype.getMimetype(defaultMimetype).load(s.getInputStream(), defaultMimetype);
                     data.mimetype = defaultMimetype;
                     s.close();
                  }
               }
            }
         }

         String lang = request.getHeader("accept-language");
         if (lang != null) {
            lang = lang.split(",")[0].split(";")[0];
            cx.setLocale(new Locale(lang));
         }

         Object result;
         Object source = Converter.getValue(Converter.getPathValue(properties.toArray()));
         response.setContentType(type);
         response.setCharacterEncoding(defaultEncoding);

         String method = (String)params.remove("http-method");
         if (method == null)
            method = request.getMethod();
         if (method.equalsIgnoreCase("POST"))
            result = (Converter.updateValues(source, params, data.data) > 0) ? "OK" : "Could not update";
         else if (method.equalsIgnoreCase("PUT"))
            result = (Converter.insertValues(source, params, data.data) > 0) ? "OK" : "Could not insert";
         else if (method.equalsIgnoreCase("DELETE"))
            result = (Converter.deleteValues(source, params) > 0) ? "OK" : "Could not delete";
         else
            result = Converter.selectValues(source, params);

         m.save(output, Converter.getValue(result), type);
      }
      catch (Throwable e) {
         response.setStatus(500);
         response.setContentType("text/plain");
         response.setCharacterEncoding(defaultEncoding);
         PrintWriter out = new PrintWriter(output);
         e.printStackTrace(out);
         out.flush();
         System.err.println(new Date()+" from "+request.getRemoteHost()+":"+request.getRemotePort()+" has exception "+e.toString());
         e.printStackTrace(System.err);
      }
   }

   public static Map getParameters(HttpServletRequest request) throws Exception {
      return getParameters(request, null);
   }

   public static Map getParameters(HttpServletRequest request, DataHolder data) throws Exception {
      Map map = new LinkedHashMap();
      if (request == null)
         return map;
      String query = request.getQueryString();
      if (query != null && !query.equals(""))
         decodeMap(map, query);
      String method = request.getMethod().toLowerCase();
      if (method.equals("post") || method.equals("put")) {
         String type = request.getContentType();
         String encoding = defaultEncoding;
         if (type == null)
            type = formEncoding;
         else {
            int i = type.indexOf(';');
            if (i > 0) {
               encoding = type.substring(i+1).replace("charset=","").trim();
               type = type.substring(0,i).trim();
            }
         }

         if (type.equals(formEncoding)) {
            query = new String(getInputBytes(request), encoding);
            if (query != null && !query.trim().equals("")) {
               decodeMap(map, query);
            }
         }
         else if (type.indexOf("json") > 0) {
            if (data != null) {
               data.data = Mimetype.getMimetype(type).load(request.getInputStream(), type);
               data.mimetype = type;
            }
         }
         else if (type.startsWith("text/")) {
            if (data != null) {
               data.data = new String(getInputBytes(request), encoding);
               data.mimetype = type;
            }
         }
         else if (type.equals(multiPartEncoding)) {
            Map obj = new LinkedHashMap();
            decodeMultipart(obj, request);
            if (!obj.isEmpty() && data != null) {
               data.data = obj;
               data.mimetype = type;
            }
         }
         else if (data != null) {
            data.data = Mimetype.getMimetype(type).load(request.getInputStream(), type);
            data.mimetype = type;
         }
      }
      return map;
   }

   private static byte[] getInputBytes(HttpServletRequest request) throws Exception {
      int n = request.getContentLength();
      byte[] bytes = new byte[n];
      InputStream input = request.getInputStream();
      for (int at = 0; at < n;) {
         int got = input.read(bytes, at, n-at);
         if (got > 0)
            at += got;
      }
      return bytes;
   }

   private static void decodeMultipart(Map map, HttpServletRequest request) throws Exception {
      throw new RuntimeException("Multipart input not implemented yet");
   }

   private static void decodeMap(Map map, String txt) throws Exception {
      if (txt == null || txt.trim().equals(""))
         return;
      String[] parts = txt.split("&");
      for (String part : parts) {
         String name;
         Object value;
         int i = part.indexOf('=');
         if (i < 0) {
            name = URLDecoder.decode(part, defaultEncoding);
            value = true;
         }
         else {
            name = URLDecoder.decode(part.substring(0,i), defaultEncoding);
            value = URLDecoder.decode(part.substring(i+1), defaultEncoding);
         }
         Object old = map.get(name);
         if (old == null)
            map.put(name, value);
         else if (old instanceof List)
            ((List)old).add(value);
         else
            map.put(name, new Group(new Object[]{old, value}));
      }
   }
}

class DataHolder {
   public Object data;
   public String mimetype;
}