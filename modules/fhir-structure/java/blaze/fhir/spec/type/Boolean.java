package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Booleans;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Boolean extends Element implements Primitive {

    @SuppressWarnings("unchecked")
    public static final Boolean TRUE = new Boolean(null, PersistentList.EMPTY, java.lang.Boolean.TRUE);
    @SuppressWarnings("unchecked")
    public static final Boolean FALSE = new Boolean(null, PersistentList.EMPTY, java.lang.Boolean.FALSE);

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "boolean");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBoolean");

    private static final byte HASH_MARKER = 0;

    private final java.lang.Boolean value;

    public Boolean(java.lang.String id, List<Extension> extension, java.lang.Boolean value) {
        super(id, extension);
        this.value = value;
    }

    public static Boolean create(IPersistentMap m) {
        return new Boolean((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (java.lang.Boolean) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned();
    }

    public java.lang.Boolean value() {
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
        seq = appendBase(seq);
        return seq;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Boolean(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Boolean assoc(Object key, Object val) {
        if (key == VALUE) return new Boolean(id, extension, (java.lang.Boolean) val);
        if (key == EXTENSION) return new Boolean(id, (List<Extension>) val, value);
        if (key == ID) return new Boolean((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Boolean.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeBoolean(value);
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
            Booleans.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Boolean c = (Boolean) o;
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
        return "Boolean{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
