package net.heberling.ismart.asn1.v1_1;

import net.heberling.ismart.asn1.AbstractMessage;
import org.bn.coders.IASN1PreparedElement;

public class Message<E extends IASN1PreparedElement>
    extends AbstractMessage<MP_DispatcherHeader, MP_DispatcherBody, E> {

  Message(MP_DispatcherHeader header, MP_DispatcherBody body, E applicationData) {
    super(header, body, applicationData);
  }
}
