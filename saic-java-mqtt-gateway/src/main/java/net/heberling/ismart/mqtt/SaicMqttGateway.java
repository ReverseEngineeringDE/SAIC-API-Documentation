package net.heberling.ismart.mqtt;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.owlike.genson.Context;
import com.owlike.genson.Converter;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.convert.ChainedFactory;
import com.owlike.genson.ext.javadatetime.JavaDateTimeBundle;
import com.owlike.genson.reflect.TypeUtil;
import com.owlike.genson.stream.ObjectReader;
import com.owlike.genson.stream.ObjectWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.heberling.ismart.asn1.AbstractMessage;
import net.heberling.ismart.asn1.AbstractMessageCoder;
import net.heberling.ismart.asn1.Anonymizer;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInReq;
import net.heberling.ismart.asn1.v1_1.entity.MP_UserLoggingInResp;
import net.heberling.ismart.cli.UTF8StringObjectWriter;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.bn.annotations.ASN1Enum;
import org.bn.annotations.ASN1Sequence;
import org.bn.coders.IASN1PreparedElement;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import picocli.CommandLine;

@CommandLine.Command(
    name = "ismart-mqtt-gateway",
    mixinStandardHelpOptions = true,
    versionProvider = SaicMqttGateway.VersionProvider.class)
public class SaicMqttGateway implements Callable<Integer> {
  static class ConfigFileConverter implements CommandLine.ITypeConverter<File> {

    @Override
    public File convert(String value) throws Exception {
      var file = new File(value);
      if (file.exists()) {
        final var tomlMapper = new TomlMapper();
        final var data = tomlMapper.readValue(file, Map.class);
        final var data2 = flat("config", data);
        data2.forEach((k, v) -> System.getProperties().putIfAbsent(k, v));
      } else {
        throw new FileNotFoundException(value);
      }
      return file;
    }
  }

  @CommandLine.Option(
      order = Integer.MIN_VALUE,
      converter = ConfigFileConverter.class,
      names = {"-c", "--config"},
      description = {
        "The config file to use. Options can be overridden by cli parameters and environment"
            + " variables"
      })
  private File config;

  @CommandLine.Option(
      names = {"-m", "--mqtt-uri"},
      required = true,
      description = {"The URI to the MQTT Server.", "Environment Variable: MQTT_URI"},
      defaultValue = "${env:MQTT_URI:-${config.mqtt.uri}}")
  private String mqttUri;

  @CommandLine.Option(
      names = {"--mqtt-user"},
      description = {"The MQTT user name.", "Environment Variable: MQTT_USER"},
      defaultValue = "${env:MQTT_USER:-${config.mqtt.username}}")
  private String mqttUser;

  @CommandLine.Option(
      names = {"--mqtt-password"},
      description = {"The MQTT password.", "Environment Variable: MQTT_PASSWORD"},
      defaultValue = "${env:MQTT_PASSWORD:-${config.mqtt.password}}")
  private char[] mqttPassword;

  @CommandLine.Option(
      names = {"-u", "--saic-user"},
      required = true,
      description = {"The SAIC user name.", "Environment Variable: SAIC_USER"},
      defaultValue = "${env:SAIC_USER:-${config.saic.username}}")
  private String saicUser;

  @CommandLine.Option(
      names = {"-p", "--saic-password"},
      required = true,
      description = {"The SAIC password.", "Environment Variable: SAIC_PASSWORD"},
      defaultValue = "${env:SAIC_PASSWORD:-${config.saic.password}}")
  private String saicPassword;

  @CommandLine.Option(
      names = {"--abrp-api-key"},
      description = {
        "The API key for the A Better Route Planer telemetry API.",
        "Default is the open source telemetry API key 8cfc314b-03cd-4efe-ab7d-4431cd8f2e2d",
        "Environment Variable: ABRP_API_KEY"
      },
      defaultValue =
          "${env:ABRP_API_KEY:-${sys:config.abrp.api-key:-8cfc314b-03cd-4efe-ab7d-4431cd8f2e2d}}")
  private String abrpApiKey;

  @CommandLine.Option(
      names = {"--abrp-user-token"},
      description = {
        "The mapping of VIN to ABRP User Token.",
        "Multiple mappings can be provided seperated by ,",
        "Example: LSJXXXX=12345-abcdef,LSJYYYY=67890-ghijkl",
        "Environment Variable: ABRP_USER_TOKEN"
      },
      defaultValue = "${env:ABRP_USER_TOKEN:-${sys:config.abrp.token:-_NULL_}}",
      split = ",")
  private Map<String, String> vinAbrpTokenMap = new HashMap<>();

