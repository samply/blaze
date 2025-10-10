package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.DateTimes;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class DateTime extends Element implements Primitive {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "dateTime");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDateTime");

    private static final byte HASH_MARKER = 11;

    private final Temporal value;
    
    public DateTime(java.lang.String id, List<Extension> extension, Temporal value) {
        super(id, extension);
        this.value = value;
    }

    public static DateTime create(IPersistentMap m) {
        return new DateTime((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                (Temporal) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Temporal value() {
        return value;
    }

    @Override
    public String valueAsString() {
        return value == null ? null : DateTimes.toString(value);
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
        return new DateTime(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DateTime assoc(Object key, Object val) {
        if (key == VALUE) return new DateTime(id, extension, (Temporal) val);
        if (key == EXTENSION) return new DateTime(id, (List<Extension>) val, value);
        if (key == ID) return new DateTime((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.DateTime.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(valueAsString());
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
            DateTimes.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DateTime that = (DateTime) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "DateTime{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
