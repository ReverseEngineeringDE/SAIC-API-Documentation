#!/usr/bin/env sh

rm -rf "ASN.1 schema/v1_1"
mkdir -p "ASN.1 schema/v1_1"

asn1extractor/asn1extractor.sh MP_DispatcherHeaderModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.dispatcher.MP_DispatcherHeader \
  > "ASN.1 schema/v1_1/MP_DispatcherHeader.asn1"

asn1extractor/asn1extractor.sh MP_DispatcherBodyModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.dispatcher.MP_DispatcherBody \
  > "ASN.1 schema/v1_1/MP_DispatcherBody.asn1"

asn1extractor/asn1extractor.sh ApplicationDataModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.v2_1.MP_UserLoggingInReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.v2_1.MP_UserLoggingInResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.MPAppAttributeResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_2.entity.advertise.AdvertiseResp \
           > "ASN.1 schema/v1_1/ApplicationData.asn1"
