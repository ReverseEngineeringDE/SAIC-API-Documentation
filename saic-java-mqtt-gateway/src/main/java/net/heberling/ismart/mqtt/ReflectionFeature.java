package net.heberling.ismart.mqtt;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class ReflectionFeature implements Feature {
  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    register(net.heberling.ismart.asn1.v1_1.MP_DispatcherHeader.class);
    register(net.heberling.ismart.asn1.v3_0.MP_DispatcherBody.class);
    register(net.heberling.ismart.asn1.v1_1.entity.ContentId.class);
    register(net.heberling.ismart.asn1.v1_1.entity.AlarmSwitch.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MessageListReq.class);
    register(net.heberling.ismart.asn1.v1_1.entity.Message.class);
    register(net.heberling.ismart.asn1.v1_1.entity.AbortSendMessageReq.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MPUserInfoResp.class);
    register(net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusReq.class);
    register(net.heberling.ismart.asn1.v2_1.entity.RvsPosition.class);
    register(net.heberling.ismart.asn1.v1_1.entity.APPUpgradeInfoReq.class);
    register(net.heberling.ismart.asn1.v1_1.entity.Advertise.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MPAppAttributeResp.class);
    register(net.heberling.ismart.asn1.v2_1.MP_DispatcherHeader.class);
    register(net.heberling.ismart.asn1.v2_1.entity.SecurityAlarm.class);
    register(net.heberling.ismart.asn1.v2_1.entity.GPSStatus.class);
    register(net.heberling.ismart.asn1.v1_1.entity.SetNotificationCountReq.class);
    register(net.heberling.ismart.asn1.v2_1.entity.Timestamp4Short.class);
    register(net.heberling.ismart.asn1.v1_1.DataEncodingType.class);
    register(net.heberling.ismart.asn1.v2_1.DataEncodingType.class);
    register(net.heberling.ismart.asn1.v1_1.entity.Timestamp.class);
    register(net.heberling.ismart.asn1.v2_1.entity.RvsWGS84Point.class);
    register(net.heberling.ismart.asn1.v1_1.LanguageType.class);
    register(net.heberling.ismart.asn1.v1_1.MP_DispatcherBody.class);
    register(net.heberling.ismart.asn1.v1_1.entity.APPUpgradeInfoResp.class);
    register(net.heberling.ismart.asn1.v1_1.NetworkInfo.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MessageListResp.class);
    register(net.heberling.ismart.asn1.v1_1.entity.AlarmSwitchReq.class);
    register(net.heberling.ismart.asn1.v3_0.MP_DispatcherHeader.class);
    register(net.heberling.ismart.asn1.v1_1.entity.StartEndNumber.class);
    register(net.heberling.ismart.asn1.v2_1.MP_DispatcherBody.class);
    register(net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp.class);
    register(net.heberling.ismart.asn1.v3_0.DataEncodingType.class);
    register(net.heberling.ismart.asn1.v1_1.entity.VinInfo.class);
    register(net.heberling.ismart.asn1.v1_1.entity.AdvertiseResp.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInReq.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MP_AlarmSettingType.class);
    register(net.heberling.ismart.asn1.v1_1.MessageCounter.class);
    register(net.heberling.ismart.asn1.v1_1.BasicPosition.class);
    register(net.heberling.ismart.asn1.v3_0.entity.RvsChargingStatus.class);
    register(net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857.class);
    register(net.heberling.ismart.asn1.v2_1.entity.RvsWayPoint.class);
    register(net.heberling.ismart.asn1.v1_1.entity.GetUnreadMessageCountResp.class);
    register(net.heberling.ismart.asn1.v2_1.entity.MP_SecurityAlarmResp.class);
    register(net.heberling.ismart.asn1.v1_1.entity.APPType.class);
    register(net.heberling.ismart.asn1.v2_1.entity.RvsExtStatus.class);
    register(net.heberling.ismart.asn1.v1_1.entity.LanguageType.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInResp.class);
    register(net.heberling.ismart.asn1.v2_1.entity.VehicleAlertInfo.class);
    register(net.heberling.ismart.asn1.v2_1.entity.RvsBasicStatus25857.class);
    register(net.heberling.ismart.asn1.v1_1.entity.MP_AlarmSettingType.EnumType.class);
    register(net.heberling.ismart.asn1.v1_1.entity.APPType.EnumType.class);
    register(net.heberling.ismart.asn1.v2_1.entity.GPSStatus.EnumType.class);
    register(net.heberling.ismart.asn1.v1_1.DataEncodingType.EnumType.class);
    register(net.heberling.ismart.asn1.v1_1.LanguageType.EnumType.class);
    register(net.heberling.ismart.asn1.v3_0.DataEncodingType.EnumType.class);
    register(net.heberling.ismart.asn1.v1_1.entity.LanguageType.EnumType.class);
    register(net.heberling.ismart.asn1.v2_1.DataEncodingType.EnumType.class);
    //        Reflections reflections =
    //                new Reflections("net.heberling.ismart.asn1", new SubTypesScanner(false));
    //        reflections.getSubTypesOf(IASN1PreparedElement.class).stream()
    //                .peek(c -> System.out.println("register(" + c.getName() + ".class);"))
    //                .forEach(ReflectionFeature::register);
    //        reflections.getSubTypesOf(Enum.class).stream()
    //                .peek(c -> System.out.println("register(" + c.getName() + ".class);"))
    //                .forEach(ReflectionFeature::register);
  }

  public static void register(Class<?> clazz) {
    RuntimeReflection.register(clazz);
    try {
      clazz.getConstructor();
      RuntimeReflection.registerForReflectiveInstantiation(clazz);
    } catch (NoSuchMethodException e) {
      // ignore
    }
    RuntimeReflection.register(clazz.getDeclaredFields());
    RuntimeReflection.register(clazz.getDeclaredMethods());
  }
}
