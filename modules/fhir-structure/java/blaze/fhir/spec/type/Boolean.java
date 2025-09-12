package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Booleans;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Boolean extends Element implements Primitive {

    public static final Boolean TRUE = new Boolean(null, null, java.lang.Boolean.TRUE);
    public static final Boolean FALSE = new Boolean(null, null, java.lang.Boolean.FALSE);

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "boolean");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBoolean");

    private static final byte HASH_MARKER = 0;

    private final java.lang.Boolean value;

    public Boolean(java.lang.String id, PersistentVector extension, java.lang.Boolean value) {
        super(id, extension);
        this.value = value;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
        System.out.println("seq = " + seq);
        return seq;
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
