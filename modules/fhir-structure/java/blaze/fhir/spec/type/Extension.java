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
import java.util.Objects;
import java.util.stream.Stream;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class Extension extends AbstractElement implements Complex {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - url reference
     * 4 or 8 byte - value reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Extension");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Extension ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk URL_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Extension e ? e.url() : this;
        }
    };

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Extension e ? e.value : this;
        }
    };

    private static final Keyword URL = RT.keyword(null, "url");
    private static final Keyword VALUE = RT.keyword(null, "value");

    private static final Keyword[] FIELDS = {ID, EXTENSION, URL, VALUE};

    private static final FieldName FIELD_NAME_URL = FieldName.of("url");

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
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == URL) return URL_LOOKUP_THUNK;
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == VALUE) return value;
        if (key == URL) return url();
        return super.valAt(key, notFound);
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
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Extension assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, url, (ExtensionValue) val);
        if (key == URL)
            return maybeIntern(extensionData, val == null ? null : URL_INTERNER.intern((String) val), value);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), url, value);
        if (key == ID) return maybeIntern(extensionData.withId(val), url, value);
        return this;
    }

    @Override
    public Extension withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), url, value);
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (url != null) {
            generator.writeFieldName(FIELD_NAME_URL.normal());
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
