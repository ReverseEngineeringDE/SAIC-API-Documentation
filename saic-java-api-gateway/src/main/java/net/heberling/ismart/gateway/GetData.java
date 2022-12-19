package net.heberling.ismart.gateway;

import com.owlike.genson.Context;
import com.owlike.genson.Converter;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.convert.ChainedFactory;
import com.owlike.genson.reflect.TypeUtil;
import com.owlike.genson.stream.ObjectReader;
import com.owlike.genson.stream.ObjectWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Random;
import net.heberling.ismart.asn1.AbstractMessage;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInReq;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInResp;
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
import org.bn.coders.IASN1PreparedElement;

public class GetData {
    public static String[] startResponse(String[] args) throws IOException {

        String[] jsonOuput = new String[4];

        MessageCoder<MP_UserLoggingInReq> loginRequestMessageCoder =
                new MessageCoder<>(MP_UserLoggingInReq.class);

        MP_UserLoggingInReq applicationData = new MP_UserLoggingInReq();
        applicationData.setPassword(args[1]);
        Message<MP_UserLoggingInReq> loginRequestMessage =
                loginRequestMessageCoder.initializeMessage(
                        "0000000000000000000000000000000000000000000000000#"
                                        .substring(args[0].length())
                                + args[0],
                        null,
                        null,
                        "501",
                        513,
                        1,
                        applicationData);
        String loginRequest = loginRequestMessageCoder.encodeRequest(loginRequestMessage);

        // System.out.println(toJSON(loginRequestMessage));

        jsonOuput[0] = toJSON(loginRequestMessage);

        System.out.println("Sending login request...");
        String loginResponse = sendRequest(loginRequest, "https://tap-eu.soimt.com/TAP.Web/ota.mp");

        Message<MP_UserLoggingInResp> loginResponseMessage =
                new MessageCoder<>(MP_UserLoggingInResp.class).decodeResponse(loginResponse);

        // System.out.println(loginResponse);
        // System.out.println(toJSON(loginResponseMessage));

        // System.out.println(loginResponseMessage.getBody().getUid());
        // System.out.println(loginResponseMessage.getBody().getToken());
        // System.out.println(loginResponseMessage.getApplicationData().getToken());

        net.heberling.ismart.asn1.v3_0.MessageCoder<IASN1PreparedElement>
                chargingStatusRequestMessageEncoder =
                        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                IASN1PreparedElement.class);

        net.heberling.ismart.asn1.v3_0.Message<IASN1PreparedElement> chargingStatusMessage =
                chargingStatusRequestMessageEncoder.initializeMessage(
                        loginResponseMessage.getBody().getUid(),
                        loginResponseMessage.getApplicationData().getToken(),
                        loginResponseMessage.getApplicationData().getVinList().stream()
                                .findFirst()
                                .get()
                                .getVin(),
                        "516",
                        768,
                        5,
                        null);

        String chargingStatusRequestMessage =
                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class)
                        .encodeRequest(chargingStatusMessage);

        // System.out.println(toJSON(chargingStatusMessage));
        System.out.println("Sending initial chargingStatusRequestMessage to wake the car...");

        String chargingStatusResponse =
                sendRequest(
                        chargingStatusRequestMessage, "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

        net.heberling.ismart.asn1.v3_0.Message<OTA_ChrgMangDataResp> chargingStatusResponseMessage =
                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
                        .decodeResponse(chargingStatusResponse);

        // System.out.println(chargingStatusResponse);
        // System.out.println(toJSON(chargingStatusResponseMessage));

        jsonOuput[1] = toJSON(chargingStatusResponseMessage);

        // we get an eventId back...
        chargingStatusMessage
                .getBody()
                .setEventID(chargingStatusResponseMessage.getBody().getEventID());
        // ... use that to request the data again, until we have it
        // TODO: check for real errors (result!=0 and/or errorMessagePresent)
        while (chargingStatusResponseMessage.getApplicationData() == null) {

            try {
                System.out.println(
                        "Waiting for 6 seconds until the car woke up and responded to our"
                                + " request.");
                Thread.sleep(6000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            fillReserved(chargingStatusMessage);

            // System.out.println(toJSON(chargingStatusMessage));

            jsonOuput[2] = toJSON(chargingStatusMessage);

            chargingStatusRequestMessage =
                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class)
                            .encodeRequest(chargingStatusMessage);

            chargingStatusResponse =
                    sendRequest(
                            chargingStatusRequestMessage,
                            "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

            chargingStatusResponseMessage =
                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
                            .decodeResponse(chargingStatusResponse);

            // System.out.println(chargingStatusResponse);
            // System.out.println(toJSON(chargingStatusResponseMessage));

            jsonOuput[3] = toJSON(chargingStatusResponseMessage);
        }
        System.out.println("We got a response.");
        return jsonOuput;
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
        String json =
                new GensonBuilder()
                        .useIndentation(true)
                        .useRuntimeType(true)
                        .exclude("preparedData")
                        .withConverterFactory(
                                new ChainedFactory() {
                                    @Override
                                    protected Converter<?> create(
                                            Type type, Genson genson, Converter<?> converter) {
                                        final Class<?> clazz = TypeUtil.getRawClass(type);
                                        if (clazz.isAnnotationPresent(ASN1Enum.class)) {

                                            return new Converter<>() {
                                                @Override
                                                public void serialize(
                                                        Object o,
                                                        ObjectWriter objectWriter,
                                                        Context context)
                                                        throws Exception {
                                                    Method getValue = clazz.getMethod("getValue");
                                                    Object value = getValue.invoke(o);
                                                    if (value == null) {
                                                        objectWriter.writeNull();
                                                    } else {
                                                        objectWriter.writeString(
                                                                String.valueOf(value));
                                                    }
                                                }

                                                @Override
                                                public Object deserialize(
                                                        ObjectReader objectReader, Context context)
                                                        throws Exception {
                                                    throw new UnsupportedOperationException(
                                                            "not implemented yet");
                                                }
                                            };
                                        } else {

                                            return converter;
                                        }
                                    }
                                })
                        .create()
                        .serialize(message);
        return json;
    }
}
