package org.basex;

import static org.basex.core.Text.*;
import static org.basex.http.HTTPText.*;

import java.io.*;
import java.net.*;

import org.basex.core.*;
import org.basex.http.*;
import org.basex.util.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.nio.*;
import org.eclipse.jetty.xml.*;

/**
 * This is the main class for the starting the database HTTP services.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class BaseXHTTP {
  /** Database context. */
  final Context context = HTTPContext.init();

  /** Server/local mode. */
  private boolean server;
  /** Start as daemon. */
  private boolean service;
  /** Stopped flag. */
  private boolean stopped;
  /** HTTP server. */
  private Server jetty;
  /** Quiet flag. */
  private boolean quiet;

  /**
   * Main method, launching the HTTP services.
   * Command-line arguments are listed with the {@code -h} argument.
   * @param args command-line arguments
   */
  public static void main(final String... args) {
    try {
      new BaseXHTTP(args);
    } catch(final Exception ex) {
      Util.debug(ex);
      Util.errln(ex);
      System.exit(1);
    }
  }

  /**
   * Constructor.
   * @param args command-line arguments
   * @throws Exception exception
   */
  public BaseXHTTP(final String... args) throws Exception {
    parseArguments(args);

    // flag for starting a database server
    server = !LOCAL.equals(System.getProperty(DBMODE));

    final MainProp mprop = context.mprop;
    final int port = mprop.num(MainProp.SERVERPORT);
    final int eport = mprop.num(MainProp.EVENTPORT);

    /* [DK] Maybe this helps to specify the same HTTP path for all configurations.
     * Not sure, though, if this is still supported>
     * http://docs.codehaus.org/display/JETTY/SystemProperties */
    //System.setProperty("jetty.home", mprop.get(MainProp.HTTPPATH));

    /* [DK] We also need a solution what to do with HTTPPORT and STOPPORT.
     * They should both be placed both at the same location (ideally: in .basexjetty). */
    int hport = mprop.num(MainProp.HTTPPORT);
    final int sport = mprop.num(MainProp.STOPPORT);

    // create jetty instance
    jetty = (Server) new XmlConfiguration(JettyConfig.get()).configure();
    for(final Connector c : jetty.getConnectors()) {
      if(c instanceof SelectChannelConnector) {
        hport = c.getPort();
        break;
      }
    }

    // check if ports are distinct
    int same = -1;
    if(port == eport || port == hport || port == sport) same = port;
    else if(eport == hport || eport == sport) same = eport;
    else if(hport == sport) same = hport;
    if(same != -1) throw new BaseXException(PORT_TWICE_X, same);

    if(service) {
      start(hport, args);
      Util.outln(HTTP + ' ' + SRV_STARTED);
      if(server) Util.outln(SRV_STARTED);
      // keep the console window open for a while, so the user can read the message
      Performance.sleep(1000);
      return;
    }

    if(stopped) {
      stop();
      Util.outln(HTTP + ' ' + SRV_STOPPED);
      if(server) Util.outln(SRV_STOPPED);
      // keep the console window open for a while, so the user can read the message
      Performance.sleep(1000);
      return;
    }

    // request password on command line if only the user was specified
    if(System.getProperty(DBUSER) != null) {
      while(System.getProperty(DBPASS) == null) {
        Util.out(PASSWORD + COLS);
        System.setProperty(DBPASS, Util.password());
      }
    }

    if(server) {
      // default mode: start database server
      if(quiet) new BaseXServer(context, "-z");
      else new BaseXServer(context);
      Util.outln(HTTP + ' ' + SRV_STARTED);
    } else {
      // local or client mode
      Util.outln(CONSOLE + HTTP + ' ' + SRV_STARTED, SERVERMODE);
    }

    jetty.start();
    final String shost = mprop.get(MainProp.SERVERHOST);
    new StopServer(sport, shost).start();

    // show info when HTTP server is aborted
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        Util.outln(HTTP + ' ' + SRV_STOPPED);
        context.close();
      }
    });
  }

   /**
   * Stops the server.
   * @throws Exception exception
   */
  public void stop() throws Exception {
    // notify the jetty monitor that it should stop
    stop(context.mprop.num(MainProp.STOPPORT));
    // server has been started as separate process and need to be stopped
    if(server) {
      final int port = context.mprop.num(MainProp.SERVERPORT);
      final int eport = context.mprop.num(MainProp.EVENTPORT);
      BaseXServer.stop(port, eport);
    }
  }

  /**
   * Parses the command-line arguments, specified by the user.
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  private void parseArguments(final String[] args) throws IOException {
    final Args arg = new Args(args, this, HTTPINFO, Util.info(CONSOLE, HTTP));
    boolean daemon = false;
    while(arg.more()) {
      if(arg.dash()) {
        switch(arg.next()) {
          case 'd': // activate debug mode
            context.mprop.set(MainProp.DEBUG, true);
            break;
          case 'D': // hidden flag: daemon mode
            daemon = true;
            break;
          case 'e': // parse event port
            context.mprop.set(MainProp.EVENTPORT, arg.number());
            break;
          case 'h': // parse HTTP port
            context.mprop.set(MainProp.HTTPPORT, arg.number());
            break;
          case 'l': // use local mode
            System.setProperty(DBMODE, LOCAL);
            break;
          case 'n': // parse host name
            context.mprop.set(MainProp.HOST, arg.string());
            break;
          case 'p': // parse server port
            context.mprop.set(MainProp.PORT, arg.number());
            context.mprop.set(MainProp.SERVERPORT, context.mprop.num(MainProp.PORT));
            break;
          case 'P': // specify password
            System.setProperty(DBPASS, arg.string());
            break;
          case 's': // parse stop port
            context.mprop.set(MainProp.STOPPORT, arg.number());
            break;
          case 'S': // set service flag
            service = !daemon;
            break;
          case 'U': // specify user name
            System.setProperty(DBUSER, arg.string());
            break;
          case 'v': // specify user name
            System.setProperty(DBVERBOSE, Boolean.TRUE.toString());
            break;
          case 'z': // suppress logging
            quiet = true;
            break;
          default:
            arg.usage();
        }
      } else {
        if(!arg.string().equalsIgnoreCase("stop")) arg.usage();
        stopped = true;
      }
    }
  }

  // STATIC METHODS ===========================================================

  /**
   * Starts the HTTP server in a separate process.
   * @param port server port
   * @param args command-line arguments
   * @throws BaseXException database exception
   */
  private static void start(final int port, final String... args) throws BaseXException {
    // check if server is already running (needs some time)
    if(ping(LOCALHOST, port)) throw new BaseXException(SRV_RUNNING);

    Util.start(BaseXHTTP.class, args);

    // try to connect to the new server instance
    for(int c = 0; c < 10; ++c) {
      if(ping(LOCALHOST, port)) return;
      Performance.sleep(100);
    }
    throw new BaseXException(CONNECTION_ERROR);
  }

  /**
   * Generates a stop file for the specified port.
   * @param port server port
   * @return stop file
   */
  private static File stopFile(final int port) {
    return new File(Prop.TMP, Util.name(BaseXHTTP.class) + port);
  }

  /**
   * Stops the server.
   * @param port server port
   * @throws IOException I/O exception
   */
  private static void stop(final int port) throws IOException {
    final File stop = stopFile(port);
    try {
      stop.createNewFile();
      new Socket(LOCALHOST, port).close();
      // give the notified process some time to quit
      Performance.sleep(100);
    } catch(final IOException ex) {
      stop.delete();
      throw ex;
    }
  }

  /**
   * Checks if a server is running.
   * @param host host
   * @param port server port
   * @return boolean success
   */
  private static boolean ping(final String host, final int port) {
    try {
      // create connection
      final URL url = new URL("http://" + host + ':' + port);
      url.openConnection().getInputStream();
      return true;
    } catch(final IOException ex) {
      // if page is not found, server is running
      return ex instanceof FileNotFoundException;
    }
  }

  /** Monitor for stopping the Jetty server. */
  @SuppressWarnings("synthetic-access")
  private final class StopServer extends Thread {
    /** Server socket. */
    private final ServerSocket ss;
    /** Stop file. */
    private final File stop;

    /**
     * Constructor.
     * @param hport HTTP port
     * @param host host address
     * @throws IOException I/O exception
     */
    StopServer(final int hport, final String host) throws IOException {
      final InetAddress addr = host.isEmpty() ? null :
        InetAddress.getByName(host);
      ss = new ServerSocket();
      ss.setReuseAddress(true);
      ss.bind(new InetSocketAddress(addr, hport));
      stop = stopFile(hport);
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        while(true) {
          ss.accept().close();
          if(stop.exists()) {
            ss.close();
            stop.delete();
            jetty.stop();
            break;
          }
        }
      } catch(final Exception ex) {
        Util.errln(ex);
      }
    }
  }
}
