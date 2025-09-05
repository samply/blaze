package blaze.fhir.spec.type;

import blaze.fhir.spec.Base;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

public final class Extension extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Extension");

    private static final Keyword URL = Keyword.intern("url");

    private static final SerializedString FIELD_NAME_URL = new SerializedString("url");
    private static final SerializedString FIELD_NAME_VALUE_QUANTITY = new SerializedString("valueQuantity");

    private static final byte HASH_MARKER = 39;

    private final java.lang.String url;
    private final Base value;

    public Extension(java.lang.String id, PersistentVector extension, java.lang.String url, Base value) {
        super(id, extension);
        this.url = url;
        this.value = value;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public java.lang.String url() {
        return url;
    }

    public Base value() {
        return value;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == URL) return value;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (url != null) {
            generator.writeFieldName(FIELD_NAME_URL);
            generator.writeString(url);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (url != null) {
            sink.putByte((byte) 2);
            // TODO: should be a System.String hashInto
            new String(null, null, url).hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 3);
            value.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Extension e = (Extension) o;
        return Objects.equals(id, e.id) &&
                Objects.equals(extension, e.extension) &&
                Objects.equals(url, e.url) &&
                Objects.equals(value, e.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, url, value);
    }

    @Override
    public java.lang.String toString() {
        return "Extension{" +
                "id='" + id + '\'' +
                ", extension=" + extension +
                ", url='" + url + '\'' +
                ", value=" + value +
                '}';
    }
}
