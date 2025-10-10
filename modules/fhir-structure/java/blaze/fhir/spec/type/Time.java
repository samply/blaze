package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.DateTimes;
import blaze.fhir.spec.type.system.Times;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Time extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "time");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueTime");

    private static final byte HASH_MARKER = 12;

    private final LocalTime value;

    public Time(java.lang.String id, List<Extension> extension, LocalTime value) {
        super(id, extension);
        this.value = value;
    }

    public static Time create(IPersistentMap m) {
        return new Time((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (LocalTime) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public LocalTime value() {
        return value;
    }

    @Override
    public String valueAsString() {
        return value == null ? null : Times.toString(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Time empty() {
        return new Time(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Time assoc(Object key, Object val) {
        if (key == VALUE) return new Time(id, extension, (LocalTime) val);
        if (key == EXTENSION) return new Time(id, (List<Extension>) val, value);
        if (key == ID) return new Time((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Time.");
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
            sink.putByte((byte) 7);
            sink.putInt(value.getHour());
            sink.putInt(value.getMinute());
            sink.putInt(value.getSecond());
            sink.putInt(value.getNano());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Time that = (Time) o;
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
        return "Time{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
