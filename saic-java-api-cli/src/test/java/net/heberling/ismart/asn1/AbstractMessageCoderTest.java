package net.heberling.ismart.asn1;

import static net.heberling.ismart.cli.GetData.toJSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import org.bn.coders.IASN1PreparedElement;
import org.junit.jupiter.api.Assertions;

public class AbstractMessageCoderTest {

    private static final boolean forceOverwrite =
            System.getProperty("forceOverwrite", "false").equalsIgnoreCase("true");

    private static final File examplesDirectory;

    static {
        try {
            CodeSource codeSource =
                    AbstractMessageCoderTest.class.getProtectionDomain().getCodeSource();
            File file = new File(codeSource.getLocation().toURI().getPath());

            while (file.getParentFile() != null && !new File(file, "pom.xml").exists()) {
                file = file.getParentFile();
            }

            examplesDirectory = new File(file.getParentFile(), "docs/examples");
        } catch (URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected <
                    H extends IASN1PreparedElement,
                    B extends IASN1PreparedElement,
                    E extends IASN1PreparedElement,
                    M extends AbstractMessage<H, B, E>>
            M decodeEncode(
                    String messageString,
                    AbstractMessageCoder<H, B, E, M> coder,
                    String... pathComponents) {
        M message = coder.decodeResponse(messageString);

        // make sure the message stays the same
        assertEquals(messageString, coder.encodeRequest(message));

        if (forceOverwrite) {
            Anonymizer.anonymize(message);
            messageString = coder.encodeRequest(message);
        }

        // make sure we match the stored values
        File e = new File(examplesDirectory, "v" + coder.getVersion().replace(".", "_"));
        e.mkdirs();
        try {
            B body = message.getBody();
            String fileName =
                    body.getClass().getMethod("getApplicationID").invoke(body)
                            + "_"
                            + body.getClass()
                                    .getMethod("getApplicationDataProtocolVersion")
                                    .invoke(body);

            if ((boolean) body.getClass().getMethod("isResultPresent").invoke(body)) {
                fileName += "_response";
            } else {
                fileName += "_request";
            }

            for (String pathComponent : pathComponents) {
                fileName += "_" + pathComponent;
            }

            File perFile = new File(e, fileName + ".per");
            if (!forceOverwrite && perFile.exists()) {
                Assertions.assertEquals(
                        Files.readString(perFile.toPath(), StandardCharsets.UTF_8), messageString);
            } else {
                Files.writeString(perFile.toPath(), messageString);
            }

            String json = toJSON(message);

            File jsonFile = new File(e, fileName + ".json");
            if (!forceOverwrite && jsonFile.exists()) {
                Assertions.assertEquals(
                        Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8), json);
            } else {
                Files.writeString(jsonFile.toPath(), json);
            }
        } catch (IOException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }

        // make sure we only have anonymized messages
        Anonymizer.anonymize(message);
        assertEquals(messageString, coder.encodeRequest(message));

        return message;
    }
}
