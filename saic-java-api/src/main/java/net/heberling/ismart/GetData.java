package net.heberling.ismart;

import com.owlike.genson.Context;
import com.owlike.genson.Converter;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.convert.ChainedFactory;
import com.owlike.genson.reflect.TypeUtil;
import com.owlike.genson.stream.JsonType;
import com.owlike.genson.stream.ObjectReader;
import com.owlike.genson.stream.ObjectWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;
import net.heberling.ismart.asn1.AbstractMessage;
import net.heberling.ismart.asn1.AbstractMessageCoder;
import net.heberling.ismart.asn1.Anonymizer;
import net.heberling.ismart.asn1.v1_1.MP_DispatcherBody;
import net.heberling.ismart.asn1.v1_1.MP_DispatcherHeader;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import net.heberling.ismart.asn1.v1_1.MessageCounter;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInReq;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInResp;
import net.heberling.ismart.asn1.v1_1.entity.VinInfo;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.bn.annotations.ASN1Enum;
import org.bn.annotations.ASN1Sequence;
import org.bn.coders.IASN1PreparedElement;

public class GetData {
    public static void main(String[] args) throws IOException {
        Message<MP_UserLoggingInReq> loginRequestMessage =
                new Message<>(
                        new MP_DispatcherHeader(),
                        new MP_DispatcherBody(),
                        new MP_UserLoggingInReq());

        MessageCounter messageCounter = new MessageCounter();
        messageCounter.setDownlinkCounter(0);
        messageCounter.setUplinkCounter(1);
        loginRequestMessage.getBody().setMessageCounter(messageCounter);

        loginRequestMessage.getBody().setMessageID(1);
        loginRequestMessage.getBody().setIccID("12345678901234567890");
        loginRequestMessage.getBody().setSimInfo("1234567890987654321");
        loginRequestMessage.getBody().setEventCreationTime(Instant.now().getEpochSecond());
        loginRequestMessage.getBody().setApplicationID("501");
        loginRequestMessage.getBody().setApplicationDataProtocolVersion(513);
        loginRequestMessage.getBody().setTestFlag(2);

        loginRequestMessage
                .getBody()
                .setUid(
                        "0000000000000000000000000000000000000000000000000#"
                                        .substring(args[0].length())
                                + args[0]);

        loginRequestMessage.getApplicationData().setPassword(args[1]);

        String loginRequest =
                new MessageCoder<>(MP_UserLoggingInReq.class).encodeRequest(loginRequestMessage);

        System.out.println(
                toJSON(
                        anonymized(
                                new MessageCoder<>(MP_UserLoggingInReq.class),
                                loginRequestMessage)));

        String loginResponse = sendRequest(loginRequest, "https://tap-eu.soimt.com/TAP.Web/ota.mp");

        Message<MP_UserLoggingInResp> loginResponseMessage =
                new MessageCoder<>(MP_UserLoggingInResp.class).decodeResponse(loginResponse);

        System.out.println(
                toJSON(
                        anonymized(
                                new MessageCoder<>(MP_UserLoggingInResp.class),
                                loginResponseMessage)));
        for (VinInfo vin : loginResponseMessage.getApplicationData().getVinList()) {
            net.heberling.ismart.asn1.v3_0.Message<IASN1PreparedElement> chargingStatusMessage =
                    new net.heberling.ismart.asn1.v3_0.Message<>(
                            new net.heberling.ismart.asn1.v3_0.MP_DispatcherHeader(),
                            new byte[16],
                            new net.heberling.ismart.asn1.v3_0.MP_DispatcherBody(),
                            null);
            fillReserved(chargingStatusMessage);

            chargingStatusMessage.getBody().setApplicationID("516");
            chargingStatusMessage.getBody().setTestFlag(2);
            chargingStatusMessage.getBody().setVin(vin.getVin());
            chargingStatusMessage.getBody().setUid(loginResponseMessage.getBody().getUid());
            chargingStatusMessage
                    .getBody()
                    .setToken(loginResponseMessage.getApplicationData().getToken());
            chargingStatusMessage.getBody().setMessageID(5);
            chargingStatusMessage
                    .getBody()
                    .setEventCreationTime((int) Instant.now().getEpochSecond());
            chargingStatusMessage.getBody().setApplicationDataProtocolVersion(768);
            chargingStatusMessage.getBody().setEventID(0);

            String chargingStatusRequestMessage =
                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class)
                            .encodeRequest(chargingStatusMessage);

            System.out.println(
                    toJSON(
                            anonymized(
                                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                            IASN1PreparedElement.class),
                                    chargingStatusMessage)));

