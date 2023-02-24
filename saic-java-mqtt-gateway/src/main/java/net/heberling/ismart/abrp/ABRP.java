package net.heberling.ismart.abrp;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.heberling.ismart.asn1.v2_1.entity.OTA_RVMVehicleStatusResp25857;
import net.heberling.ismart.asn1.v3_0.entity.OTA_ChrgMangDataResp;
import net.heberling.ismart.mqtt.SaicMqttGateway;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ABRP {
  private static final Logger LOGGER = LoggerFactory.getLogger(ABRP.class);

  public static String updateAbrp(
      String abrpApiKey,
      String abrpUserToken,
      OTA_RVMVehicleStatusResp25857 vehicleStatus,
      OTA_ChrgMangDataResp chargeStatus)
      throws IOException {
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
          vehicleStatus.getGpsPosition().getWayPoint().getPosition().getLatitude() / 1000000d);
      // lon [°]: Current vehicle longitude
      map.put(
          "lon",
          vehicleStatus.getGpsPosition().getWayPoint().getPosition().getLongitude() / 1000000d);
      // is_charging [bool or 1/0]: Determines vehicle state. 0 is not charging, 1 is
      // charging
      boolean isCharging =
          vehicleStatus.getBasicVehicleStatus().isExtendedData2Present()
              && vehicleStatus.getBasicVehicleStatus().getExtendedData2() >= 1;
      map.put("is_charging", isCharging);
      // TODO: is_dcfc [bool or 1/0]: If is_charging, indicate if this is DC fast charging
      // is_parked [bool or 1/0]: If the vehicle gear is in P (or the driver has left the
      // car)
      map.put(
          "is_parked",
          vehicleStatus.getBasicVehicleStatus().getEngineStatus() != 1
              || vehicleStatus.getBasicVehicleStatus().getHandBrake());
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
          "elevation", vehicleStatus.getGpsPosition().getWayPoint().getPosition().getAltitude());
      // ext_temp [°C]: Outside temperature measured by the vehicle
      if (vehicleStatus.getBasicVehicleStatus().getExteriorTemperature() != -128) {
        map.put("ext_temp", vehicleStatus.getBasicVehicleStatus().getExteriorTemperature());
      }
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
            "est_battery_range", vehicleStatus.getBasicVehicleStatus().getFuelRangeElec() / 10.d);
      }
      String request =
          "token="
              + abrpUserToken
              + "&tlm="
              + URLEncoder.encode(SaicMqttGateway.toJSON(map), StandardCharsets.UTF_8);
      LOGGER.debug("ABRP request: {}", request);

      HttpGet httppost =
          new HttpGet("https://api.iternio.com/1/tlm/send?api_key=" + abrpApiKey + "&" + request);

      // Execute and get the response.
      // Create a custom response handler
      HttpClientResponseHandler<String> responseHandler =
          response -> {
            final int status = response.getCode();
            final HttpEntity entity = response.getEntity();
            if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
              try {
                return entity != null ? EntityUtils.toString(entity) : null;
              } catch (final ParseException ex) {
                throw new ClientProtocolException(ex);
              }
            } else {
              try {
                if (entity != null)
                  throw new ClientProtocolException(
                      "Unexpected response status: "
                          + status
                          + " Content: "
                          + EntityUtils.toString(entity));
                else throw new ClientProtocolException("Unexpected response status: " + status);
              } catch (final ParseException ex) {
                throw new ClientProtocolException(ex);
              }
            }
          };
      String response = httpclient.execute(httppost, responseHandler);
      LOGGER.debug("ABRP response: {}", response);
      return response;
    }
  }
}
