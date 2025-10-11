package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Uuid extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "uuid");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUuid");

    private static final byte HASH_MARKER = 19;

    private final UUID value;

    public Uuid(java.lang.String id, List<Extension> extension, java.lang.String value) {
        super(id, extension);
        this.value = value == null ? null : UUID.fromString(value.substring(9));
    }

    public static Uuid create(IPersistentMap m) {
        return new Uuid((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (java.lang.String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.String value() {
        return value == null ? null : "urn:uuid:" + value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value();
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Uri(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uuid assoc(Object key, Object val) {
        if (key == VALUE) return new Uuid(id, extension, (java.lang.String) val);
        if (key == EXTENSION) return new Uuid(id, (List<Extension>) val, value());
        if (key == ID) return new Uuid((java.lang.String) val, extension, value());
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Uuid.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value());
        return appendBase(seq);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value());
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
            sink.putLong(value.getMostSignificantBits());
            sink.putLong(value.getLeastSignificantBits());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Uuid c = (Uuid) o;
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
        return "Uuid{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : "'" + value() + '\'') +
                '}';
    }
}
