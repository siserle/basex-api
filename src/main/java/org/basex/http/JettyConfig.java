package org.basex.http;

import static org.basex.core.Prop.*;
import java.io.*;
import org.basex.io.*;
import org.basex.util.*;

/**
 * This class returns the configuration file for the Jetty web server.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class JettyConfig {
  /** Jetty web server properties file. */
  public static final String JETTYCONF = IO.BASEXSUFFIX + "jetty";

  /** Hidden constructor. */
  private JettyConfig() { }

  /**
   * Returns an input stream to the Jetty configuration file.
   * @return input stream
   * @throws IOException I/O exception
   */
  public static InputStream get() throws IOException {
    // try to locate file in home directory
    final IOFile file = new IOFile(HOME + JETTYCONF);
    if(!file.exists()) {
      // not found: get default configuration file and write it to home directory
      final InputStream is = JettyConfig.class.getResourceAsStream('/' + JETTYCONF);
      // unexpected: default file not found
      if(is == null) Util.notexpected(JETTYCONF + " not found.");

      // [DK] Version for testing:
      return is;

      /* Final version:
      // write default configuration to home directory
      file.write(new IOStream(is).read());
      */
    }
    return file.inputStream();
  }
}
