package net.heberling.ismart.asn1.v1_1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import net.heberling.ismart.asn1.AbstractMessageCoder;
import org.bn.coders.IASN1PreparedElement;
import org.bn.coders.per.PERUnalignedDecoder;

public class MessageCoder<E extends IASN1PreparedElement>
        extends AbstractMessageCoder<MP_DispatcherHeader, MP_DispatcherBody, E, Message<E>> {

    public MessageCoder(Class<E> applicationDataClass) {
        super(applicationDataClass);
    }

    @Override
    public String encodeRequest(Message<E> message) {

        E request = message.getApplicationData();

        try {
            ByteArrayOutputStream bos;
            byte[] applicationData;
            MyPERUnalignedEncoder encoder = new MyPERUnalignedEncoder();
            if (request != null) {
                bos = new ByteArrayOutputStream();
                encoder.encode(request, bos);
                applicationData = bos.toByteArray();
            } else {
                applicationData = new byte[0];
            }

            MP_DispatcherBody body = message.getBody();
            final DataEncodingType dataEncoding = new DataEncodingType();
            dataEncoding.setValue(DataEncodingType.EnumType.perUnaligned);
            body.setApplicationDataEncoding(dataEncoding);
            body.setApplicationDataLength((long) applicationData.length);

            bos = new ByteArrayOutputStream();
            encoder.encode(body, bos);
            final byte[] bodyData = bos.toByteArray();

            MP_DispatcherHeader header = message.getHeader();
            if (header.getProtocolVersion() == null) {
                header.setProtocolVersion(17);
            }
            if (header.getSecurityContext() == null) {
                header.setSecurityContext(0);
            }
            header.setDispatcherMessageLength(bodyData.length + 4 /*header length*/);
            header.setDispatcherBodyEncoding(0); // PER

            bos = new ByteArrayOutputStream();
            bos.write(header.getProtocolVersion());
            bos.write(header.getSecurityContext());
            bos.write(header.getDispatcherMessageLength());
            bos.write(header.getDispatcherBodyEncoding());

            bos.write(bodyData);

            bos.write(applicationData);

            byte[] bytes = bos.toByteArray();
            return String.format("%04X", bytes.length * 2 + 5) + "1" + bytesToHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Message<E> decodeResponse(String message) {
        try {
            // TODO: check for message encoding and length
            byte[] bytes = hexStringToByteArray(message.substring(5));
            InputStream inputStream = new ByteArrayInputStream(bytes);
            // not asn.1 encoded
            MP_DispatcherHeader header = new MP_DispatcherHeader();
            header.setProtocolVersion(inputStream.read());
            header.setSecurityContext(inputStream.read());
            header.setDispatcherMessageLength(inputStream.read());
            header.setDispatcherBodyEncoding(inputStream.read());

            byte[] b = new byte[header.getDispatcherMessageLength() - 4];
            inputStream.read(b);

            final PERUnalignedDecoder decoder = new MyPERUnalignedDecoder();
            MP_DispatcherBody body =
                    decoder.decode(new ByteArrayInputStream(b), MP_DispatcherBody.class);

            E e = null;
            if (body.getApplicationDataLength() > 0) {
                byte[] appData = new byte[body.getApplicationDataLength().intValue()];
                inputStream.read(appData);
                e = decoder.decode(new ByteArrayInputStream(appData), getApplicationDataClass());
            }
            return new Message<>(header, body, e);
        } catch (Exception e) {
            throw new RuntimeException("Could not decode: " + message, e);
        }
    }

    @Override
    public Message<E> initializeMessage(
            String uid,
            String token,
            String vin,
            String applicationID,
            int applicationDataProtocolVersion,
            int messageID,
            E applicationData) {
        Message<E> message =
                new Message<>(new MP_DispatcherHeader(), new MP_DispatcherBody(), applicationData);

        MessageCounter messageCounter = new MessageCounter();
        messageCounter.setDownlinkCounter(0);
        messageCounter.setUplinkCounter(1);
        message.getBody().setMessageCounter(messageCounter);

        message.getBody().setMessageID(messageID);
        message.getBody().setIccID("12345678901234567890");
        message.getBody().setSimInfo("1234567890987654321");
        message.getBody().setEventCreationTime(Instant.now().getEpochSecond());
        message.getBody().setApplicationID(applicationID);
        message.getBody().setApplicationDataProtocolVersion(applicationDataProtocolVersion);
        message.getBody().setTestFlag(2);
        message.getBody().setUid(uid);
        message.getBody().setToken(token);
        message.getBody().setVin(vin);
        message.getBody().setEventID(0L);

        return message;
    }

    @Override
    public String getVersion() {
        return "1.1";
    }
}
