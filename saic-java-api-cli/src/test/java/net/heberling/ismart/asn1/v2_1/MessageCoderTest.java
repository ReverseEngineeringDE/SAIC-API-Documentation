package net.heberling.ismart.asn1.v2_1;

import net.heberling.ismart.asn1.AbstractMessageCoderTest;
import net.heberling.ismart.asn1.v2_1.entity.MP_SecurityAlarmResp;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVCReq;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVCStatus25857;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusReq;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857;
import org.bn.coders.IASN1PreparedElement;
import org.junit.jupiter.api.Test;

class MessageCoderTest extends AbstractMessageCoderTest {

  @Test
  void decodeEncodeRequest_5BD() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mpv21

    decodeEncode("5BD_25857_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_5BD() {

    decodeEncode("5BD_25857_response", new MessageCoder<>(MP_SecurityAlarmResp.class));
  }

  @Test
  void decodeEncodeRequest_511() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mpv21

    decodeEncode("511_25857_request", new MessageCoder<>(OTA_RVMVehicleStatusReq.class));
  }

  @Test
  void decodeEncodeResponse_511_Failed() {
    // failed!

    decodeEncode(
        "511_25857_response_failed", new MessageCoder<>(OTA_RVMVehicleStatusResp25857.class));
  }

  @Test
  void decodeEncodeResponse_511() {

    decodeEncode("511_25857_response", new MessageCoder<>(OTA_RVMVehicleStatusResp25857.class));
  }

  @Test
  void decodeEncodeRequest_510() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mpv21
    decodeEncode("510_25857_request", new MessageCoder<>(OTA_RVCReq.class));
  }

  @Test
  void decodeEncodeResponse_510() {
    decodeEncode("510_25857_response", new MessageCoder<>(OTA_RVCStatus25857.class));
  }
}
