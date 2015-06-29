package org.regadou.nalasys;

import java.io.*;
import java.util.*;

public class Main {

   public static final String consoleKey = "console";

   public static void main(String[] args) {
      List<String> files = new ArrayList<String>();
      Map<String,String> params = new LinkedHashMap<String,String>();
      for (String arg : args) {
         if (arg == null)
            continue;
         else if (arg.equals("debug")) {
            try {
               System.out.println("*** press enter after starting debugger ***");
               new BufferedReader(new InputStreamReader(System.in)).readLine();
            }
            catch (Exception e) {
               Context.exception(e, "Main.main("+Arrays.asList(args)+")");
            }
            continue;
         }

         int eq = arg.indexOf('=');
         if (eq >= 0) {
            int dp = arg.indexOf(':');
            if (dp < 0 || dp > eq) {
                int qm = arg.indexOf('?');
                if (qm < 0 || qm > eq) {
                    String key = arg.substring(0,eq);
                    String value = arg.substring(eq+1);
                    params.put(key, value);
                    continue;
                }
            }
         }
         files.add(arg);
      }

      try {
         if (params.get(consoleKey) == null)
            params.put(consoleKey, Main.class.getName());
         if (!Context.init(params))
            throw new RuntimeException("\n ***** Error while initing main context ***** \n");
         Context cx = Context.currentContext();
         cx.setSource(Main.class.getName());
         cx.setReader(new InputStreamReader(System.in));
         cx.setWriter(new OutputStreamWriter(System.out));
         cx.configure(params);
         if (cx.getFolder() == null)
            cx.setFolder(new File(System.getProperty("user.dir")));
         for (String file : files) {
             Stream s = new Stream(file);
             Object result = s.read(null);
             if (result instanceof Throwable) {
                ((Throwable)result).printStackTrace();
                break;
             }
             else if(result instanceof CharSequence) {
                cx.getWriter().write(result.toString());
                cx.getWriter().flush();
             }
         }

         if (files.isEmpty())
            cx.console();
         cx.close();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }
}

