package net.heberling.ismart.mqtt;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class TestUtils {
  public static File getProjectPath() {
    try {
      URI uri =
          Class.forName(Thread.currentThread().getStackTrace()[2].getClassName())
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .toURI();

      File start = new File(uri).getParentFile();

      while (!new File(start, "pom.xml").exists()) {
        start = start.getParentFile();
      }

      return start;
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
