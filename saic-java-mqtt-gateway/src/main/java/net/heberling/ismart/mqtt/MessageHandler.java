package net.heberling.ismart.mqtt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import net.heberling.ismart.asn1.v1_1.entity.MessageListReq;
import net.heberling.ismart.asn1.v1_1.entity.MessageListResp;
import net.heberling.ismart.asn1.v1_1.entity.StartEndNumber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageHandler implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageHandler.class);
  private final String uid;
  private final String token;
  private final SaicMqttGateway gateway;
  private final URI saicUri;

  public MessageHandler(URI saicUri, String uid, String token, SaicMqttGateway gateway) {
    this.saicUri = saicUri;
    this.uid = uid;
    this.token = token;
    this.gateway = gateway;
  }

  @Override
  public void run() {
    MessageCoder<MessageListReq> messageListRequestMessageCoder =
        new MessageCoder<>(MessageListReq.class);

    // We currently assume that the newest message is the first.
    // TODO: get all messages
    // TODO: delete old messages
    // TODO: handle case when no messages are there
    // TODO: automatically subscribe for engine start messages
    MessageListReq messageListReq = new MessageListReq();
    messageListReq.setStartEndNumber(new StartEndNumber());
    messageListReq.getStartEndNumber().setStartNumber(1L);
    messageListReq.getStartEndNumber().setEndNumber(5L);
    messageListReq.setMessageGroup("ALARM");

    Message<MessageListReq> messageListRequestMessage =
        messageListRequestMessageCoder.initializeMessage(
            uid, token, null, "531", 513, 1, messageListReq);

    messageListRequestMessage.getHeader().setProtocolVersion(18);

    String messageListRequest =
        messageListRequestMessageCoder.encodeRequest(messageListRequestMessage);

    try {
      String messageListResponse =
          SaicMqttGateway.sendRequest(messageListRequest, saicUri.resolve("/TAP.Web/ota.mp"));

      Message<MessageListResp> messageListResponseMessage =
          new MessageCoder<>(MessageListResp.class).decodeResponse(messageListResponse);

      LOGGER.debug(
          SaicMqttGateway.toJSON(
              SaicMqttGateway.anonymized(
                  new MessageCoder<>(MessageListResp.class), messageListResponseMessage)));

      if (messageListResponseMessage.getApplicationData() != null) {
        for (net.heberling.ismart.asn1.v1_1.entity.Message message :
            messageListResponseMessage.getApplicationData().getMessages()) {
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
            Instant.ofEpochSecond(message.getMessageTime().getSeconds()), ZoneId.systemDefault()),
        new String(message.getSender(), StandardCharsets.UTF_8),
        new String(message.getContent(), StandardCharsets.UTF_8),
        message.getReadStatus(),
        message.getVin());
  }
}
