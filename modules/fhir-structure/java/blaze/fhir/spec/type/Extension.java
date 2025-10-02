package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Extension extends Element implements Complex {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Extension");

    private static final Keyword URL = Keyword.intern("url");

    private static final Keyword[] FIELDS = {ID, EXTENSION, URL, VALUE};

    private static final SerializedString FIELD_NAME_URL = new SerializedString("url");

    private static final byte HASH_MARKER = 39;

    private final java.lang.String url;
    private final ExtensionValue value;

    public Extension(java.lang.String id, PersistentVector extension, java.lang.String url, ExtensionValue value) {
        super(id, extension);
        this.url = url;
        this.value = value;
    }

    public static Extension create(IPersistentMap m) {
        return new Extension((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (java.lang.String) m.valAt(URL), (ExtensionValue) m.valAt(VALUE));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "url"),
                Symbol.intern(null, "value"));
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
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == URL) return url;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, URL, url);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new Extension(null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Extension assoc(Object key, Object val) {
        if (key == ID) return new Extension((java.lang.String) val, extension, url, value);
        if (key == EXTENSION) return new Extension(id, (PersistentVector) val, url, value);
        if (key == URL) return new Extension(id, extension, (java.lang.String) val, value);
        if (key == VALUE) return new Extension(id, extension, url, (ExtensionValue) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Extension.");
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (url != null) {
            generator.writeFieldName(FIELD_NAME_URL);
            generator.writeString(url);
        }
        if (value != null) {
            if (value instanceof Primitive primitiveValue) {
                primitiveValue.serializeAsJsonProperty(generator, value.fieldNameExtensionValue());
            } else if (value instanceof Complex complexValue) {
                generator.writeFieldName(value.fieldNameExtensionValue().normal());
                complexValue.serializeAsJsonValue(generator);
            }
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
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", url=" + (url == null ? null : '\'' + url + '\'') +
                ", value=" + value +
                '}';
    }
}
