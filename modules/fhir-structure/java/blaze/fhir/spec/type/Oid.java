package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Oid extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "oid");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueOid");

    private static final byte HASH_MARKER = 14;

    private final java.lang.String value;

    public Oid(java.lang.String id, PersistentVector extension, java.lang.String value) {
        super(id, extension);
        this.value = value;
    }

    public static Oid create(IPersistentMap m) {
        return new Oid((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION), (java.lang.String) m.valAt(VALUE));
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
        return new Oid(null, null, null);
    }

    @Override
    public Oid assoc(Object key, Object val) {
        if (key == VALUE) return new Oid(id, extension, (java.lang.String) val);
        if (key == EXTENSION) return new Oid(id, (PersistentVector) val, value);
        if (key == ID) return new Oid((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Oid.");
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
        Oid c = (Oid) o;
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
        return "Oid{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + (value == null ? null : '\'' + value + '\'') +
                '}';
    }
}
