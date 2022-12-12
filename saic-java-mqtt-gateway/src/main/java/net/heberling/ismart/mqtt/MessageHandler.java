package net.heberling.ismart.mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.heberling.ismart.asn1.v1_1.MP_DispatcherBody;
import net.heberling.ismart.asn1.v1_1.MP_DispatcherHeader;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import net.heberling.ismart.asn1.v1_1.MessageCounter;
import net.heberling.ismart.asn1.v1_1.entity.MessageListReq;
import net.heberling.ismart.asn1.v1_1.entity.MessageListResp;
import net.heberling.ismart.asn1.v1_1.entity.StartEndNumber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class MessageHandler implements Runnable {
    private final String uid;
    private final String token;
    private final SaicMqttGateway gateway;

    public MessageHandler(String uid, String token, SaicMqttGateway gateway) {
        this.uid = uid;
        this.token = token;
        this.gateway = gateway;
    }

    @Override
    public void run() {
        Message<MessageListReq> messageListRequestMessage =
                new Message<>(
                        new MP_DispatcherHeader(), new MP_DispatcherBody(), new MessageListReq());

        messageListRequestMessage.getHeader().setProtocolVersion(18);

        MessageCounter messageCounter = new MessageCounter();
        messageCounter.setDownlinkCounter(0);
        messageCounter.setUplinkCounter(1);
        messageListRequestMessage.getBody().setMessageCounter(messageCounter);

        messageListRequestMessage.getBody().setMessageID(1);
        messageListRequestMessage.getBody().setIccID("12345678901234567890");
        messageListRequestMessage.getBody().setSimInfo("1234567890987654321");
        messageListRequestMessage.getBody().setEventCreationTime(Instant.now().getEpochSecond());
        messageListRequestMessage.getBody().setApplicationID("531");
        messageListRequestMessage.getBody().setApplicationDataProtocolVersion(513);
        messageListRequestMessage.getBody().setTestFlag(2);

        messageListRequestMessage.getBody().setUid(uid);
        messageListRequestMessage.getBody().setToken(token);

        // We currently assume that the newest message is the first.
        // TODO: get all messages
        // TODO: delete old messages
        // TODO: handle case when no messages are there
        // TODO: automatically subscribe for engine start messages
        messageListRequestMessage.getApplicationData().setStartEndNumber(new StartEndNumber());
        messageListRequestMessage.getApplicationData().getStartEndNumber().setStartNumber(1L);
        messageListRequestMessage.getApplicationData().getStartEndNumber().setEndNumber(5L);
        messageListRequestMessage.getApplicationData().setMessageGroup("ALARM");

        String messageListRequest =
                new MessageCoder<>(MessageListReq.class).encodeRequest(messageListRequestMessage);

        try {
            String messageListResponse =
                    SaicMqttGateway.sendRequest(
                            messageListRequest, "https://tap-eu.soimt.com/TAP.Web/ota.mp");

            Message<MessageListResp> messageListResponseMessage =
                    new MessageCoder<>(MessageListResp.class).decodeResponse(messageListResponse);

            System.out.println(
                    SaicMqttGateway.toJSON(
                            SaicMqttGateway.anonymized(
                                    new MessageCoder<>(MessageListResp.class),
                                    messageListResponseMessage)));

            if (messageListResponseMessage.getApplicationData() != null) {
                for (net.heberling.ismart.asn1.v1_1.entity.Message message :
                        messageListResponseMessage.getApplicationData().getMessages()) {
                    MqttMessage msg =
                            new MqttMessage(
                                    SaicMqttGateway.toJSON(convert(message))
                                            .getBytes(StandardCharsets.UTF_8));

                    gateway.notifyMessage(convert(message));
                }
            } else {
                // logger.warn("No application data found!");
            }
        } catch (IOException | MqttException e) {
            throw new RuntimeException(e);
        }
    }

    private SaicMessage convert(net.heberling.ismart.asn1.v1_1.entity.Message message) {
        return new SaicMessage(
                message.getMessageId(),
                message.getMessageType(),
                new String(message.getTitle(), StandardCharsets.UTF_8),
                ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(message.getMessageTime().getSeconds()),
                        ZoneId.systemDefault()),
                new String(message.getSender(), StandardCharsets.UTF_8),
                new String(message.getContent(), StandardCharsets.UTF_8),
                message.getReadStatus(),
                message.getVin());
    }
}
