package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Integers;
import blaze.fhir.spec.type.system.Longs;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Integer extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "integer");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInteger");

    private static final byte HASH_MARKER = 1;

    private final java.lang.Integer value;

    public Integer(java.lang.String id, PersistentVector extension, java.lang.Integer value) {
        super(id, extension);
        this.value = value;
    }

    public static Integer create(IPersistentMap m) {
        Number value = (Number) m.valAt(VALUE);
        return new Integer((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION), value == null ? null : value.intValue());
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
        return new Integer(null, null, null);
    }

    @Override
    public Integer assoc(Object key, Object val) {
        if (key == VALUE) return new Integer(id, extension, (java.lang.Integer) val);
        if (key == EXTENSION) return new Integer(id, (PersistentVector) val, value);
        if (key == ID) return new Integer((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Integer.");
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
        Integer c = (Integer) o;
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
        return "Integer{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
