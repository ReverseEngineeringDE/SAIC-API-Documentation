package net.heberling.ismart.cli;

import com.owlike.genson.stream.JsonType;
import com.owlike.genson.stream.ObjectWriter;
import java.nio.charset.StandardCharsets;

public class UTF8StringObjectWriter implements ObjectWriter {
    private final ObjectWriter delegate;

    private String utf8EncodedByteArrayName;

    public UTF8StringObjectWriter(ObjectWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public ObjectWriter beginArray() {
        return delegate.beginArray();
    }

    @Override
    public ObjectWriter endArray() {
        return delegate.endArray();
    }

    @Override
    public ObjectWriter beginObject() {
        return delegate.beginObject();
    }

    @Override
    public ObjectWriter endObject() {
        return delegate.endObject();
    }

    @Override
    public ObjectWriter writeName(String name) {
        return delegate.writeName(name);
    }

    @Override
    public ObjectWriter writeEscapedName(char[] name) {
        final String nameString = String.valueOf(name);
        if (nameString.equals("content")
                || nameString.equals("brandName")
                || nameString.equals("colorName")
                || nameString.equals("modelName")
                || nameString.equals("sender")
                || nameString.equals("title")
                || nameString.equals("errorMessage")
                || nameString.equals("rvcReqType")
                || nameString.equals("paramValue")) {
            // Some fields are really UTF8 strings, but the ASN.1 schema declares them as byte
            // arrays. We want to see the plain text additionally to the HEX String in the JSON
            utf8EncodedByteArrayName = "@" + nameString + "UTF8";
        }
        return delegate.writeEscapedName(name);
    }

    @Override
    public ObjectWriter writeValue(int value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeValue(double value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeValue(long value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeValue(short value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeValue(float value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeValue(boolean value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeBoolean(Boolean value) {
        return delegate.writeBoolean(value);
    }

    @Override
    public ObjectWriter writeValue(Number value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeNumber(Number value) {
        return delegate.writeNumber(value);
    }

    @Override
    public ObjectWriter writeValue(String value) {
        return delegate.writeValue(value);
    }

    @Override
    public ObjectWriter writeString(String value) {
        return delegate.writeString(value);
    }

    @Override
    public ObjectWriter writeValue(byte[] value) {
        final ObjectWriter writer = delegate.writeValue(value);
        if (utf8EncodedByteArrayName != null) {
            writer.writeEscapedName(utf8EncodedByteArrayName.toCharArray());
            writer.writeString(new String(value, StandardCharsets.UTF_8));
            utf8EncodedByteArrayName = null;
        }
        return writer;
    }

    @Override
    public ObjectWriter writeBytes(byte[] value) {
        return delegate.writeBytes(value);
    }

    @Override
    public ObjectWriter writeUnsafeValue(String value) {
        return delegate.writeUnsafeValue(value);
    }

    @Override
    public ObjectWriter writeNull() {
        utf8EncodedByteArrayName = null;
        return delegate.writeNull();
    }

    @Override
    public ObjectWriter beginNextObjectMetadata() {
        return delegate.beginNextObjectMetadata();
    }

    @Override
    public ObjectWriter writeMetadata(String name, String value) {
        return delegate.writeMetadata(name, value);
    }

    @Override
    public ObjectWriter writeBoolean(String name, Boolean value) {
        return delegate.writeBoolean(name, value);
    }

    @Override
    public ObjectWriter writeNumber(String name, Number value) {
        return delegate.writeNumber(name, value);
    }

    @Override
    public ObjectWriter writeString(String name, String value) {
        return delegate.writeString(name, value);
    }

    @Override
    public ObjectWriter writeBytes(String name, byte[] value) {
        return delegate.writeBytes(name, value);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public JsonType enclosingType() {
        return delegate.enclosingType();
    }
}
