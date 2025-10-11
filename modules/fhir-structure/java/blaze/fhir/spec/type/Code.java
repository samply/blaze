package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Code extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "code");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCode");

    private static final byte HASH_MARKER = 13;

    private final SerializedString value;

    private Code(java.lang.String id, List<Extension> extension, SerializedString value) {
        super(id, extension);
        this.value = value;
    }

    public static Code create(java.lang.String id, List<Extension> extension, java.lang.String value) {
        return new Code(id, extension, value == null ? null : new SerializedString(value));
    }

    public static Code create(IPersistentMap m) {
        var value =  m.valAt(VALUE);
        return new Code((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), value == null ? null :
                new SerializedString((java.lang.String) value));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned();
    }

    public java.lang.String value() {
        return value == null ? null : value.getValue();
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value == null ? null : value.getValue();
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value == null ? null : value.getValue());
        return appendBase(seq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Code(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Code assoc(Object key, Object val) {
        if (key == VALUE) return new Code(id, extension, val == null ? null : new SerializedString((java.lang.String) val));
        if (key == EXTENSION) return new Code(id, (List<Extension>) val, value);
        if (key == ID) return new Code((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Code.");
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
            Strings.hashInto(value.getValue(), sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Code c = (Code) o;
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
        return "Code{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value='" + value + '\'' +
                '}';
    }
}
