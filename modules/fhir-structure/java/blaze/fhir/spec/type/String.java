package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class String extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "string");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueString");

    private static final byte HASH_MARKER = 3;

    private final java.lang.String value;

    public String(java.lang.String id, List<Extension> extension, java.lang.String value) {
        super(id, extension);
        this.value = value;
    }

    public static String create(IPersistentMap m) {
        return new String((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                (java.lang.String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.String value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value);
        return appendBase(seq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new String(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String assoc(Object key, Object val) {
        if (key == VALUE) return new String(id, extension, (java.lang.String) val);
        if (key == EXTENSION) return new String(id, (List<Extension>) val, value);
        if (key == ID) return new String((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.String.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value);
        } else {
            generator.writeNull();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            Strings.hashInto(value, sink);
        }
    }

    /**
     * Creates the hash of a FHIR string with {@code value} without creating the
     * FHIR string first.
     *
     * @param sink the sink for the hash bytes
     * @param value the string value to hash
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void hashIntoValue(PrimitiveSink sink, java.lang.String value) {
        sink.putByte(HASH_MARKER);
        if (value != null) {
            sink.putByte((byte) 2);
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        String c = (String) o;
        return Objects.equals(id, c.id) &&
                Objects.equals(extension, c.extension) &&
                Objects.equals(value, c.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "String{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : '\'' + value + '\'') +
                '}';
    }
}
