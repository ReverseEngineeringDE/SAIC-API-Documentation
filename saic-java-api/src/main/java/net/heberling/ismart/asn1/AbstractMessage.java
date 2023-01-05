package net.heberling.ismart.asn1;

import org.bn.coders.IASN1PreparedElement;

public class AbstractMessage<
    H extends IASN1PreparedElement,
    B extends IASN1PreparedElement,
    E extends IASN1PreparedElement> {
  protected H header;
  protected B body;
  protected E applicationData;

  public AbstractMessage(H header, B body, E applicationData) {
    this.header = header;
    this.body = body;
    this.applicationData = applicationData;
  }

  public H getHeader() {
    return header;
  }

  public B getBody() {
    return body;
  }

  public E getApplicationData() {
    return applicationData;
  }
}
