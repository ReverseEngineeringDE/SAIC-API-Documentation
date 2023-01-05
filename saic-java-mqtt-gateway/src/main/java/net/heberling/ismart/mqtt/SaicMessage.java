package net.heberling.ismart.mqtt;

import java.time.ZonedDateTime;

public class SaicMessage {

  private final Long messageId;

  private final String messageType;

  private final String title;

  private final ZonedDateTime messageTime;

  private final String sender;

  // private java.util.Collection<ContentId>  contentId = null;

  private final String content;

  private final Integer readStatus;

  private final String vin;

  public SaicMessage(
      Long messageId,
      String messageType,
      String title,
      ZonedDateTime messageTime,
      String sender,
      String content,
      Integer readStatus,
      String vin) {
    this.messageId = messageId;
    this.messageType = messageType;
    this.title = title;
    this.messageTime = messageTime;
    this.sender = sender;
    this.content = content;
    this.readStatus = readStatus;
    this.vin = vin;
  }

  public Long getMessageId() {
    return messageId;
  }

  public String getMessageType() {
    return messageType;
  }

  public String getTitle() {
    return title;
  }

  public ZonedDateTime getMessageTime() {
    return messageTime;
  }

  public String getSender() {
    return sender;
  }

  public String getContent() {
    return content;
  }

  public Integer getReadStatus() {
    return readStatus;
  }

  public String getVin() {
    return vin;
  }
}
