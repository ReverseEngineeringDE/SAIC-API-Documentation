package net.heberling.ismart.asn1;

import java.util.concurrent.ThreadLocalRandom;

public final class Util {
  private Util() {}

  public static byte[] generateReservedBytes() {
    byte[] reservedBytes = new byte[16];
    System.arraycopy(
        (ThreadLocalRandom.current().nextLong() + "1111111111111111").getBytes(),
        0,
        reservedBytes,
        0,
        16);
    return reservedBytes;
  }
}
