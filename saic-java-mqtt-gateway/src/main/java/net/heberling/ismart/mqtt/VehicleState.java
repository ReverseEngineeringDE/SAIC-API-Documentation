package net.heberling.ismart.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import net.heberling.ismart.asn1.v1_1.entity.VinInfo;
import net.heberling.ismart.asn1.v2_1.Message;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class VehicleState {

  private final IMqttClient client;
  private final String mqttVINPrefix;

  private ZonedDateTime lastCarActivity;
  private ZonedDateTime lastVehicleMessage;

  public VehicleState(IMqttClient client, String mqttVINPrefix) {
    this.client = client;
    this.mqttVINPrefix = mqttVINPrefix;
  }

  public void handleVehicleStatusMessage(
      Message<OTA_RVMVehicleStatusResp25857> vehicleStatusResponseMessage) throws MqttException {
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

    MqttMessage msg =
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
    client.publish(mqttVINPrefix + "/drivetrain/auxiliaryBatteryVoltage", msg);

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
      client.publish(mqttVINPrefix + "/drivetrain/mileage", msg);

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
  }

  public void handleChargeStatusMessage(
      net.heberling.ismart.asn1.v3_0.Message<OTA_ChrgMangDataResp> chargingStatusResponseMessage)
      throws MqttException {
    MqttMessage msg =
        new MqttMessage(
            SaicMqttGateway.toJSON(chargingStatusResponseMessage).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(
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
    client.publish(mqttVINPrefix + "/drivetrain/current", msg);

    double voltage =
        (double) chargingStatusResponseMessage.getApplicationData().getBmsPackVol() * 0.25d;
    msg = new MqttMessage((String.valueOf(voltage)).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/voltage", msg);

    double power = current * voltage / 1000d;
    msg = new MqttMessage((String.valueOf(power)).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/power", msg);

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
    client.publish(mqttVINPrefix + "/drivetrain/chargerConnected", msg);

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
    client.publish(mqttVINPrefix + "/drivetrain/chargingType", msg);

    msg =
        new MqttMessage(
            (String.valueOf(
                    chargingStatusResponseMessage.getApplicationData().getBmsPackSOCDsp() / 10d))
                .getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    msg.setRetained(true);
    client.publish(mqttVINPrefix + "/drivetrain/soc", msg);
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

  public boolean isRecentlyActive() {
    return lastCarActivity.isAfter(ZonedDateTime.now().minus(15, ChronoUnit.MINUTES));
  }

  public void configure(VinInfo vinInfo) throws MqttException {
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
  }
}
