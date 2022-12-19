package net.heberling.ismart.asn1.v2_1;

import net.heberling.ismart.asn1.AbstractMessage;
import org.bn.coders.IASN1PreparedElement;

public class Message<E extends IASN1PreparedElement>
        extends AbstractMessage<MP_DispatcherHeader, MP_DispatcherBody, E> {

    private final byte[] reserved;

    Message(
            MP_DispatcherHeader header,
            byte[] reserved,
            MP_DispatcherBody body,
            E applicationData) {
        super(header, body, applicationData);
        this.reserved = reserved;
    }

    public byte[] getReserved() {
        return reserved;
    }
}
