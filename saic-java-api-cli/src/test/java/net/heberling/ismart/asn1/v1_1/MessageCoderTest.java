package net.heberling.ismart.asn1.v1_1;

import net.heberling.ismart.asn1.v1_1.entity.APPUpgradeInfoReq;
import net.heberling.ismart.asn1.v1_1.entity.APPUpgradeInfoResp;
import net.heberling.ismart.asn1.v1_1.entity.AbortSendMessageReq;
import net.heberling.ismart.asn1.v1_1.entity.AdvertiseResp;
import net.heberling.ismart.asn1.v1_1.entity.AlarmSwitchReq;
import net.heberling.ismart.asn1.v1_1.entity.GetUnreadMessageCountResp;
import net.heberling.ismart.asn1.v1_1.entity.MPAppAttributeResp;
import net.heberling.ismart.asn1.v1_1.entity.MPUserInfoResp;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInReq;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInResp;
import net.heberling.ismart.asn1.v1_1.entity.MessageListReq;
import net.heberling.ismart.asn1.v1_1.entity.MessageListResp;
import net.heberling.ismart.asn1.v1_1.entity.PINVerificationReq;
import net.heberling.ismart.asn1.v1_1.entity.SetNotificationCountReq;
import org.bn.coders.IASN1PreparedElement;
import org.junit.jupiter.api.Test;

class MessageCoderTest extends net.heberling.ismart.asn1.AbstractMessageCoderTest {

  @Test
  void decodeEncodeRequest_5D6() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("5D6_513_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_5D6() {
    decodeEncode("5D6_513_response", new MessageCoder<>(MPAppAttributeResp.class));
  }

  @Test
  void decodeEncodeRequest_5D5() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("5D5_513_request", new MessageCoder<>(APPUpgradeInfoReq.class));
  }

  @Test
  void decodeEncodeResponse_5D5() {
    decodeEncode("5D5_513_response", new MessageCoder<>(APPUpgradeInfoResp.class));
  }

  @Test
  void decodeEncodeRequest_5D7() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("5D7_513_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_5D7() {
    decodeEncode("5D7_513_response", new MessageCoder<>(AdvertiseResp.class));
  }

  @Test
  void decodeEncodeRequest_501() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("501_513_request", new MessageCoder<>(MP_UserLoggingInReq.class));
  }

  @Test
  void decodeEncodeResponse_501_not_registered() {
    // account not registered
    decodeEncode("501_513_response_not_registered", new MessageCoder<>(MP_UserLoggingInResp.class));
  }

  @Test
  void decodeEncodeResponse_501() {
    decodeEncode("501_513_response", new MessageCoder<>(MP_UserLoggingInResp.class));
  }

  @Test
  void decodeEncodeRequest_531() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("531_513_request", new MessageCoder<>(MessageListReq.class));
  }

  @Test
  void decodeEncodeResponse_531() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("531_513_response", new MessageCoder<>(MessageListResp.class));
  }

  @Test
  void decodeEncodeRequest_533() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("533_513_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_533() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("533_513_response", new MessageCoder<>(GetUnreadMessageCountResp.class));
  }

  @Test
  void decodeEncodeRequest_535() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("535_513_request", new MessageCoder<>(SetNotificationCountReq.class));
  }

  @Test
  void decodeEncodeResponse_535() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("535_513_response", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeRequest_506() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("506_513_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_506() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("506_513_response", new MessageCoder<>(MPUserInfoResp.class));
  }

  @Test
  void decodeEncodeRequest_521() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("521_513_request", new MessageCoder<>(AlarmSwitchReq.class));
  }

  @Test
  void decodeEncodeResponse_521() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("521_513_response", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeRequest_615() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("615_513_request", new MessageCoder<>(AbortSendMessageReq.class));
  }

  @Test
  void decodeEncodeResponse_615() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("615_513_response", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeRequest_313() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("313_513_request", new MessageCoder<>(PINVerificationReq.class));
  }

  @Test
  void decodeEncodeResponse_313() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mp
    decodeEncode("313_513_response", new MessageCoder<>(IASN1PreparedElement.class));
  }
}
