package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Base64Binary extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "base64Binary");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBase64Binary");

    private static final byte HASH_MARKER = 8;

    private final java.lang.String value;

    public Base64Binary(java.lang.String id, PersistentVector extension, java.lang.String value) {
        super(id, extension);
        this.value = value;
    }

    public static Base64Binary create(IPersistentMap m) {
        return new Base64Binary((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION), (java.lang.String) m.valAt(VALUE));
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
    public IPersistentCollection empty() {
        return new Base64Binary(null, null, null);
    }

    @Override
    public Base64Binary assoc(Object key, Object val) {
        if (key == VALUE) return new Base64Binary(id, extension, (java.lang.String) val);
        if (key == EXTENSION) return new Base64Binary(id, (PersistentVector) val, value);
        if (key == ID) return new Base64Binary((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Base64Binary.");
    }

    @Override
    public boolean equiv(Object o) {
        return equals(o);
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Base64Binary c = (Base64Binary) o;
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
        return "Base64Binary{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : '\'' + value + '\'') +
                '}';
    }
}
