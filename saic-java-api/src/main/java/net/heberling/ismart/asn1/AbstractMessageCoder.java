package net.heberling.ismart.asn1;

import java.io.InputStream;
import java.io.OutputStream;
import org.bn.annotations.constraints.ASN1SizeConstraint;
import org.bn.annotations.constraints.ASN1ValueRangeConstraint;
import org.bn.coders.CoderUtils;
import org.bn.coders.DecodedObject;
import org.bn.coders.ElementInfo;
import org.bn.coders.IASN1PreparedElement;
import org.bn.coders.per.PERUnalignedDecoder;
import org.bn.coders.per.PERUnalignedEncoder;
import org.bn.metadata.constraints.ASN1SizeConstraintMetadata;
import org.bn.metadata.constraints.ASN1ValueRangeConstraintMetadata;
import org.bn.metadata.constraints.IASN1ConstraintMetadata;
import org.bn.utils.BitArrayInputStream;
import org.bn.utils.BitArrayOutputStream;

public abstract class AbstractMessageCoder<
    H extends IASN1PreparedElement,
    B extends IASN1PreparedElement,
    E extends IASN1PreparedElement,
    M extends AbstractMessage<H, B, E>> {
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private final Class<E> applicationDataClass;

  protected AbstractMessageCoder(Class<E> applicationDataClass) {
    this.applicationDataClass = applicationDataClass;
  }

  public static byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }

  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static boolean isNumericString(ElementInfo elementInfo) {
    return CoderUtils.getStringTagForElement(elementInfo) == 18;
  }

  public abstract String encodeRequest(M message);

  public abstract M decodeResponse(String message);

  public abstract M initializeMessage(
      String uid,
      String token,
      String vin,
      String applicationID,
      int applicationDataProtocolVersion,
      int messageID,
      E applicationData);

  public abstract String getVersion();

  public Class<E> getApplicationDataClass() {
    return applicationDataClass;
  }

  public static class MyPERUnalignedEncoder extends PERUnalignedEncoder {

    @Override
    public int encodeString(Object obj, OutputStream outputStream, ElementInfo elementInfo)
        throws Exception {
      // upstream is missing support for numeric strings
      if (!AbstractMessageCoder.isNumericString(elementInfo)) {
        return super.encodeString(obj, outputStream, elementInfo);
      }
      byte[] bytes = obj.toString().getBytes();
      int encodeLength = encodeLength(bytes.length, elementInfo, outputStream);
      if (bytes.length == 0) {
        return encodeLength;
      }
      BitArrayOutputStream bitArrayOutputStream = (BitArrayOutputStream) outputStream;
      if (AbstractMessageCoder.isNumericString(elementInfo)) {
        for (int b : bytes) {
          if (b == 32) {
            // space
            b = 0;
          } else if (b >= 48 && b <= 57) {
            // digit
            b = b - 47;
          } else {
            // other character, ignore
            continue;
          }

          bitArrayOutputStream.writeBits(b, 4);
          encodeLength++;
        }
      }
      return encodeLength;
    }

    public int encodeLength(int value, ElementInfo elementInfo, OutputStream stream)
        throws Exception {
      int resultSize = 0;
      BitArrayOutputStream bitStream = (BitArrayOutputStream) stream;
      CoderUtils.checkConstraints(value, elementInfo);
      if (elementInfo.hasPreparedInfo()) {
        if (elementInfo.getPreparedInfo().hasConstraint()) {
          IASN1ConstraintMetadata constraint = elementInfo.getPreparedInfo().getConstraint();
          if (constraint instanceof ASN1ValueRangeConstraintMetadata) {
            resultSize +=
                encodeConstraintLengthDeterminant(
                    value,
                    (int) ((ASN1ValueRangeConstraintMetadata) constraint).getMin(),
                    (int) ((ASN1ValueRangeConstraintMetadata) constraint).getMax(),
                    bitStream);
          } else if (constraint instanceof ASN1SizeConstraintMetadata) {
            // exact size, don't need to encode it in the stream
            resultSize += (int) ((ASN1SizeConstraintMetadata) constraint).getMax();
          }
        } else {
          resultSize += encodeLengthDeterminant(value, bitStream);
        }
      } else if (elementInfo
          .getAnnotatedClass()
          .isAnnotationPresent(ASN1ValueRangeConstraint.class)) {
        ASN1ValueRangeConstraint constraint =
            elementInfo.getAnnotatedClass().getAnnotation(ASN1ValueRangeConstraint.class);
        resultSize +=
            encodeConstraintLengthDeterminant(
                value, (int) constraint.min(), (int) constraint.max(), bitStream);
      } else if (elementInfo.getAnnotatedClass().isAnnotationPresent(ASN1SizeConstraint.class)) {
        ASN1SizeConstraint constraint =
            elementInfo.getAnnotatedClass().getAnnotation(ASN1SizeConstraint.class);
        resultSize += (int) constraint.max();
      } else {
        resultSize += encodeLengthDeterminant(value, bitStream);
      }
      return resultSize;
    }
  }

  public static class MyPERUnalignedDecoder extends PERUnalignedDecoder {

    public DecodedObject<String> decodeString(
        DecodedObject<Integer> decodedTag2,
        Class objectClass,
        ElementInfo elementInfo,
        InputStream stream)
        throws Exception {
      // upstream is missing support for numeric strings
      if (!AbstractMessageCoder.isNumericString(elementInfo)) {
        return super.decodeString(decodedTag2, objectClass, elementInfo, stream);
      } else {
        DecodedObject<String> decodedTag = new DecodedObject<>();
        int decodeLength = this.decodeLength(elementInfo, stream);
        if (decodeLength <= 0) {
          decodedTag.setValue("");
        } else {
          BitArrayInputStream bitArrayInputStream = (BitArrayInputStream) stream;
          byte[] decodedTagBytes = new byte[decodeLength];
          for (int i = 0; i < decodeLength; i++) {
            byte decodedTagByte = (byte) bitArrayInputStream.readBits(4);
            if (decodedTagByte > 0 && decodedTagByte <= 10) {
              decodedTagBytes[i] = (byte) ((byte) (decodedTagByte + 47));
            } else {
              decodedTagBytes[i] = (byte) 32;
            }
          }

          decodedTag.setValue(new String(decodedTagBytes));
        }
        return decodedTag;
      }
    }
  }
}