  private IMqttClient client;

  private final Map<String, VehicleHandler> vehicleHandlerMap = new HashMap<>();

  @Override
  public Integer call() throws Exception { // your business logic goes here...
    String publisherId = UUID.randomUUID().toString();
    try (IMqttClient client =
        new MqttClient(mqttUri, publisherId, null) {
          @Override
          public void close() throws MqttException {
            disconnect();
            super.close(true);
          }
        }) {
      this.client = client;
      MqttConnectOptions options = new MqttConnectOptions();
      options.setAutomaticReconnect(true);
      options.setCleanSession(true);
      options.setConnectionTimeout(10);
      if (mqttUser != null) {
        options.setUserName(mqttUser);
      }
      if (mqttPassword != null) {
        options.setPassword(mqttPassword);
      }
      client.connect(options);

      MessageCoder<MP_UserLoggingInReq> loginRequestMessageCoder =
          new MessageCoder<>(MP_UserLoggingInReq.class);

      MP_UserLoggingInReq applicationData = new MP_UserLoggingInReq();
      applicationData.setPassword(saicPassword);
      Message<MP_UserLoggingInReq> loginRequestMessage =
          loginRequestMessageCoder.initializeMessage(
              "0000000000000000000000000000000000000000000000000#".substring(saicUser.length())
                  + saicUser,
              null,
              null,
              "501",
              513,
              1,
              applicationData);

      String loginRequest = loginRequestMessageCoder.encodeRequest(loginRequestMessage);

      System.out.println(toJSON(anonymized(loginRequestMessageCoder, loginRequestMessage)));

      String loginResponse = sendRequest(loginRequest, "https://tap-eu.soimt.com/TAP.Web/ota.mp");

      Message<MP_UserLoggingInResp> loginResponseMessage =
          new MessageCoder<>(MP_UserLoggingInResp.class).decodeResponse(loginResponse);

      System.out.println(
          toJSON(anonymized(new MessageCoder<>(MP_UserLoggingInResp.class), loginResponseMessage)));
      List<Future<?>> futures =
          loginResponseMessage.getApplicationData().getVinList().stream()
              .map(
                  vin -> {
                    VehicleHandler handler =
                        new VehicleHandler(
                            this,
                            client,
                            loginResponseMessage.getBody().getUid(),
                            loginResponseMessage.getApplicationData().getToken(),
                            vin);
                    vehicleHandlerMap.put(vin.getVin(), handler);
                    return handler;
                  })
              .map(
                  handler ->
                      (Callable<Object>)
                          () -> {
                            handler.handleVehicle();
                            return null;
                          })
              .map(Executors.newSingleThreadExecutor()::submit)
              .collect(Collectors.toList());

      ScheduledFuture<?> pollingJob =
          createMessagePoller(
              loginResponseMessage.getBody().getUid(),
              loginResponseMessage.getApplicationData().getToken());

      futures.add(pollingJob);

      for (Future<?> future : futures) {
        // make sure we wait on all futures before exiting
        future.get();
      }
      return 0;
    }
  }

  private ScheduledFuture<?> createMessagePoller(String uid, String token) {
    return Executors.newSingleThreadScheduledExecutor()
        .scheduleWithFixedDelay(new MessageHandler(uid, token, this), 1, 1, TimeUnit.SECONDS);
  }

  public static void main(String... args) {
    int exitCode = new CommandLine(new SaicMqttGateway()).execute(args);
    System.exit(exitCode);
  }

