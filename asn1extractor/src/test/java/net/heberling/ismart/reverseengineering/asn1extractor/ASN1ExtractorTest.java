package net.heberling.ismart.reverseengineering.asn1extractor;

import static org.junit.jupiter.api.Assertions.*;

import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.dispatcher.MP_DispatcherBody;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.MP_UserLoggingInReq;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.MP_UserLoggingInResp;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoReq;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoResp;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.MPAppAttributeResp;
import com.saicmotor.telematics.tsgp.otaadapter.mp.v1_2.entity.advertise.AdvertiseResp;
import java.util.List;
import org.junit.jupiter.api.Test;

class ASN1ExtractorTest {

    @Test
    void generateASN1Module() {
        String sequenceDefinition =
                ASN1Extractor.generateASN1Module(
                        "DispatcherBodyMP", List.of(MP_DispatcherBody.class));
        System.out.println(sequenceDefinition);

        Class<?> MPAppAttributeResp;
        sequenceDefinition =
                ASN1Extractor.generateASN1Module(
                        "ApplicationData",
                        List.of(
                                MP_UserLoggingInReq.class,
                                MP_UserLoggingInResp.class,
                                MPAppAttributeResp.class,
                                APPUpgradeInfoReq.class,
                                APPUpgradeInfoResp.class,
                                AdvertiseResp.class));
        System.out.println(sequenceDefinition);
    }
}
