package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Integers;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class UnsignedInt extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "unsignedInt");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUnsignedInt");

    private static final byte HASH_MARKER = 17;

    private final java.lang.Integer value;

    public UnsignedInt(java.lang.String id, PersistentVector extension, java.lang.Integer value) {
        super(id, extension);
        this.value = value;
    }

    public static UnsignedInt create(IPersistentMap m) {
        Number value = (Number) m.valAt(VALUE);
        return new UnsignedInt((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION), value == null ? null : value.intValue());
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.Integer value() {
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
        return new UnsignedInt(null, null, null);
    }

    @Override
    public UnsignedInt assoc(Object key, Object val) {
        if (key == VALUE) return new UnsignedInt(id, extension, (java.lang.Integer) val);
        if (key == EXTENSION) return new UnsignedInt(id, (PersistentVector) val, value);
        if (key == ID) return new UnsignedInt((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.UnsignedInt.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeNumber(value);
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
            Integers.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UnsignedInt c = (UnsignedInt) o;
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
        return "UnsignedInt{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
