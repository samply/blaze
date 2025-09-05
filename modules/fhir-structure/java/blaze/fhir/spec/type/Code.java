package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Strings;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

public final class Code extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "code");

    private static final byte HASH_MARKER = 13;

    private final java.lang.String value;
    private final SerializedString jsonValue;

    public Code(java.lang.String id, PersistentVector extension, java.lang.String value) {
        super(id, extension);
        this.value = value;
        jsonValue = value == null ? null : new SerializedString(value);
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.String value() {
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
        if (jsonValue != null) {
            generator.writeString(jsonValue);
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
                "id='" + id + '\'' +
                ", extension=" + extension +
                ", value='" + value + '\'' +
                '}';
    }
}
