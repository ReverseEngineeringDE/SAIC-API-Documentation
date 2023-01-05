package net.heberling.ismart.gateway;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class App extends NanoHTTPD {

  public long cooldownAccessTime;
  public int cooldownSeconds = 600;
  public String cachedApiResponse;

  public App() throws IOException {
    super(42042);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    System.out.println(
        "\n"
            + "SAIC-API Gateway running! Access the API endpoints via"
            + " http://localhost:42042/ \n");
  }

  public static void main(String[] args) {
    try {
      new App();
    } catch (IOException ioe) {
      System.err.println("Couldn't start server:\n" + ioe);
    }
  }

  @Override
  public Response serve(IHTTPSession session) {
    String msg = "";
    Map<String, String> content = new HashMap<>();
    Method method = session.getMethod();
    if (Method.PUT.equals(method) || Method.POST.equals(method))
      try {
        session.parseBody(content);
      } catch (IOException ioe) {
        System.out.println("unable to parse body of request");
      } catch (ResponseException re) {
        System.out.println("unable to parse body of request");
      }

    // getData with 600 seconds API request cooldown
    if (session.getUri().equals("/getData")) {
      System.out.println("Accessed: /getData");
      String user = session.getParms().get("user");
      String password = session.getParms().get("password");
      String skipCooldown = session.getParms().get("skipCooldown");

      if (user != null && password != null) {
        String[] loginData = {user, password};

        if (Objects.equals(skipCooldown, "true")) {
          System.out.println("Cooldown was skipped via post-parameter 'skipCooldown'");
          cooldownAccessTime = 0;
        }

        // the cooldown prevents 12v battery drain of the MG5
        // cooldown can be skipped with post-parameter 'skippedCooldown' set to true
        // TODO check if the vehicle is turned on or charging (no 12v battery drain if
        // vehicle is turned on)
        if ((Instant.now().getEpochSecond() - cooldownAccessTime) >= cooldownSeconds) {
          System.out.println(
              "Cooldown of " + cooldownSeconds + " seconds is over and API request will be sent.");
          cooldownAccessTime = Instant.now().getEpochSecond();

          try {

            String[] jsonOutput = new String[4];

            jsonOutput = GetData.startResponse(loginData);

            cachedApiResponse = jsonOutput[3];

          } catch (IOException e) {
            throw new RuntimeException(e);
          }

        } else {
          long secondsUntilNextRequest =
              (Instant.now().getEpochSecond() - cooldownAccessTime) - cooldownSeconds;
          secondsUntilNextRequest = Math.abs(secondsUntilNextRequest);
          System.out.println(
              "Cached output of vehicleResponse (implemented to reduce 12v battery"
                  + " drain). Cooldown seconds to next API request: "
                  + secondsUntilNextRequest);
        }
        msg += cachedApiResponse;
      } else {
        System.out.println("No login post parameters set (user, password).");
        msg += "No login post parameters set (user, password).";
      }

    } else {
      msg += "API error";
    }
    return newFixedLengthResponse(msg);
  }
}