            String chargingStatusResponse =
                    sendRequest(
                            chargingStatusRequestMessage,
                            "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

            net.heberling.ismart.asn1.v3_0.Message<OTA_ChrgMangDataResp>
                    chargingStatusResponseMessage =
                            new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                            OTA_ChrgMangDataResp.class)
                                    .decodeResponse(chargingStatusResponse);

            System.out.println(
                    toJSON(
                            anonymized(
                                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                            OTA_ChrgMangDataResp.class),
                                    chargingStatusResponseMessage)));

            // we get an eventId back...
            chargingStatusMessage
                    .getBody()
                    .setEventID(chargingStatusResponseMessage.getBody().getEventID());
            // ... use that to request the data again, until we have it
            // TODO: check for real errors (result!=0 and/or errorMessagePresent)
            while (chargingStatusResponseMessage.getApplicationData() == null) {

                fillReserved(chargingStatusMessage);

                System.out.println(
                        toJSON(
                                anonymized(
                                        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                                IASN1PreparedElement.class),
                                        chargingStatusMessage)));

                chargingStatusRequestMessage =
                        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                        IASN1PreparedElement.class)
                                .encodeRequest(chargingStatusMessage);

                chargingStatusResponse =
                        sendRequest(
                                chargingStatusRequestMessage,
                                "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

                chargingStatusResponseMessage =
                        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                        OTA_ChrgMangDataResp.class)
                                .decodeResponse(chargingStatusResponse);

                System.out.println(
                        toJSON(
                                anonymized(
                                        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                                OTA_ChrgMangDataResp.class),
                                        chargingStatusResponseMessage)));
            }
        }
    }

    private static <
                    H extends IASN1PreparedElement,
                    B extends IASN1PreparedElement,
                    E extends IASN1PreparedElement,
                    M extends AbstractMessage<H, B, E>>
            M anonymized(AbstractMessageCoder<H, B, E, M> coder, M message) {
        M messageCopy = coder.decodeResponse(coder.encodeRequest(message));
        Anonymizer.anonymize(messageCopy);
        return messageCopy;
    }

    private static void fillReserved(
            net.heberling.ismart.asn1.v3_0.Message<IASN1PreparedElement> chargingStatusMessage) {
        System.arraycopy(
                ((new Random(System.currentTimeMillis())).nextLong() + "1111111111111111")
                        .getBytes(),
                0,
                chargingStatusMessage.getReserved(),
                0,
                16);
    }

    private static String sendRequest(String request, String endpoint) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(endpoint);
            // Request parameters and other properties.
            httppost.setEntity(new StringEntity(request, ContentType.TEXT_HTML));

            // Execute and get the response.
            // Create a custom response handler
            HttpClientResponseHandler<String> responseHandler =
                    response -> {
                        final int status = response.getCode();
                        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                            final HttpEntity entity = response.getEntity();
                            try {
                                return entity != null ? EntityUtils.toString(entity) : null;
                            } catch (final ParseException ex) {
                                throw new ClientProtocolException(ex);
                            }
                        } else {
                            throw new ClientProtocolException(
                                    "Unexpected response status: " + status);
                        }
                    };
            return httpclient.execute(httppost, responseHandler);
        }
    }

    public static <
                    H extends IASN1PreparedElement,
                    B extends IASN1PreparedElement,
                    E extends IASN1PreparedElement,
                    M extends AbstractMessage<H, B, E>>
            String toJSON(M message) {
        // TODO: make sure this corresponds to the JER ASN.1 serialisation format
        final ChainedFactory chain =
                new ChainedFactory() {
                    @Override
                    protected Converter<?> create(
                            Type type, Genson genson, Converter<?> nextConverter) {
                        return new Converter<>() {
                            @Override
                            public void serialize(Object object, ObjectWriter writer, Context ctx)
                                    throws Exception {
                                if (object != null) {
                                    writer.beginNextObjectMetadata();
                                    if (object.getClass().isAnnotationPresent(ASN1Enum.class)) {
                                        writer.writeMetadata(
                                                "ASN1Type",
                                                object.getClass()
                                                        .getAnnotation(ASN1Enum.class)
                                                        .name());
                                    } else if (object.getClass()
                                            .isAnnotationPresent(ASN1Sequence.class)) {
                                        writer.writeMetadata(
                                                "ASN1Type",
                                                object.getClass()
                                                        .getAnnotation(ASN1Sequence.class)
                                                        .name());
                                    }
                                }

                                @SuppressWarnings("unchecked")
                                Converter<Object> n = (Converter<Object>) nextConverter;
                                if (!(writer instanceof MyObjectWriter)) {
                                    writer = new MyObjectWriter(writer);
                                }
                                n.serialize(object, writer, ctx);
                            }

                            @Override
                            public Object deserialize(ObjectReader reader, Context ctx)
                                    throws Exception {
                                return nextConverter.deserialize(reader, ctx);
                            }
                        };
                    }
                };
        chain.withNext(
                new ChainedFactory() {
                    @Override
                    protected Converter<?> create(
                            Type type, Genson genson, Converter<?> converter) {
                        final Class<?> clazz = TypeUtil.getRawClass(type);
                        if (clazz.isAnnotationPresent(ASN1Enum.class)) {

                            return new Converter<>() {
                                @Override
                                public void serialize(
                                        Object o, ObjectWriter objectWriter, Context context)
                                        throws Exception {
                                    Method getValue = clazz.getMethod("getValue");
                                    Object value = getValue.invoke(o);
                                    if (value == null) {
                                        objectWriter.writeNull();
                                    } else {
                                        objectWriter.writeString(String.valueOf(value));
                                    }
                                }

                                @Override
                                public Object deserialize(
                                        ObjectReader objectReader, Context context)
                                        throws Exception {
                                    throw new UnsupportedOperationException("not implemented yet");
                                }
                            };
                        } else {

                            return converter;
                        }
                    }
                });
        return new GensonBuilder()
                .useIndentation(true)
                .useRuntimeType(true)
                .exclude("preparedData")
                .withConverterFactory(chain)
                .create()
                .serialize(message);
    }

    private static class MyObjectWriter implements ObjectWriter {
        private final ObjectWriter delegate;

        private String utf8EncodedByteArrayName;

        private MyObjectWriter(ObjectWriter delegate) {
            this.delegate = delegate;
        }

        @Override
        public ObjectWriter beginArray() {
            return delegate.beginArray();
        }

        @Override
        public ObjectWriter endArray() {
            return delegate.endArray();
        }

        @Override
        public ObjectWriter beginObject() {
            return delegate.beginObject();
        }

        @Override
        public ObjectWriter endObject() {
            return delegate.endObject();
        }

        @Override
        public ObjectWriter writeName(String name) {
            return delegate.writeName(name);
        }

        @Override
        public ObjectWriter writeEscapedName(char[] name) {
            final String nameString = String.valueOf(name);
            if (nameString.equals("content")
                    || nameString.equals("brandName")
                    || nameString.equals("colorName")
                    || nameString.equals("modelName")
                    || nameString.equals("sender")
                    || nameString.equals("title")
                    || nameString.equals("errorMessage")) {
                // Some fields are really UTF8 strings, but the ASN.1 schema declares them as byte
                // arrays. We want to see the plain text additionally to the HEX String in the JSON
                utf8EncodedByteArrayName = "@" + nameString + "UTF8";
            }
            return delegate.writeEscapedName(name);
        }

        @Override
        public ObjectWriter writeValue(int value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeValue(double value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeValue(long value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeValue(short value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeValue(float value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeValue(boolean value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeBoolean(Boolean value) {
            return delegate.writeBoolean(value);
        }

        @Override
        public ObjectWriter writeValue(Number value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeNumber(Number value) {
            return delegate.writeNumber(value);
        }

        @Override
        public ObjectWriter writeValue(String value) {
            return delegate.writeValue(value);
        }

        @Override
        public ObjectWriter writeString(String value) {
            return delegate.writeString(value);
        }

        @Override
        public ObjectWriter writeValue(byte[] value) {
            final ObjectWriter writer = delegate.writeValue(value);
            if (utf8EncodedByteArrayName != null) {
                writer.writeEscapedName(utf8EncodedByteArrayName.toCharArray());
                writer.writeString(new String(value, StandardCharsets.UTF_8));
                utf8EncodedByteArrayName = null;
            }
            return writer;
        }

        @Override
        public ObjectWriter writeBytes(byte[] value) {
            return delegate.writeBytes(value);
        }

        @Override
        public ObjectWriter writeUnsafeValue(String value) {
            return delegate.writeUnsafeValue(value);
        }

        @Override
        public ObjectWriter writeNull() {
            utf8EncodedByteArrayName = null;
            return delegate.writeNull();
        }

        @Override
        public ObjectWriter beginNextObjectMetadata() {
            return delegate.beginNextObjectMetadata();
        }

        @Override
        public ObjectWriter writeMetadata(String name, String value) {
            return delegate.writeMetadata(name, value);
        }

        @Override
        public ObjectWriter writeBoolean(String name, Boolean value) {
            return delegate.writeBoolean(name, value);
        }

        @Override
        public ObjectWriter writeNumber(String name, Number value) {
            return delegate.writeNumber(name, value);
        }

        @Override
        public ObjectWriter writeString(String name, String value) {
            return delegate.writeString(name, value);
        }

        @Override
        public ObjectWriter writeBytes(String name, byte[] value) {
            return delegate.writeBytes(name, value);
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public JsonType enclosingType() {
            return delegate.enclosingType();
        }
    }
}
