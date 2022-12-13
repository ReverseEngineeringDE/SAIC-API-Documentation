package net.heberling.ismart.asn1;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.bn.coders.IASN1PreparedElement;

public class Anonymizer {
    public static void anonymize(AbstractMessage<?, ?, ?> message) {
        try {
            for (Field field : message.getClass().getDeclaredFields()) {
                if (field.getName().equals("reserved")) {
                    field.setAccessible(true);
                    // Don't know what this is for, better redact
                    Arrays.fill((byte[]) field.get(message), (byte) 0);
                }
            }

            anonymize(message.getBody());
            if (message.getApplicationData() != null) {
                anonymize(message.getApplicationData());
            }

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void anonymize(IASN1PreparedElement element) throws IllegalAccessException {
        for (Field field : element.getClass().getDeclaredFields()) {
            // make all fields accessible
            field.setAccessible(true);
            if (field.get(element) != null) {
                // only replace actually filled fields
                if (IASN1PreparedElement.class.isAssignableFrom(field.getType())) {
                    // replace nested structures
                    anonymize((IASN1PreparedElement) field.get(element));
                } else if (Collection.class.isAssignableFrom(field.getType())) {
                    // replace collections
                    Collection<?> c = (Collection<?>) field.get(element);
                    for (Object o : c) {
                        if (o instanceof IASN1PreparedElement) {
                            anonymize((IASN1PreparedElement) o);
                        }
                    }
                } else {
                    // replace identifying values
                    switch (field.getName()) {
                        case "password":
                            // replace everything with *
                            field.set(element, ((String) field.get(element)).replaceAll(".", "*"));
                            break;
                        case "email":
                        case "uid":
                        case "pin":
                        case "token":
                        case "refreshToken":
                        case "vin":
                            // replace all letters with X and all numbers > 1 with 9, keep
                            // everything
                            field.set(element, anonymizeString((String) field.get(element)));
                            break;
                        case "deviceId":
                            // replace all letters with X and all numbers > 1 with 9, keep
                            // everything
                            // after ###
                            String[] s = ((String) field.get(element)).split("###");
                            field.set(element, anonymizeString(s[0]) + "###" + s[1]);
                            break;
                        case "seconds":
                        case "bindTime":
                        case "eventCreationTime":
                        case "latitude":
                        case "longitude":
                            if (field.get(element) instanceof Long) {
                                field.set(element, ((Long) field.get(element)) / 100000 * 100000);
                            } else {
                                field.set(
                                        element, ((Integer) field.get(element)) / 100000 * 100000);
                            }
                            break;
                        case "eventID":
                        case "lastKeySeen":
                            field.set(element, 9999);
                            break;
                        case "content":
                            field.set(
                                    element,
                                    new String((byte[]) field.get(element), StandardCharsets.UTF_8)
                                            .replaceAll("\\(\\*\\*\\*...\\)", "(***XXX)")
                                            .getBytes(StandardCharsets.UTF_8));
                            break;
                    }
                }
            }
        }
    }

    private static String anonymizeString(String s) {
        return s.replaceAll("[a-zA-Z]", "X").replaceAll("[1-9]", "9");
    }
}
