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
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.alarm.AlarmSwitchReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.MPUserInfoResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.v2_1.MP_UserLoggingInReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.login.v2_1.MP_UserLoggingInResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.APPUpgradeInfoResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.software.MPAppAttributeResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_2.entity.advertise.AdvertiseResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.message.AbortSendMessageReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_2.entity.message.MessageListReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_2.entity.message.MessageListResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.message.GetUnreadMessageCountResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.message.v2_0.SetNotificationCountReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v1_1.entity.vehicle.v2_0.PINVerificationReq \
           > "ASN.1 schema/v1_1/ApplicationData.asn1"

rm -rf "ASN.1 schema/v2_1"
mkdir -p "ASN.1 schema/v2_1"

asn1extractor/asn1extractor.sh MP_DispatcherHeaderModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v2_1.entity.dispatcher.MP_DispatcherHeader \
  > "ASN.1 schema/v2_1/MP_DispatcherHeader.asn1"

asn1extractor/asn1extractor.sh MP_DispatcherBodyModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v2_1.entity.dispatcher.MP_DispatcherBody \
  > "ASN.1 schema/v2_1/MP_DispatcherBody.asn1"

asn1extractor/asn1extractor.sh ApplicationDataModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v2_1.entity.alarm.MP_SecurityAlarmResp \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v2_1.entity.vehicle.OTA_RVMVehicleStatusReq \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v2_1.entity.vehicle.OTA_RVMVehicleStatusResp25857 \
           > "ASN.1 schema/v2_1/ApplicationData.asn1"

rm -rf "ASN.1 schema/v3_0"
mkdir -p "ASN.1 schema/v3_0"

asn1extractor/asn1extractor.sh MP_DispatcherHeaderModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v3_0.entity.dispatcher.MP_DispatcherHeader \
  > "ASN.1 schema/v3_0/MP_DispatcherHeader.asn1"

asn1extractor/asn1extractor.sh MP_DispatcherBodyModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v3_0.entity.dispatcher.MP_DispatcherBody \
  > "ASN.1 schema/v3_0/MP_DispatcherBody.asn1"

asn1extractor/asn1extractor.sh ApplicationDataModule \
  com.saicmotor.telematics.tsgp.otaadapter.mp.v3_0.entity.charging.OTA_ChrgMangDataResp \
  > "ASN.1 schema/v3_0/ApplicationData.asn1"
