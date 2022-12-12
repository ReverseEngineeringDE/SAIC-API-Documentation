package net.heberling.ismart.mqtt;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import net.heberling.ismart.asn1.v1_1.entity.VinInfo;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusReq;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.bn.coders.IASN1PreparedElement;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class VehicleHandler {
    private String uid;
    private String token;
    private VinInfo vinInfo;
    private SaicMqttGateway saicMqttGateway;
    private IMqttClient client;
    private ZonedDateTime lastCarActivity;
    private ZonedDateTime lastVehicleMessage;

    public VehicleHandler(
            SaicMqttGateway saicMqttGateway,
            IMqttClient client,
            String uid,
            String token,
            VinInfo vinInfo) {

        this.saicMqttGateway = saicMqttGateway;
        this.client = client;
        this.uid = uid;
        this.token = token;
        this.vinInfo = vinInfo;
    }

    void handleVehicle() throws MqttException, IOException {
        MqttMessage msg =
                new MqttMessage(
                        vinInfo.getModelConfigurationJsonStr().getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vinInfo.getVin() + "/configuration/raw", msg);
        for (String c : vinInfo.getModelConfigurationJsonStr().split(";")) {
            Map<String, String> map = new HashMap<>();
            for (String e : c.split(",")) {
                map.put(e.split(":")[0], e.split(":")[1]);
            }
            msg = new MqttMessage(SaicMqttGateway.toJSON(map).getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish(
                    "saic/vehicle/" + vinInfo.getVin() + "/configuration/" + map.get("code"), msg);
        }
        // we just got started, force some updates
        notifyCarActivity(ZonedDateTime.now(), true);
        while (true) {
            if (lastCarActivity.isAfter(ZonedDateTime.now().minus(1, ChronoUnit.MINUTES))) {
                OTA_RVMVehicleStatusResp25857 vehicleStatus =
                        updateVehicleStatus(client, uid, token, vinInfo.getVin());
                OTA_ChrgMangDataResp chargeStatus =
                        updateChargeStatus(client, uid, token, vinInfo.getVin());
                updateAbrp(vehicleStatus, chargeStatus);
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

    private void updateAbrp(
            OTA_RVMVehicleStatusResp25857 vehicleStatus, OTA_ChrgMangDataResp chargeStatus)
            throws MqttException {
        MqttMessage msg;
        String abrpUserToken = saicMqttGateway.getAbrpUserToken(vinInfo.getVin());
        if (saicMqttGateway.getAbrpApiKey() != null
                && abrpUserToken != null
                && vehicleStatus != null
                && chargeStatus != null) {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                // Request parameters and other properties.
                HashMap<String, Object> map = new HashMap<>();
                // utc [s]: Current UTC timestamp (epoch) in seconds (note, not milliseconds!)
                map.put("utc", vehicleStatus.getGpsPosition().getTimestamp4Short().getSeconds());
                // soc [SoC %]: State of Charge of the vehicle (what's displayed on the dashboard of
                // the vehicle is preferred)
                map.put("soc", chargeStatus.getBmsPackSOCDsp() / 10.d);
                // power [kW]: Instantaneous power output/input to the vehicle. Power output is
                // positive, power input is negative (charging)
                double current = chargeStatus.getBmsPackCrnt() * 0.05d - 1000.0d;
                double voltage = (double) chargeStatus.getBmsPackVol() * 0.25d;
                double power = current * voltage / 1000d;
                map.put("power", power);
                // speed [km/h]: Vehicle speed
                map.put("speed", vehicleStatus.getGpsPosition().getWayPoint().getSpeed() / 10.d);
                // lat [°]: Current vehicle latitude
                map.put(
                        "lat",
                        vehicleStatus.getGpsPosition().getWayPoint().getPosition().getLatitude()
                                / 1000000d);
                // lon [°]: Current vehicle longitude
                map.put(
                        "lon",
                        vehicleStatus.getGpsPosition().getWayPoint().getPosition().getLongitude()
                                / 1000000d);
                // is_charging [bool or 1/0]: Determines vehicle state. 0 is not charging, 1 is
                // charging
                boolean isCharging =
                        vehicleStatus.getBasicVehicleStatus().isExtendedData2Present()
                                && vehicleStatus.getBasicVehicleStatus().getExtendedData2() >= 1;
                map.put("is_charging", isCharging);
                // TODO: is_dcfc [bool or 1/0]: If is_charging, indicate if this is DC fast charging
                // TODO: is_parked [bool or 1/0]: If the vehicle gear is in P (or the driver has
                // left the car)
                // TODO: capacity [kWh]: Estimated usable battery capacity (can be given together
                // with soh, but usually not)
                // TODO: kwh_charged [kWh]: Measured energy input while charging. Typically a
                // cumulative total, but also supports individual sessions.
                // TODO: soh [%]: State of Health of the battery. 100 = no degradation
                // heading [°]: Current heading of the vehicle. This will take priority over phone
                // heading, so don't include if not accurate.
                map.put("heading", vehicleStatus.getGpsPosition().getWayPoint().getHeading());
                // elevation [m]: Vehicle's current elevation. If not given, will be looked up from
                // location (but may miss 3D structures)
                map.put(
                        "elevation",
                        vehicleStatus.getGpsPosition().getWayPoint().getPosition().getAltitude());
                // TODO: ext_temp [°C]: Outside temperature measured by the vehicle
                // TODO: batt_temp [°C]: Battery temperature
                // voltage [V]: Battery pack voltage
                map.put("voltage", voltage);
                // current [A]: Battery pack current (similar to power: output is
                // positive, input (charging) is negative.)
                map.put("current", current);
                // odometer [km]: Current odometer reading in km.
                if (vehicleStatus.getBasicVehicleStatus().getMileage() > 0) {
                    map.put("odometer", vehicleStatus.getBasicVehicleStatus().getMileage() / 10.d);
                }
                // est_battery_range [km]: Estimated remaining range of the vehicle (according to
                // the vehicle)
                if (vehicleStatus.getBasicVehicleStatus().getFuelRangeElec() > 0) {
                    map.put(
                            "est_battery_range",
                            vehicleStatus.getBasicVehicleStatus().getFuelRangeElec() / 10.d);
                }
                String request =
                        "token="
                                + abrpUserToken
                                + "&tlm="
                                + URLEncoder.encode(
                                        SaicMqttGateway.toJSON(map), StandardCharsets.UTF_8);
                HttpGet httppost =
                        new HttpGet(
                                "https://api.iternio.com/1/tlm/send?api_key="
                                        + saicMqttGateway.getAbrpApiKey()
                                        + "&"
                                        + request);

                // Execute and get the response.
                // Create a custom response handler
                HttpClientResponseHandler<String> responseHandler =
                        response -> {
                            final int status = response.getCode();
                            if (status >= HttpStatus.SC_SUCCESS
                                    && status < HttpStatus.SC_REDIRECTION) {
                                final HttpEntity entity = response.getEntity();
                                try {
                                    return entity != null ? EntityUtils.toString(entity) : null;
                                } catch (final ParseException ex) {
                                    throw new ClientProtocolException(ex);
                                }
                            } else {
                                final HttpEntity entity = response.getEntity();
                                try {
                                    if (entity != null)
                                        throw new ClientProtocolException(
                                                "Unexpected response status: "
                                                        + status
                                                        + " Content: "
                                                        + EntityUtils.toString(entity));
                                    else
                                        throw new ClientProtocolException(
                                                "Unexpected response status: " + status);
                                } catch (final ParseException ex) {
                                    throw new ClientProtocolException(ex);
                                }
                            }
                        };
                String execute = httpclient.execute(httppost, responseHandler);
                System.out.println("ABRP: " + execute);
                msg = new MqttMessage(execute.getBytes(StandardCharsets.UTF_8));
                msg.setQos(0);
                msg.setRetained(true);
                client.publish("saic/vehicle/" + vinInfo.getVin() + "/abrp", msg);
            } catch (Exception e) {
                System.out.println("ABRP failed.:");
                e.printStackTrace();
                msg = new MqttMessage(e.toString().getBytes(StandardCharsets.UTF_8));
                msg.setQos(0);
                msg.setRetained(true);
                client.publish("saic/vehicle/" + vinInfo.getVin() + "/abrp", msg);
            }
        }
    }

    private OTA_RVMVehicleStatusResp25857 updateVehicleStatus(
            IMqttClient client, String uid, String token, String vin)
            throws IOException, MqttException {
        net.heberling.ismart.asn1.v2_1.Message<OTA_RVMVehicleStatusReq> chargingStatusMessage =
                new net.heberling.ismart.asn1.v2_1.Message<>(
                        new net.heberling.ismart.asn1.v2_1.MP_DispatcherHeader(),
                        new byte[16],
                        new net.heberling.ismart.asn1.v2_1.MP_DispatcherBody(),
                        new OTA_RVMVehicleStatusReq());
        SaicMqttGateway.fillReserved(chargingStatusMessage.getReserved());

        chargingStatusMessage.getBody().setApplicationID("511");
        chargingStatusMessage.getBody().setTestFlag(2);
        chargingStatusMessage.getBody().setVin(vin);
        chargingStatusMessage.getBody().setUid(uid);
        chargingStatusMessage.getBody().setToken(token);
        chargingStatusMessage.getBody().setMessageID(1);
        chargingStatusMessage.getBody().setEventCreationTime((int) Instant.now().getEpochSecond());
        chargingStatusMessage.getBody().setApplicationDataProtocolVersion(25857);
        chargingStatusMessage.getBody().setEventID(0);

        chargingStatusMessage.getApplicationData().setVehStatusReqType(2);

        String chargingStatusRequestMessage =
                new net.heberling.ismart.asn1.v2_1.MessageCoder<>(OTA_RVMVehicleStatusReq.class)
                        .encodeRequest(chargingStatusMessage);

        String chargingStatusResponse =
                SaicMqttGateway.sendRequest(
                        chargingStatusRequestMessage, "https://tap-eu.soimt.com/TAP.Web/ota.mpv21");

        net.heberling.ismart.asn1.v2_1.Message<OTA_RVMVehicleStatusResp25857>
                chargingStatusResponseMessage =
                        new net.heberling.ismart.asn1.v2_1.MessageCoder<>(
                                        OTA_RVMVehicleStatusResp25857.class)
                                .decodeResponse(chargingStatusResponse);

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

            chargingStatusMessage.getBody().setUid(uid);
            chargingStatusMessage.getBody().setToken(token);

            SaicMqttGateway.fillReserved(chargingStatusMessage.getReserved());

            chargingStatusRequestMessage =
                    new net.heberling.ismart.asn1.v2_1.MessageCoder<>(OTA_RVMVehicleStatusReq.class)
                            .encodeRequest(chargingStatusMessage);

            chargingStatusResponse =
                    SaicMqttGateway.sendRequest(
                            chargingStatusRequestMessage,
                            "https://tap-eu.soimt.com/TAP.Web/ota.mpv21");

            chargingStatusResponseMessage =
                    new net.heberling.ismart.asn1.v2_1.MessageCoder<>(
                                    OTA_RVMVehicleStatusResp25857.class)
                            .decodeResponse(chargingStatusResponse);

            System.out.println(
                    SaicMqttGateway.toJSON(
                            SaicMqttGateway.anonymized(
                                    new net.heberling.ismart.asn1.v2_1.MessageCoder<>(
                                            OTA_RVMVehicleStatusResp25857.class),
                                    chargingStatusResponseMessage)));
        }

        boolean engineRunning =
                chargingStatusResponseMessage
                                .getApplicationData()
                                .getBasicVehicleStatus()
                                .getEngineStatus()
                        == 1;
        boolean isCharging =
                chargingStatusResponseMessage
                                .getApplicationData()
                                .getBasicVehicleStatus()
                                .isExtendedData2Present()
                        && chargingStatusResponseMessage
                                        .getApplicationData()
                                        .getBasicVehicleStatus()
                                        .getExtendedData2()
                                >= 1;

        if (isCharging || engineRunning) {
            notifyCarActivity(ZonedDateTime.now(), false);
        }

        MqttMessage msg = new MqttMessage(chargingStatusResponse.getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish(
                "saic/vehicle/"
                        + vin
                        + "/"
                        + chargingStatusResponseMessage.getBody().getApplicationID()
                        + "_"
                        + chargingStatusResponseMessage
                                .getBody()
                                .getApplicationDataProtocolVersion()
                        + "/raw",
                msg);

        msg =
                new MqttMessage(
                        SaicMqttGateway.toJSON(chargingStatusResponseMessage)
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish(
                "saic/vehicle/"
                        + vin
                        + "/"
                        + chargingStatusResponseMessage.getBody().getApplicationID()
                        + "_"
                        + chargingStatusResponseMessage
                                .getBody()
                                .getApplicationDataProtocolVersion()
                        + "/json",
                msg);

        msg = new MqttMessage(String.valueOf(engineRunning).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/running", msg);

        msg = new MqttMessage(String.valueOf(isCharging).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/charging", msg);

        Integer interiorTemperature =
                chargingStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getInteriorTemperature();
        if (interiorTemperature > -128) {
            msg =
                    new MqttMessage(
                            String.valueOf(interiorTemperature).getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vin + "/temperature/interior", msg);
        }

        Integer exteriorTemperature =
                chargingStatusResponseMessage
                        .getApplicationData()
                        .getBasicVehicleStatus()
                        .getExteriorTemperature();
        if (exteriorTemperature > -128) {
            msg =
                    new MqttMessage(
                            String.valueOf(exteriorTemperature).getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vin + "/temperature/exterior", msg);
        }

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                        .getApplicationData()
                                                        .getBasicVehicleStatus()
                                                        .getBatteryVoltage()
                                                / 10.d)
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/auxillary_battery", msg);

        msg =
                new MqttMessage(
                        SaicMqttGateway.toJSON(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getGpsPosition())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/gps/json", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                        .getApplicationData()
                                                        .getGpsPosition()
                                                        .getWayPoint()
                                                        .getSpeed()
                                                / 10d)
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/speed", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBasicVehicleStatus()
                                                .getLockStatus())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/locked", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBasicVehicleStatus()
                                                .getRemoteClimateStatus())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/remoteClimate", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBasicVehicleStatus()
                                                .getRmtHtdRrWndSt())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        client.publish("saic/vehicle/" + vin + "/remoteRearWindowHeater", msg);

        if (chargingStatusResponseMessage.getApplicationData().getBasicVehicleStatus().getMileage()
                > 0) {
            // sometimes milage is 0, ignore such values
            msg =
                    new MqttMessage(
                            String.valueOf(
                                            chargingStatusResponseMessage
                                                            .getApplicationData()
                                                            .getBasicVehicleStatus()
                                                            .getMileage()
                                                    / 10.d)
                                    .getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vin + "/milage", msg);

            // if the milage is 0, the electric range is also 0
            msg =
                    new MqttMessage(
                            String.valueOf(
                                            chargingStatusResponseMessage
                                                            .getApplicationData()
                                                            .getBasicVehicleStatus()
                                                            .getFuelRangeElec()
                                                    / 10.d)
                                    .getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vin + "/range/electric", msg);
        }
        return chargingStatusResponseMessage.getApplicationData();
    }

    private static OTA_ChrgMangDataResp updateChargeStatus(
            IMqttClient publisher, String uid, String token, String vin)
            throws IOException, MqttException {
        net.heberling.ismart.asn1.v3_0.Message<IASN1PreparedElement> chargingStatusMessage =
                new net.heberling.ismart.asn1.v3_0.Message<>(
                        new net.heberling.ismart.asn1.v3_0.MP_DispatcherHeader(),
                        new byte[16],
                        new net.heberling.ismart.asn1.v3_0.MP_DispatcherBody(),
                        null);
        SaicMqttGateway.fillReserved(chargingStatusMessage.getReserved());

        chargingStatusMessage.getBody().setApplicationID("516");
        chargingStatusMessage.getBody().setTestFlag(2);
        chargingStatusMessage.getBody().setVin(vin);
        chargingStatusMessage.getBody().setUid(uid);
        chargingStatusMessage.getBody().setToken(token);
        chargingStatusMessage.getBody().setMessageID(5);
        chargingStatusMessage.getBody().setEventCreationTime((int) Instant.now().getEpochSecond());
        chargingStatusMessage.getBody().setApplicationDataProtocolVersion(768);
        chargingStatusMessage.getBody().setEventID(0);

        String chargingStatusRequestMessage =
                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class)
                        .encodeRequest(chargingStatusMessage);

        System.out.println(
                SaicMqttGateway.toJSON(
                        SaicMqttGateway.anonymized(
                                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                        IASN1PreparedElement.class),
                                chargingStatusMessage)));

        String chargingStatusResponse =
                SaicMqttGateway.sendRequest(
                        chargingStatusRequestMessage, "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

        net.heberling.ismart.asn1.v3_0.Message<OTA_ChrgMangDataResp> chargingStatusResponseMessage =
                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
                        .decodeResponse(chargingStatusResponse);

        System.out.println(
                SaicMqttGateway.toJSON(
                        SaicMqttGateway.anonymized(
                                new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                        OTA_ChrgMangDataResp.class),
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

            System.out.println(
                    SaicMqttGateway.toJSON(
                            SaicMqttGateway.anonymized(
                                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                            IASN1PreparedElement.class),
                                    chargingStatusMessage)));

            chargingStatusRequestMessage =
                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(IASN1PreparedElement.class)
                            .encodeRequest(chargingStatusMessage);

            chargingStatusResponse =
                    SaicMqttGateway.sendRequest(
                            chargingStatusRequestMessage,
                            "https://tap-eu.soimt.com/TAP.Web/ota.mpv30");

            chargingStatusResponseMessage =
                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(OTA_ChrgMangDataResp.class)
                            .decodeResponse(chargingStatusResponse);

            System.out.println(
                    SaicMqttGateway.toJSON(
                            SaicMqttGateway.anonymized(
                                    new net.heberling.ismart.asn1.v3_0.MessageCoder<>(
                                            OTA_ChrgMangDataResp.class),
                                    chargingStatusResponseMessage)));
        }
        MqttMessage msg = new MqttMessage(chargingStatusResponse.getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish(
                "saic/vehicle/"
                        + vin
                        + "/"
                        + chargingStatusResponseMessage.getBody().getApplicationID()
                        + "_"
                        + chargingStatusResponseMessage
                                .getBody()
                                .getApplicationDataProtocolVersion()
                        + "/raw",
                msg);

        msg =
                new MqttMessage(
                        SaicMqttGateway.toJSON(chargingStatusResponseMessage)
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish(
                "saic/vehicle/"
                        + vin
                        + "/"
                        + chargingStatusResponseMessage.getBody().getApplicationID()
                        + "_"
                        + chargingStatusResponseMessage
                                .getBody()
                                .getApplicationDataProtocolVersion()
                        + "/json",
                msg);

        double current =
                chargingStatusResponseMessage.getApplicationData().getBmsPackCrnt() * 0.05d
                        - 1000.0d;
        msg = new MqttMessage((String.valueOf(current)).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/current", msg);

        double voltage =
                (double) chargingStatusResponseMessage.getApplicationData().getBmsPackVol() * 0.25d;
        msg = new MqttMessage((String.valueOf(voltage)).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/voltage", msg);

        double power = current * voltage / 1000d;
        msg = new MqttMessage((String.valueOf(power)).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/power", msg);

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
        publisher.publish("saic/vehicle/" + vin + "/charge/type", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBmsChrgCtrlDspCmd())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsChrgCtrlDspCmd", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBmsChrgOtptCrntReq())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsChrgOtptCrntReq", msg);
        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBmsChrgSts())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsChrgSts", msg);
        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBmsPackCrnt())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsPackCrnt", msg);
        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                        .getApplicationData()
                                                        .getBmsPackVol()
                                                / 4)
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsPackVol", msg);

        msg =
                new MqttMessage(
                        String.valueOf(
                                        chargingStatusResponseMessage
                                                .getApplicationData()
                                                .getBmsPTCHeatReqDspCmd())
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/bms/bmsPTCHeatReqDspCmd", msg);

        msg =
                new MqttMessage(
                        (String.valueOf(
                                        chargingStatusResponseMessage
                                                        .getApplicationData()
                                                        .getBmsPackSOCDsp()
                                                / 10d))
                                .getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish("saic/vehicle/" + vin + "/soc", msg);

        return chargingStatusResponseMessage.getApplicationData();
    }

    public void notifyCarActivity(ZonedDateTime now, boolean force) throws MqttException {
        // if the car activity changed, notify the channel
        if (lastCarActivity == null || force || lastCarActivity.isBefore(now)) {
            lastCarActivity = now;
            MqttMessage msg =
                    new MqttMessage(
                            SaicMqttGateway.toJSON(lastCarActivity)
                                    .getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vinInfo.getVin() + "/last_activity", msg);
        }
    }

    public void notifyMessage(SaicMessage message) throws MqttException {
        if (lastVehicleMessage == null || message.getMessageTime().isAfter(lastVehicleMessage)) {
            // only publish the latest message
            MqttMessage msg =
                    new MqttMessage(
                            SaicMqttGateway.toJSON(message).getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(true);
            client.publish("saic/vehicle/" + vinInfo.getVin() + "/message", msg);
            lastVehicleMessage = message.getMessageTime();
        }
        // something happened, better check the vehicle state
        notifyCarActivity(message.getMessageTime(), false);
    }
}
