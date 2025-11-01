package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class Extension extends AbstractElement implements Complex {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - url reference
     * 4 byte - value reference
     * 1 byte - interned boolean
     * 3 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Extension");

    private static final Keyword URL = Keyword.intern("url");

    private static final Keyword[] FIELDS = {ID, EXTENSION, URL, VALUE};

    private static final SerializedString FIELD_NAME_URL = new SerializedString("url");

    private static final byte HASH_MARKER = 39;

    private static final Interner<String, SerializedString> URL_INTERNER = Interners.weakInterner(SerializedString::new);
    private static final Interner<InternerKey, Extension> INTERNER = Interners.weakInterner(
            k -> new Extension(k.extensionData, k.url, k.value, true)
    );
    private static final Extension EMPTY = new Extension(ExtensionData.EMPTY, null, null, true);

    private final SerializedString url;
    private final ExtensionValue value;
    private final boolean interned;

    private Extension(ExtensionData extensionData, SerializedString url, ExtensionValue value, boolean interned) {
        super(extensionData);
        this.url = url;
        this.value = value;
        this.interned = interned;
    }

    private static Extension maybeIntern(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
        return extensionData.isInterned() && Base.isInterned(value)
                ? INTERNER.intern(new InternerKey(extensionData, url, value))
                : new Extension(extensionData, url, value, false);
    }

    public static Extension create(IPersistentMap m) {
        var url = (String) m.valAt(URL);
        return maybeIntern(ExtensionData.fromMap(m), url == null ? null : URL_INTERNER.intern(url),
                (ExtensionValue) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public String url() {
        return url == null ? null : url.getValue();
    }

    public Base value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == VALUE) return value;
        if (key == URL) return url();
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, URL, url());
        return extensionData.append(seq);
    }

    @Override
    public Extension empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Extension assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, url, (ExtensionValue) val);
        if (key == URL)
            return maybeIntern(extensionData, val == null ? null : URL_INTERNER.intern((String) val), value);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), url, value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), url, value);
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
            value.serializeJsonField(generator, value.fieldNameExtensionValue());
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings({"UnstableApiUsage"})
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (url != null) {
            sink.putByte((byte) 2);
            // for compatibility reasons, we use the hash signature of a FHIR.String instead of a System.String
            blaze.fhir.spec.type.String.hashIntoValue(sink, url());
        }
        if (value != null) {
            sink.putByte((byte) 3);
            value.hashInto(sink);
        }
    }

    @Override
    public Stream<PersistentVector> references() {
        return value == null ? super.references() : Stream.concat(super.references(), value.references());
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Extension that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(url, that.url) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(url);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "Extension{" +
                extensionData +
                ", url=" + (url == null ? null : '\'' + url() + '\'') +
                ", value=" + value +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
