package org.regadou.nalasys;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class LoginFilter implements Filter {

	FilterConfig filterConfig = null;

   public void init(FilterConfig filterConfig) throws ServletException {
		this.filterConfig = filterConfig;
	}

	public void destroy() {}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

      LoginResponse login = checkLogin((HttpServletRequest)request);
      if (login != null) {
         request.setAttribute("login.status", login.status);
         request.setAttribute("login.message", login.message);
         RequestDispatcher dispatcher = request.getRequestDispatcher(login.uri);
         dispatcher.forward(request, response);
      }
      else
         chain.doFilter(request, response);
	}

   private LoginResponse checkLogin(HttpServletRequest request) {
      if (request == null)
         return new LoginResponse(401, "Authentication required", errorUri());
      else if (WebListener.keepContextSession) {
         HttpSession session = request.getSession(false);
         if (session != null) {
            Context cx = (Context) session.getAttribute(WebListener.sessionContextParam);
            if (cx != null && cx.isIdentified()) {
               Context oldcx = Context.currentContext(false);
               if (oldcx == null || oldcx != cx) {
                  Context.closeCurrentContext();
                  Context.currentContext(cx);
               }
               return null;
            }
         }
      }
      String auth = request.getHeader("authorization");
      if (auth == null) {
         Context cx = Context.currentContext(false);
         if (cx != null && cx.isIdentified())
            return null;
         else {
            try {
               Map params = Servlet.getParameters(request);
               Object user = params.get("user");
               Object password = params.get("password");
               if (user != null && password != null && Context.login(user.toString(), password.toString())) {
                  WebListener.setSession(request);
                  return null;
               }
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
         return new LoginResponse(401, "Authentication required", errorUri());
      }
      else {
         String parts[] = auth.split(" ");
         if (parts.length < 2)
            return new LoginResponse(401, "Authentication received has no specified mode", errorUri());
         else {
            String mode = parts[0].toUpperCase();
            String credential[] = new String(javax.xml.bind.DatatypeConverter.parseBase64Binary(parts[1])).split(":");
            String name = (credential.length > 0) ? credential[0] : "";
            String pass = (credential.length > 1) ? credential[1] : "";
            if (!mode.equals("BASIC"))
               return new LoginResponse(401, mode+" mode not supported", errorUri());
            else if (name == null)
               return new LoginResponse(401, "Forbidden access", errorUri());
            else if (Context.login(name, pass)) {
               WebListener.setSession(request);
               return null;
            }
            else {
               if (!name.equals(""))
                  name = " for "+name;
               return new LoginResponse(401, "Forbidden access"+name, errorUri());
            }
         }
      }
   }

   private String errorUri() {
      return filterConfig.getInitParameter("error");
   }
}

class LoginResponse {
   public int status;
   public String message;
   public String uri;
   public LoginResponse(int s, String m, String u) {
      status = s;
      message = m;
      uri = u;
   }
}
