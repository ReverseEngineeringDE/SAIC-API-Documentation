package net.heberling.ismart.asn1.v3_0;

import static org.junit.jupiter.api.Assertions.*;

import net.heberling.ismart.asn1.AbstractMessageCoderTest;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import org.bn.coders.IASN1PreparedElement;
import org.junit.jupiter.api.Test;

class MessageCoderTest extends AbstractMessageCoderTest {

  @Test
  void decodeEncodeRequest_516() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mpv30
    decodeEncode("516_768_request", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_516() {
    decodeEncode("516_768_response", new MessageCoder<>(OTA_ChrgMangDataResp.class));
  }

  @Test
  void decodeEncodeRequest_516_with_eventid() {
    // https://tap-eu.soimt.com/TAP.Web/ota.mpv30
    decodeEncode("516_768_request_with_eventid", new MessageCoder<>(IASN1PreparedElement.class));
  }

  @Test
  void decodeEncodeResponse_516_with_eventid() {
    decodeEncode("516_768_response_with_eventid", new MessageCoder<>(OTA_ChrgMangDataResp.class));
  }
}
