package net.heberling.ismart.mqtt;

import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.heberling.ismart.asn1.v1_1.Message;
import net.heberling.ismart.asn1.v1_1.MessageCoder;
import org.bn.coders.IASN1PreparedElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@ExtendWith(MockServerExtension.class)
public class DebianIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(DebianIT.class);

  private final Network network = Network.newNetwork();

  @Container
  final HiveMQContainer hivemq =
      new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2022.1"))
          .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES))
          .withNetwork(network)
          .withNetworkAliases("mqtt");

  @Test
  void testDebianInstallation(MockServerClient mockServerClient)
      throws IOException, InterruptedException {

    org.testcontainers.Testcontainers.exposeHostPorts(mockServerClient.getPort());

    try (GenericContainer<?> container =
        new GenericContainer<>(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(
                    builder ->
                        builder
                            .from(
                                "debian:stable-slim") // @sha256:9554f6caf2cafc99ad9873a822e1aafbb29d40608fe7ebe6569365b80fa5a422")
                            .run("apt-get update")
                            .run("apt-get install -y systemd")
                            .cmd("systemd", "--system")
                            .build()))) {

      Map<String, String> tmpFs = new HashMap<>();
      tmpFs.put("/run", "");
      tmpFs.put("/run/lock", "");

      container
          .dependsOn(hivemq)
          .withTmpFs(tmpFs)
          .withFileSystemBind("/sys/fs/cgroup", "/sys/fs/cgroup", BindMode.READ_ONLY)
          .withNetwork(network)
          .withLogConsumer(new Slf4jLogConsumer(LOGGER));

      container.start();

      File[] debs =
          new File(TestUtils.getProjectPath(), "target")
              .listFiles((dir, name) -> name.endsWith(".deb"));

      Assertions.assertNotNull(debs);
      Assertions.assertEquals(1, debs.length);

      container.copyFileToContainer(
          MountableFile.forHostPath(debs[0].getAbsolutePath()), "/root/ismart-mqtt-gateway.deb");

      ExecResult result;

      runInContainer(container, "apt-get", "install", "-y", "/root/ismart-mqtt-gateway.deb");

      // check version
      result =
          runInContainer(
              container, "java", "-jar", "/opt/saic-mqtt-gateway/saic-mqtt-gateway.jar", "-V");
      final List<String> lines = List.of(result.getStdout().split("\n"));
      final String version = lines.get(lines.size() - 1);
      Assertions.assertTrue(
          version.matches("\\d+\\.\\d+\\.\\d+.*"),
          "Version does not match pattern X.X.X: " + version);

      mockServerClient
          .when(request().withMethod("POST").withPath("/TAP.Web/ota.mp"))
          .respond(HttpClassCallback.callback(CallbackV1.class));

      // configure saic endoint
      final var tomlMapper = new TomlMapper();
      final var data =
          tomlMapper.readValue(
              new File(TestUtils.getProjectPath(), "src/dist/etc/saic-mqtt-gateway.toml"),
              Map.class);
      ((Map) data.get("saic"))
          .put("uri", "http://host.testcontainers.internal:" + mockServerClient.getPort());
      ((Map) data.get("mqtt")).put("uri", "tcp://mqtt:1883");
      ((Map) data.get("mqtt")).remove("username");
      ((Map) data.get("mqtt")).remove("password");
      final String toml = tomlMapper.writeValueAsString(data);
      container.copyFileToContainer(Transferable.of(toml), "etc/saic-mqtt-gateway.toml");

      // start the server
      runInContainer(container, "systemctl", "enable", "saic-mqtt-gateway");
      runInContainer(container, "systemctl", "start", "saic-mqtt-gateway");

      runInContainer(container, "systemctl", "status", "saic-mqtt-gateway");

      // check if it did the correct http calls
      await()
          .timeout(60, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                mockServerClient.verify(
                    request().withMethod("POST").withPath("/TAP.Web/ota.mp"),
                    VerificationTimes.atLeast(1));
              });
    }
  }

  public static class CallbackV1 implements ExpectationResponseCallback {

    @Override
    public HttpResponse handle(HttpRequest httpRequest) throws Exception {
      // read message without application data
      final MessageCoder<IASN1PreparedElement> messageCoder = new MessageCoder<>(null);
      final Message<IASN1PreparedElement> message =
          messageCoder.decodeResponse(httpRequest.getBodyAsString());

      switch (message.getBody().getApplicationID()
          + "_"
          + message.getBody().getApplicationDataProtocolVersion()) {
        case "501_513":
          // login
          return HttpResponse.response(
              "0915111007B00C82E60C183060C183060C183060C183060C183060C183060C183060C183060C183072C183060C183060E5CB972E6C39B161CD8B0C1CD860E5CB07362C397361CB97361CD8B16183972E5CD872E6C5872E5AB062C68B06C0040202468ACF1343530ECA864468ACF1342468ACF13420000081A0100A00000F6C39B161CD8B0E56B9B0E5CAD72C18395B62C5872B5CD872E6C3960E5CD860E6C3972E6C39B161CD8B0E56B9B0E5CAD72C18395B62C5872B5CD872E6C3960E5CD860E6C3972E58DB4BE8000FAC58B161CB960E5CD8B0C1CB972E5CAC5A0CA2506614D47169A8E6A408AD8CAC6E8E4D2C6164C19312D0931050D2F0A814115054933FADD876E575534F2CA83872CBCF9F5E59506DDFBB4F4DFCB4EECE839F9E7D32ED598F7E4CAEA5316EB3B61D9D72BA62EF761DB95D52CB9FAECC3C9061D3CB161CFCD663DF932BAA2C182CED87675CAE98BBDD876E5751B96FDDD16F3CB87A20C3A7962C39F9ACC7BF265754583159DB0ECEB95D3177BB0EDCAEA0E9E58B0E741CFBE9E98F42CC7BF265754583959DB0ECEB95D3177BB0EDCAEA9F5DC8296FDF99663DF932BAA6CDAACED87675CAE983BDD876E5754B2EDDFD32A0C7BF774E5BF62CC7BF265754DB3159DB0ECEB95D3177BB0EDCAEA0E9E4831EFDD934F4D3BF769DD9D663DF932BAA8C58ACED87675CAE98BBDD876E57517665C7D3969C68286FEF97920A7D32E5E5A7767598F7E4CAEA2D0A6B3B61D9D72BA62EF761DB95D53CB8FAF2D3D3CA0C3B32F2E8B31EFC995D5382D9A2CED87675CAE983162C58B160C183060C183060C183062C183060C183160C18B062C183060C183062C18B060C183060C183060C183060C183060C183162C1DEEC3B72BA85BF76EEBD1053E987A75E6B31EFC995D429F3A755A8B3B61D9D72BA62EF761DB95D44DFBF920A7D30F4EBCD663DF932BA893E7D259DB0ECEB95D3162C58BBDD876E5750B7EFE8829F4C3D3AF3598F7E4CAEA14F9F51676C3B3AE574C5DEEC3B72BA8BBB3E9DD95053E987A75E6B31EFC995D459D1E4CE8AB3B61D9D72BA62EF761DB95D45D9971F4E5A71A0AD97469C7B32ACC7BF2657516B2CED87675CAE983BDD876E575232E1E997253CB87A2CC7BF26575232E1E997253CB87A2CED87675CAE983BDD876E5752F2F941437F3D3D34EFDCB31EFC995D4B8B6684FA6B3B61D9D72BA62EF761DB95D45DD97967F2839F4C3D32ACC7BF2657516745A51ECACED87675CAE983BDD876E5750B0F4E997979415B7ECE9873E5598F7E4CAEA141A9522D2B2B3B61D9D72BA62EF761DB95D49DDD32F2D3BF920A9976F0CBCB0F4EBCB2ACC7BF26575267548B3682CED87675CAE98BBDD876E57517C74CBCB4EFE482A65DBC32F2C3D3AF2CAB31EFC995D45B1522CDA0B3B61D9D72BA62EF761DB95D57D3BB26FEE829F4C3D3AF3598F7E4CAEABC99D127D759DB0ECEB95D3060C183BDD876E575332E6E8B6969CFA3A2089CB4F6D3BB3ACC7BF265753294459DB0ECEB95D3077BB0EDCAEA16CEB97A6FDFD34209797CACC7BF265750AA4B8B65676C3B3AE574C1DEEC3B72BA8587A74CBCBCA0A9E7865598F7E4CAEA154F3C32ACED87675CAE9931913580262C99B46AD9BB872C060");
        default:
          throw new IllegalArgumentException("Unknown message: " + SaicMqttGateway.toJSON(message));
      }
    }
  }

  private static ExecResult runInContainer(GenericContainer<?> container, String... args)
      throws IOException, InterruptedException {
    ExecResult execResult = container.execInContainer(args);
    LOGGER.error(execResult.getStderr());
    LOGGER.info(execResult.getStdout());
    Assertions.assertEquals(0, execResult.getExitCode());
    return execResult;
  }
}
