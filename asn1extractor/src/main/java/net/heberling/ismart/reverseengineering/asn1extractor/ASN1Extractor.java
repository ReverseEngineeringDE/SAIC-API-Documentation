package net.heberling.ismart.reverseengineering.asn1extractor;

import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Boolean;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Element;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Enum;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1EnumItem;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Integer;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1OctetString;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1Sequence;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1SequenceOf;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.ASN1String;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.constraints.ASN1SizeConstraint;
import com.saicmotor.telematics.tsgp.otaadapter.asn.annotations.constraints.ASN1ValueRangeConstraint;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class ASN1Extractor {
    public static void main(String[] args) throws ClassNotFoundException {
        if (args.length < 2) {
            System.err.println("Missing parameters.");
            System.err.println("Usage: asn1extractor <ASN1 Module name> <included classes>");
            System.exit(-1);
        }
        String moduleName = args[0];

        List<Class<?>> classes = new LinkedList<>();
        for (int i = 1; i < args.length; i++) {
            classes.add(ASN1Extractor.class.getClassLoader().loadClass(args[i]));
        }

        String sequenceDefinition = generateASN1Module(moduleName, classes);
        System.out.println(sequenceDefinition);
    }

    static String generateASN1Module(String name, List<Class<?>> classes) {
        StringBuilder sequenceDefinition = new StringBuilder();
        sequenceDefinition
                .append(name)
                .append("\n")
                .append("\n")
                .append("DEFINITIONS\n")
                .append("AUTOMATIC TAGS ::= \n")
                .append("BEGIN\n");
        getASN1Definition(sequenceDefinition, new HashSet<>(), new LinkedList<>(classes));
        sequenceDefinition.append("\nEND");
        return sequenceDefinition.toString();
    }

    private static void getASN1Definition(
            StringBuilder sequenceDefinition, Set<Class<?>> processed, Queue<Class<?>> todo) {
        if (todo.isEmpty()) {
            return;
        }
        Class<?> aClass = todo.remove();
        if (processed.contains(aClass)) {
            return;
        }

        processed.add(aClass);

        if (aClass.isAnnotationPresent(ASN1Enum.class)) {
            ASN1Enum sequence = aClass.getAnnotation(ASN1Enum.class);
            sequenceDefinition.append(sequence.name());
            sequenceDefinition.append(" ::= ENUMERATED\n" + "{\n");
            try {
                String collect =
                        Arrays.stream(
                                        aClass.getDeclaredField("value")
                                                .getType()
                                                .getDeclaredFields())
                                .filter(f -> f.isAnnotationPresent(ASN1EnumItem.class))
                                .sorted(
                                        Comparator.comparingInt(
                                                o -> o.getAnnotation(ASN1EnumItem.class).tag()))
                                .map(
                                        f ->
                                                "  "
                                                        + f.getAnnotation(ASN1EnumItem.class).name()
                                                        + "("
                                                        + f.getAnnotation(ASN1EnumItem.class).tag()
                                                        + ")")
                                .collect(Collectors.joining(",\n"));
                sequenceDefinition.append(collect);

            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            sequenceDefinition.append("\n}\n");

        } else {

            ASN1Sequence sequence = aClass.getAnnotation(ASN1Sequence.class);

            String name = sequence.name();
            if (name.startsWith("mp")) {
                // must be upper case...
                name = "MP" + name.substring(2);
            }
            sequenceDefinition.append(name);
            sequenceDefinition.append(" ::= SEQUENCE\n" + "{\n");
            String collect =
                    Arrays.stream(aClass.getDeclaredFields())
                            .filter(field -> field.isAnnotationPresent(ASN1Element.class))
                            .sorted(
                                    Comparator.comparingInt(
                                            o -> o.getAnnotation(ASN1Element.class).tag()))
                            .map(
                                    f -> {
                                        StringBuilder definition = new StringBuilder("  ");
                                        definition.append(
                                                f.getAnnotation(ASN1Element.class).name());
                                        if (f.isAnnotationPresent(ASN1SequenceOf.class)) {
                                            definition.append(" SEQUENCE SIZE");
                                            addSizeConstraint(f, definition, false);
                                            definition.append(" OF");
                                            // TODO: we only support Sequences for now, but the
                                            // standard also allows primitive types and enums
                                            Class<?> t =
                                                    (Class<?>)
                                                            ((ParameterizedType) f.getGenericType())
                                                                    .getActualTypeArguments()[0];
                                            definition
                                                    .append(" ")
                                                    .append(
                                                            t.getAnnotation(ASN1Sequence.class)
                                                                    .name());
                                            todo.add(t);
                                        } else {

                                            boolean sizeConstraints = false;
                                            if (f.isAnnotationPresent(ASN1Boolean.class)) {
                                                definition.append(" BOOLEAN");
                                            } else if (f.isAnnotationPresent(ASN1Integer.class)) {
                                                definition.append(" INTEGER");
                                            } else if (f.isAnnotationPresent(
                                                    ASN1OctetString.class)) {
                                                definition.append(" OCTET STRING");
                                                sizeConstraints = true;
                                            } else if (f.isAnnotationPresent(ASN1String.class)) {
                                                ASN1String s = f.getAnnotation(ASN1String.class);
                                                switch (s.stringType()) {
                                                    case 18:
                                                        definition.append(" NumericString");
                                                        break;
                                                    case 22:
                                                        definition.append(" IA5String");
                                                        break;
                                                    default:
                                                        throw new RuntimeException(
                                                                "Unsupported string type for "
                                                                        + f
                                                                        + ": "
                                                                        + s.stringType());
                                                }
                                                sizeConstraints = true;
                                            } else if (f.getType()
                                                    .isAnnotationPresent(ASN1Sequence.class)) {
                                                definition
                                                        .append(" ")
                                                        .append(
                                                                f.getType()
                                                                        .getAnnotation(
                                                                                ASN1Sequence.class)
                                                                        .name());
                                                todo.add(f.getType());
                                            } else if (f.getType()
                                                    .isAnnotationPresent(ASN1Enum.class)) {
                                                definition
                                                        .append(" ")
                                                        .append(
                                                                f.getType()
                                                                        .getAnnotation(
                                                                                ASN1Enum.class)
                                                                        .name());
                                                todo.add(f.getType());
                                            } else {
                                                throw new RuntimeException(
                                                        "No type annotation found for "
                                                                + f
                                                                + " in: "
                                                                + Arrays.asList(
                                                                        f.getAnnotations()));
                                            }

                                            addSizeConstraint(f, definition, sizeConstraints);
                                        }
                                        if (f.getAnnotation(ASN1Element.class).isOptional()) {
                                            definition.append(" OPTIONAL");
                                        }

                                        return definition.toString();
                                    })
                            .collect(Collectors.joining(",\n"));

            sequenceDefinition.append(collect);
            sequenceDefinition.append("\n}\n");
        }
        while (!todo.isEmpty()) {
            getASN1Definition(sequenceDefinition, processed, todo);
        }
    }

    private static void addSizeConstraint(
            Field f, StringBuilder definition, boolean finalSizeConstraints) {
        Optional.ofNullable(f.getAnnotation(ASN1ValueRangeConstraint.class))
                .ifPresent(
                        c -> {
                            if (finalSizeConstraints) {
                                definition.append("(SIZE");
                            }
                            definition
                                    .append("(")
                                    .append(c.min())
                                    .append("..")
                                    .append(c.max())
                                    .append(")");
                            if (finalSizeConstraints) {
                                definition.append(")");
                            }
                        });
        Optional.ofNullable(f.getAnnotation(ASN1SizeConstraint.class))
                .ifPresent(
                        c -> {
                            if (finalSizeConstraints) {
                                definition.append("(SIZE");
                            }
                            definition.append("(").append(c.max()).append(")");
                            if (finalSizeConstraints) {
                                definition.append(")");
                            }
                        });
    }
}
