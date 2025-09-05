package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.DateTimes;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.time.temporal.Temporal;
import java.util.Objects;

public final class DateTime extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "dateTime");

    private static final byte HASH_MARKER = 11;

    private final Temporal value;

    public DateTime(java.lang.String id, PersistentVector extension, Temporal value) {
        super(id, extension);
        this.value = value;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Temporal value() {
        return value;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public void serializeJson(JsonGenerator generator) throws IOException {
        if (value != null) {
            generator.writeString(DateTimes.toString(value));
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
                Objects.equals(extension, that.extension) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "DateTime{" +
                "id='" + id + '\'' +
                ", extension=" + extension +
                ", value='" + value + '\'' +
                '}';
    }
}
