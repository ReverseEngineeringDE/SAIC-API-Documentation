package net.heberling.ismart.mqtt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import net.heberling.ismart.abrp.ABRP;
import net.heberling.ismart.asn1.v1_1.entity.VinInfo;
import net.heberling.ismart.asn1.v2_1.MessageCoder;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusReq;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import org.bn.coders.IASN1PreparedElement;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(VehicleHandler.class);
  private final URI saicUri;
  private final String uid;
  private final String token;
  private final VinInfo vinInfo;
  private final SaicMqttGateway saicMqttGateway;
  private final IMqttClient client;
  private final String mqttVINPrefix;
  private ZonedDateTime lastCarActivity;
  private ZonedDateTime lastVehicleMessage;

  public VehicleHandler(
      SaicMqttGateway saicMqttGateway,
      IMqttClient client,
      URI saicUri,
      String uid,
      String token,
      String mqttAccountPrefix,
      VinInfo vinInfo) {

    this.saicMqttGateway = saicMqttGateway;
    this.client = client;
    this.saicUri = saicUri;
    this.uid = uid;
    this.token = token;
    this.mqttVINPrefix = mqttAccountPrefix + "/vehicles/" + vinInfo.getVin();
    this.vinInfo = vinInfo;
  }

  void handleVehicle() throws MqttException, IOException {
    MqttMessage msg =
        new MqttMessage(vinInfo.getModelConfigurationJsonStr().getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/_internal/configuration/raw", msg);
    for (String c : vinInfo.getModelConfigurationJsonStr().split(";")) {
      Map<String, String> map = new HashMap<>();
      for (String e : c.split(",")) {
        map.put(e.split(":")[0], e.split(":")[1]);
      }
      msg = new MqttMessage(map.get("value").getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/info/configuration/" + map.get("code"), msg);
    }
    // we just got started, force some updates
    notifyCarActivity(ZonedDateTime.now(), true);
    while (true) {
      if (lastCarActivity.isAfter(ZonedDateTime.now().minus(15, ChronoUnit.MINUTES))) {
        OTA_RVMVehicleStatusResp25857 vehicleStatus =
            updateVehicleStatus(client, uid, token, vinInfo.getVin());
        OTA_ChrgMangDataResp chargeStatus =
            updateChargeStatus(client, uid, token, vinInfo.getVin());

        final String abrpApiKey = saicMqttGateway.getAbrpApiKey();
        final String abrpUserToken = saicMqttGateway.getAbrpUserToken(vinInfo.getVin());
        if (abrpApiKey != null
            && abrpUserToken != null
            && vehicleStatus != null
            && chargeStatus != null) {
          String abrpResponse =
              ABRP.updateAbrp(abrpApiKey, abrpUserToken, vehicleStatus, chargeStatus);
          msg = new MqttMessage(abrpResponse.getBytes(StandardCharsets.UTF_8));
          msg.setQos(0);
          msg.setRetained(true);
          client.publish(mqttVINPrefix + "/_internal/abrp", msg);
        }
      } else {
        try {
          // car not active, wait a second
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private OTA_RVMVehicleStatusResp25857 updateVehicleStatus(
      IMqttClient client, String uid, String token, String vin) throws IOException, MqttException {
    MessageCoder<OTA_RVMVehicleStatusReq> otaRvmVehicleStatusReqMessageCoder =
        new MessageCoder<>(OTA_RVMVehicleStatusReq.class);

    OTA_RVMVehicleStatusReq otaRvmVehicleStatusReq = new OTA_RVMVehicleStatusReq();
    otaRvmVehicleStatusReq.setVehStatusReqType(2);
    net.heberling.ismart.asn1.v2_1.Message<OTA_RVMVehicleStatusReq> vehicleStatusRequestMessage =
        otaRvmVehicleStatusReqMessageCoder.initializeMessage(
            uid, token, vin, "511", 25857, 1, otaRvmVehicleStatusReq);

    String vehicleStatusRequest =
        otaRvmVehicleStatusReqMessageCoder.encodeRequest(vehicleStatusRequestMessage);

    String vehicleStatusResponse =
        SaicMqttGateway.sendRequest(vehicleStatusRequest, saicUri.resolve("/TAP.Web/ota.mpv21"));

    net.heberling.ismart.asn1.v2_1.Message<OTA_RVMVehicleStatusResp25857>
        vehicleStatusResponseMessage =
            new net.heberling.ismart.asn1.v2_1.MessageCoder<>(OTA_RVMVehicleStatusResp25857.class)
                .decodeResponse(vehicleStatusResponse);

    // we get an eventId back...
    vehicleStatusRequestMessage
        .getBody()
        .setEventID(vehicleStatusResponseMessage.getBody().getEventID());
    // ... use that to request the data again, until we have it
    // TODO: check for real errors (result!=0 and/or errorMessagePresent)
    while (vehicleStatusResponseMessage.getApplicationData() == null) {

      if (vehicleStatusResponseMessage.getBody().isErrorMessagePresent()) {
        if (vehicleStatusResponseMessage.getBody().getResult() == 2) {
          // TODO: relogn
        }
        // try again next time
        return null;
      }

      vehicleStatusRequestMessage.getBody().setUid(uid);
      vehicleStatusRequestMessage.getBody().setToken(token);

      SaicMqttGateway.fillReserved(vehicleStatusRequestMessage.getReserved());

      vehicleStatusRequest =
          otaRvmVehicleStatusReqMessageCoder.encodeRequest(vehicleStatusRequestMessage);

      vehicleStatusResponse =
          SaicMqttGateway.sendRequest(vehicleStatusRequest, saicUri.resolve("/TAP.Web/ota.mpv21"));

      vehicleStatusResponseMessage =
          new net.heberling.ismart.asn1.v2_1.MessageCoder<>(OTA_RVMVehicleStatusResp25857.class)
              .decodeResponse(vehicleStatusResponse);

      LOGGER.debug(
          SaicMqttGateway.toJSON(
              SaicMqttGateway.anonymized(
                  new net.heberling.ismart.asn1.v2_1.MessageCoder<>(
                      OTA_RVMVehicleStatusResp25857.class),
                  vehicleStatusResponseMessage)));
    }

    boolean engineRunning =
        vehicleStatusResponseMessage.getApplicationData().getBasicVehicleStatus().getEngineStatus()
            == 1;
    boolean isCharging =
        vehicleStatusResponseMessage
                .getApplicationData()
                .getBasicVehicleStatus()
                .isExtendedData2Present()
            && vehicleStatusResponseMessage
                    .getApplicationData()
                    .getBasicVehicleStatus()
                    .getExtendedData2()
                >= 1;

    if (isCharging
        || engineRunning
        || vehicleStatusResponseMessage
                .getApplicationData()
                .getBasicVehicleStatus()
                .getRemoteClimateStatus()
            > 0) {
      notifyCarActivity(ZonedDateTime.now(), false);
    }

    MqttMessage msg = new MqttMessage(vehicleStatusResponse.getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(
        mqttVINPrefix
            + "/_internal/"
            + vehicleStatusResponseMessage.getBody().getApplicationID()
            + "_"
            + vehicleStatusResponseMessage.getBody().getApplicationDataProtocolVersion()
            + "/raw",
        msg);

    msg =
        new MqttMessage(
            SaicMqttGateway.toJSON(vehicleStatusResponseMessage).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(
        mqttVINPrefix
            + "/_internal/"
            + vehicleStatusResponseMessage.getBody().getApplicationID()
            + "_"
            + vehicleStatusResponseMessage.getBody().getApplicationDataProtocolVersion()
            + "/json",
        msg);

    msg = new MqttMessage(String.valueOf(engineRunning).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/running", msg);

    msg = new MqttMessage(String.valueOf(isCharging).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/charging", msg);

    Integer interiorTemperature =
        vehicleStatusResponseMessage
            .getApplicationData()
            .getBasicVehicleStatus()
            .getInteriorTemperature();
    if (interiorTemperature > -128) {
      msg = new MqttMessage(String.valueOf(interiorTemperature).getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/climate/interiorTemperature", msg);
    }

    Integer exteriorTemperature =
        vehicleStatusResponseMessage
            .getApplicationData()
            .getBasicVehicleStatus()
            .getExteriorTemperature();
    if (exteriorTemperature > -128) {
      msg = new MqttMessage(String.valueOf(exteriorTemperature).getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/climate/exteriorTemperature", msg);
    }

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                            .getApplicationData()
                            .getBasicVehicleStatus()
                            .getBatteryVoltage()
                        / 10.d)
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/auxillaryBatteryVoltage", msg);

    msg =
        new MqttMessage(
            SaicMqttGateway.toJSON(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getGpsPosition()
                        .getWayPoint()
                        .getPosition())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/location/position", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                            .getApplicationData()
                            .getGpsPosition()
                            .getWayPoint()
                            .getSpeed()
                        / 10d)
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/location/speed", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                            .getApplicationData()
                            .getGpsPosition()
                            .getWayPoint()
                            .getHeading()
                        / 10d)
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/location/heading", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getLockStatus())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/locked", msg);

    // todo check configuration for available doors
    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getDriverDoor())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/driver", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getPassengerDoor())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/passenger", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRearLeftDoor())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/rearLeft", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRearRightDoor())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/rearRight", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getBootStatus())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/boot", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getBonnetStatus())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/doors/bonnet", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getFrontLeftTyrePressure())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/tyres/frontLeftPressure", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getFrontRrightTyrePressure())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/tyres/frontRightPressure", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRearLeftTyrePressure())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/tyres/rearLeftPressure", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRearRightTyrePressure())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/tyres/rearRightPressure", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRemoteClimateStatus())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/climate/remoteClimateState", msg);

    msg =
        new MqttMessage(
            String.valueOf(
                    vehicleStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getRmtHtdRrWndSt())
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/climate/backWindowHeat", msg);

    if (vehicleStatusResponseMessage.getApplicationData().getBasicVehicleStatus().getMileage()
        > 0) {
      // sometimes milage is 0, ignore such values
      msg =
          new MqttMessage(
              String.valueOf(
                      vehicleStatusResponseMessage
                              .getApplicationData()
                              .getBasicVehicleStatus()
                              .getMileage()
                          / 10.d)
                  .getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/drivetrain/milage", msg);

      // if the milage is 0, the electric range is also 0
      msg =
          new MqttMessage(
              String.valueOf(
                      vehicleStatusResponseMessage
                              .getApplicationData()
                              .getBasicVehicleStatus()
                              .getFuelRangeElec()
                          / 10.d)
                  .getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/drivetrain/range", msg);
    }
    return vehicleStatusResponseMessage.getApplicationData();
  }

  private OTA_ChrgMangDataResp updateChargeStatus(
      IMqttClient publisher, String uid, String token, String vin)
      throws IOException, MqttException {
    net.heberling.ismart.asn1.v3_0.MessageCoder<IASN1PreparedElement>
        chargingStatusRequestMessageEncoder =
            new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class);

    net.heberling.ismart.asn1.v3_0.Message<IASN1PreparedElement> chargingStatusMessage =
        chargingStatusRequestMessageEncoder.initializeMessage(uid, token, vin, "516", 768, 5, null);

    String chargingStatusRequestMessage =
        chargingStatusRequestMessageEncoder.encodeRequest(chargingStatusMessage);

    LOGGER.debug(
        SaicMqttGateway.toJSON(
            SaicMqttGateway.anonymized(
                chargingStatusRequestMessageEncoder, chargingStatusMessage)));

    String chargingStatusResponse =
        SaicMqttGateway.sendRequest(
            chargingStatusRequestMessage, saicUri.resolve("/TAP.Web/ota.mpv30"));

    net.heberling.ismart.asn1.v3_0.Message<OTA_ChrgMangDataResp> chargingStatusResponseMessage =
        new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
            .decodeResponse(chargingStatusResponse);

    LOGGER.debug(
        SaicMqttGateway.toJSON(
            SaicMqttGateway.anonymized(
                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class),
                chargingStatusResponseMessage)));

    // we get an eventId back...
    chargingStatusMessage
        .getBody()
        .setEventID(chargingStatusResponseMessage.getBody().getEventID());
    // ... use that to request the data again, until we have it
    // TODO: check for real errors (result!=0 and/or errorMessagePresent)
    while (chargingStatusResponseMessage.getApplicationData() == null) {

      if (chargingStatusResponseMessage.getBody().isErrorMessagePresent()) {
        if (chargingStatusResponseMessage.getBody().getResult() == 2) {
          // TODO: relogn
        }
        // try again next time
        return null;
      }

      SaicMqttGateway.fillReserved(chargingStatusMessage.getReserved());

      LOGGER.debug(
          SaicMqttGateway.toJSON(
              SaicMqttGateway.anonymized(
                  chargingStatusRequestMessageEncoder, chargingStatusMessage)));

      chargingStatusRequestMessage =
          chargingStatusRequestMessageEncoder.encodeRequest(chargingStatusMessage);

      chargingStatusResponse =
          SaicMqttGateway.sendRequest(
              chargingStatusRequestMessage, saicUri.resolve("/TAP.Web/ota.mpv30"));

      chargingStatusResponseMessage =
          new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
              .decodeResponse(chargingStatusResponse);

      LOGGER.debug(
          SaicMqttGateway.toJSON(
              SaicMqttGateway.anonymized(
                  new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class),
                  chargingStatusResponseMessage)));
    }
    MqttMessage msg = new MqttMessage(chargingStatusResponse.getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(
        mqttVINPrefix
            + "/_internal/"
            + chargingStatusResponseMessage.getBody().getApplicationID()
            + "_"
            + chargingStatusResponseMessage.getBody().getApplicationDataProtocolVersion()
            + "/raw",
        msg);

    msg =
        new MqttMessage(
            SaicMqttGateway.toJSON(chargingStatusResponseMessage).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(
        mqttVINPrefix
            + "/_internal/"
            + chargingStatusResponseMessage.getBody().getApplicationID()
            + "_"
            + chargingStatusResponseMessage.getBody().getApplicationDataProtocolVersion()
            + "/json",
        msg);

    double current =
        chargingStatusResponseMessage.getApplicationData().getBmsPackCrnt() * 0.05d - 1000.0d;
    msg = new MqttMessage((String.valueOf(current)).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/current", msg);

    double voltage =
        (double) chargingStatusResponseMessage.getApplicationData().getBmsPackVol() * 0.25d;
    msg = new MqttMessage((String.valueOf(voltage)).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/voltage", msg);

    double power = current * voltage / 1000d;
    msg = new MqttMessage((String.valueOf(power)).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/power", msg);

    msg =
        new MqttMessage(
            (String.valueOf(
                    chargingStatusResponseMessage
                        .getApplicationData()
                        .getChargeStatus()
                        .getChargingGunState()))
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/chargerConnected", msg);

    msg =
        new MqttMessage(
            (String.valueOf(
                    chargingStatusResponseMessage
                        .getApplicationData()
                        .getChargeStatus()
                        .getChargingType()))
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/chargingType", msg);

    msg =
        new MqttMessage(
            (String.valueOf(
                    chargingStatusResponseMessage.getApplicationData().getBmsPackSOCDsp() / 10d))
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    publisher.publish(mqttVINPrefix + "/drivetrain/soc", msg);

    return chargingStatusResponseMessage.getApplicationData();
  }

  public void notifyCarActivity(ZonedDateTime now, boolean force) throws MqttException {
    // if the car activity changed, notify the channel
    if (lastCarActivity == null || force || lastCarActivity.isBefore(now)) {
      lastCarActivity = now;
      MqttMessage msg =
          new MqttMessage(SaicMqttGateway.toJSON(lastCarActivity).getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/refresh/lastActivity", msg);
    }
  }

  public void notifyMessage(SaicMessage message) throws MqttException {
    if (lastVehicleMessage == null || message.getMessageTime().isAfter(lastVehicleMessage)) {
      // only publish the latest message
      MqttMessage msg =
          new MqttMessage(SaicMqttGateway.toJSON(message).getBytes(StandardCharsets.UTF_8));
      msg.setQos(0);
      msg.setRetained(true);
      client.publish(mqttVINPrefix + "/info/lastMessage", msg);
      lastVehicleMessage = message.getMessageTime();
    }
    // something happened, better check the vehicle state
    notifyCarActivity(message.getMessageTime(), false);
  }
}
