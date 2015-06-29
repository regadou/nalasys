package org.regadou.nalasys;

import java.io.*;
import java.util.*;
import java.net.*;

public class TcpServer extends Thread implements Closeable {

   public static TcpServer listen(String address) {
      TcpServer srv;
      try {
          String[] parts = (address.startsWith("tcp:") ? address.substring(4) : address).split(":");
          String host;
          int port;
          switch (parts.length) {
              case 1:
                  host = null;
                  port = Integer.parseInt(parts[0]);
                  break;
              case 2:
                  host = parts[0];
                  while (host.startsWith("/"))
                     host = host.substring(1);
                  port = Integer.parseInt(parts[1]);
                  break;
              default:
                  throw new RuntimeException("Invalid address "+address);
          }
          srv = new TcpServer(host, port);
          srv.start();
      }
      catch (Exception e) {
         srv = null;
         Context.exception(e, "TcpServer.listen("+address+")");
      }
      return srv;
   }

   public static void closeAll() {
      for (TcpServer srv : servers)
         srv.close();
      servers = new ArrayList<TcpServer>();
   }

   private static List<TcpServer> servers = new ArrayList<TcpServer>();
   private ServerSocket server = null;
   private Map<Socket,Context> clients = new LinkedHashMap<Socket,Context>();
   private long sleepTime = 1000;
   private boolean running = false;

   public TcpServer(int port) throws IOException {
      this(null, port);
   }

   public TcpServer(String host, int port) throws IOException {
      if (host == null)
         server = new ServerSocket(port);
      else
         server = new ServerSocket(port, 0, InetAddress.getByName(host));
      setName(getHost() + ":" + getPort());
      servers.add(this);
   }

   protected void finalize() throws Throwable {
      super.finalize();
      close();
   }

   public String toString() {
      return "[TcpServer "+getName()+"]";
   }

   public final String getHost() { return server.getInetAddress().getHostName(); }

   public final int getPort() { return server.getLocalPort(); }

   public synchronized boolean isRunning() { return running; }

   public synchronized long getSleepTime() { return sleepTime; }

   public synchronized void setSleepTime(long sleepTime) {
      this.sleepTime = sleepTime;
   }

   public void run() {
      if (running || server == null)
         return;
      synchronized (this ) { running = true; }

      while (isRunning()) {
         try {
            final Socket s = server.accept();
            if (s != null) {
               new Thread(new Runnable() {
                  public void run() { readClient(s); }
               }).start();
            }
         }
         catch (Exception e) {
	         e.printStackTrace();
         }
         try { sleep(getSleepTime()); }
         catch (Exception e) {
            synchronized (this) { running = false; }
         }
      }

      close();
   }

   public void open() {
      this.start();
   }

   public void close() {
      synchronized (this) { running = false; }
      if (server != null) {
         try { server.close(); }
         catch (Exception e) {}
         server = null;
      }
      Iterator iter = clients.keySet().iterator();
      while (iter.hasNext())
         close((Socket)iter.next());
   }

   private void close(Socket s) {
      try { s.close(); }
      catch (Exception e) {}
      Context cx = clients.remove(s);
      if (cx != null)
         cx.close();
   }

   private void readClient(Socket s) {
      Context cx = Context.currentContext();
      cx.setSource("tcp:"+s.getRemoteSocketAddress());
      try {
         clients.put(s, cx);
         cx.setReader(new InputStreamReader(s.getInputStream()));
         cx.setWriter(new OutputStreamWriter(s.getOutputStream()));
         cx.console();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
      finally { close(s); }
   }
}
