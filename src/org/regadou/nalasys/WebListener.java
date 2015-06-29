package org.regadou.nalasys;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

public class WebListener implements ServletContextListener, ServletRequestListener {

   public static final String sessionContextParam = "org.regadou.nalasys.session";
   public static final boolean keepContextSession = false;

   public void contextInitialized(ServletContextEvent sce) {
        ServletContext scx = sce.getServletContext();
        Enumeration e = scx.getInitParameterNames();
        Map params = new LinkedHashMap();
        while (e.hasMoreElements()) {
            String name = e.nextElement().toString();
            params.put(name, scx.getInitParameter(name));
        }
        Context.init(params);
        System.err.println(logDate()+": Nalasys web context for "+scx.getContextPath()+" initialized with these parameters: "+params);
   }

   public void contextDestroyed(ServletContextEvent sce) {
      TcpServer.closeAll();
      System.err.println(logDate()+": Nalasys web context is shuting down");
   }

   public void requestInitialized(ServletRequestEvent sre) {
      HttpServletRequest request = (HttpServletRequest)sre.getServletRequest();
      String url = request.getRequestURL().toString();
      String query = request.getQueryString();
      if (query != null && !query.equals(""))
         url += "?" + query;
      String src = request.getRemoteHost()+":"+request.getRemotePort();
      Context cx = Context.currentContext(!keepContextSession);
      if (cx == null) {
         HttpSession session = request.getSession(false);
         if (session != null) {
            cx = (Context)session.getAttribute(sessionContextParam);
            if (cx != null) {
               Context.currentContext(cx);
            }
         }
         if (cx == null) {
            cx = setSession(request);
         }
      }
      cx.setSource(src);
      if (cx.getFolder() == null)
         cx.setFolder(new File(request.getServletContext().getRealPath("")));
      System.err.println(logDate()+": request started from "+src+" to "+url);
   }

   public void requestDestroyed(ServletRequestEvent sre) {
      Context cx = (Context) Context.currentContext(false);
      if (cx != null && (!cx.isIdentified() || !keepContextSession)) {
         cx.close();
         HttpSession session = ((HttpServletRequest)sre.getServletRequest()).getSession(false);
         if (session != null)
            session.removeAttribute(sessionContextParam);
      }
      HttpServletRequest request = (HttpServletRequest)sre.getServletRequest();
      System.err.println(logDate()+": request ended for "+request.getRemoteHost()+":"+request.getRemotePort());
   }

   private String logDate() {
      return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
   }

   protected static Context setSession(HttpServletRequest request) {
      Context cx = Context.currentContext();
      if (keepContextSession && request != null) {
         HttpSession session = request.getSession();
         session.setAttribute(sessionContextParam, cx);
         cx.setOwner(session);
      }
      return cx;
   }
}