  private static Map<String, String> flat(String prefix, Map<String, Object> nested) {
    Map<String, String> map = new HashMap<>();
    nested.forEach(
        (key, value) -> {
          var newPrefix = prefix + "." + key;
          if (value instanceof Map) {
            map.putAll(flat(newPrefix, (Map<String, Object>) value));
          } else if (value instanceof Collection) {
            map.put(
                newPrefix,
                ((Collection<Object>) value)
                    .stream()
                        .map(o -> (Map<String, String>) o)
                        .flatMap(m -> m.entrySet().stream())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(",")));
          } else {
            map.put(newPrefix, value.toString());
          }
        });
    return map;
  }

  static <
          H extends IASN1PreparedElement,
          B extends IASN1PreparedElement,
          E extends IASN1PreparedElement,
          M extends AbstractMessage<H, B, E>>
      M anonymized(AbstractMessageCoder<H, B, E, M> coder, M message) {
    M messageCopy = coder.decodeResponse(coder.encodeRequest(message));
    Anonymizer.anonymize(messageCopy);
    return messageCopy;
  }

  public static void fillReserved(byte[] reservedBytes) {
    System.arraycopy(
        (ThreadLocalRandom.current().nextLong() + "1111111111111111").getBytes(),
        0,
        reservedBytes,
        0,
        16);
  }

  static String sendRequest(String request, String endpoint) throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPost httppost = new HttpPost(endpoint);
      // Request parameters and other properties.
      httppost.setEntity(new StringEntity(request, ContentType.TEXT_HTML));

      // Execute and get the response.
      // Create a custom response handler
      HttpClientResponseHandler<String> responseHandler =
          response -> {
            final int status = response.getCode();
            if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
              final HttpEntity entity = response.getEntity();
              try {
                return entity != null ? EntityUtils.toString(entity) : null;
              } catch (final ParseException ex) {
                throw new ClientProtocolException(ex);
              }
            } else {
              throw new ClientProtocolException("Unexpected response status: " + status);
            }
          };
      return httpclient.execute(httppost, responseHandler);
    }
  }

  public static String toJSON(Object message) {
    // TODO: make sure this corresponds to the JER ASN.1 serialisation format
    final ChainedFactory chain =
        new ChainedFactory() {
          @Override
          protected Converter<?> create(Type type, Genson genson, Converter<?> nextConverter) {
            return new Converter<>() {
              @Override
              public void serialize(Object object, ObjectWriter writer, Context ctx)
                  throws Exception {
                if (object != null) {
                  writer.beginNextObjectMetadata();
                  if (object.getClass().isAnnotationPresent(ASN1Enum.class)) {
                    writer.writeMetadata(
                        "ASN1Type", object.getClass().getAnnotation(ASN1Enum.class).name());
                  } else if (object.getClass().isAnnotationPresent(ASN1Sequence.class)) {
                    writer.writeMetadata(
                        "ASN1Type", object.getClass().getAnnotation(ASN1Sequence.class).name());
                  }
                }

                @SuppressWarnings("unchecked")
                Converter<Object> n = (Converter<Object>) nextConverter;
                if (!(writer instanceof UTF8StringObjectWriter)) {
                  writer = new UTF8StringObjectWriter(writer);
                }
                n.serialize(object, writer, ctx);
              }

              @Override
              public Object deserialize(ObjectReader reader, Context ctx) throws Exception {
                return nextConverter.deserialize(reader, ctx);
              }
            };
          }
        };
    chain.withNext(
        new ChainedFactory() {
          @Override
          protected Converter<?> create(Type type, Genson genson, Converter<?> converter) {
            final Class<?> clazz = TypeUtil.getRawClass(type);
            if (clazz.isAnnotationPresent(ASN1Enum.class)) {

              return new Converter<>() {
                @Override
                public void serialize(Object o, ObjectWriter objectWriter, Context context)
                    throws Exception {
                  Method getValue = clazz.getMethod("getValue");
                  Object value = getValue.invoke(o);
                  if (value == null) {
                    objectWriter.writeNull();
                  } else {
                    objectWriter.writeString(String.valueOf(value));
                  }
                }

                @Override
                public Object deserialize(ObjectReader objectReader, Context context) {
                  throw new UnsupportedOperationException("not implemented yet");
                }
              };
            } else {

              return converter;
            }
          }
        });
    return new GensonBuilder()
        .useDateAsTimestamp(false)
        .withBundle(new JavaDateTimeBundle())
        .useIndentation(true)
        .useRuntimeType(true)
        .exclude("preparedData")
        .withConverterFactory(chain)
        .create()
        .serialize(message);
  }

  public String getAbrpApiKey() {
    return abrpApiKey;
  }

  public String getAbrpUserToken(String vin) {
    return vinAbrpTokenMap.get(vin);
  }

  public void notifyMessage(SaicMessage message) throws MqttException {
    MqttMessage msg =
        new MqttMessage(SaicMqttGateway.toJSON(message).getBytes(StandardCharsets.UTF_8));
    msg.setQos(0);
    // Don't retain, so deleted messages are removed
    // automatically from the broker
    msg.setRetained(false);
    client.publish("saic/message/" + message.getMessageId(), msg);

    if (message.getVin() != null) {
      vehicleHandlerMap.get(message.getVin()).notifyMessage(message);
    }
  }

  static class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
      return new String[] {getClass().getPackage().getImplementationVersion()};
    }
  }
}
