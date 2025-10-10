package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.DateTimes;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Instant extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "instant");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInstant");

    private static final byte HASH_MARKER = 9;

    private final OffsetDateTime value;

    public Instant(java.lang.String id, List<Extension> extension, OffsetDateTime value) {
        super(id, extension);
        this.value = value;
    }

    public static Instant create(IPersistentMap m) {
        return new Instant((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                (OffsetDateTime) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public OffsetDateTime value() {
        return value;
    }

    @Override
    public String valueAsString() {
        return value == null ? null : DateTimes.toString(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Instant empty() {
        return new Instant(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Instant assoc(Object key, Object val) {
        if (key == VALUE) return new Instant(id, extension, (OffsetDateTime) val);
        if (key == EXTENSION) return new Instant(id, (List<Extension>) val, value);
        if (key == ID) return new Instant((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Instant.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            var appendable = new AsciiByteArrayAppendable(35);
            DateTimes.DATE_TIME.formatTo(value, appendable);
            generator.writeRawUTF8String(appendable.toByteArray(), 0, appendable.length());
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
        Instant that = (Instant) o;
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
        return "Instant{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
