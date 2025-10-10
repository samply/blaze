package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Integers;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class PositiveInt extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "positiveInt");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valuePositiveInt");

    private static final byte HASH_MARKER = 18;

    private final java.lang.Integer value;

    public PositiveInt(java.lang.String id, List<Extension> extension, java.lang.Integer value) {
        super(id, extension);
        this.value = value;
    }

    public static PositiveInt create(IPersistentMap m) {
        Number value = (Number) m.valAt(VALUE);
        return new PositiveInt((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                value == null ? null : value.intValue());
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.Integer value() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PositiveInt empty() {
        return new PositiveInt(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PositiveInt assoc(Object key, Object val) {
        if (key == VALUE) return new PositiveInt(id, extension, (java.lang.Integer) val);
        if (key == EXTENSION) return new PositiveInt(id, (List<Extension>) val, value);
        if (key == ID) return new PositiveInt((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.PositiveInt.");
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
        PositiveInt c = (PositiveInt) o;
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
        return "PositiveInt{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
