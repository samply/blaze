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
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public sealed abstract class Extension extends AbstractElement implements Complex
        permits Extension.Normal, Extension.Interned {

    /**
     * Memory size of a not interned Extension.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - url reference
     * 4 or 8 byte - value reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

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
            k -> new Interned(k.extensionData, k.url, k.value)
    );

    private final SerializedString url;
    private final ExtensionValue value;

    private Extension(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
        super(extensionData);
        this.url = url;
        this.value = value;
    }

    private static Extension maybeIntern(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
        if (extensionData.isInterned() && Base.isInterned(value)) {
            return extensionData == ExtensionData.EMPTY && url == null && value == null
                    ? Interned.EMPTY
                    : INTERNER.intern(new InternerKey(extensionData, url, value));
        }
        return new Normal(extensionData, url, value);
    }

    private static SerializedString internUrl(String url) {
        return url == null ? null : URL_INTERNER.intern(url);
    }

    public static Extension create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), internUrl((String) m.valAt(URL)), (ExtensionValue) m.valAt(VALUE));
    }

    /**
     * Creates a Extension from {@code slots} holding the values of the keys of
     * {@link #fields()} at the same index.
     */
    public static Extension fromSlots(Object[] slots) {
        return maybeIntern(ExtensionData.fromSlots(slots), internUrl((String) slots[2]), (ExtensionValue) slots[3]);
    }

    /**
     * Returns the keys of all fields of Extension in slot order.
     */
    public static Keyword[] fields() {
        return FIELDS.clone();
    }

    @Override
    public boolean isInterned() {
        return false;
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
    public Interned empty() {
        return Interned.EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Extension assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, url, (ExtensionValue) val);
        if (key == URL) return maybeIntern(extensionData, internUrl((String) val), value);
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
    public void collectReferences(List<PersistentVector> refs) {
        super.collectReferences(refs);
        Base.collectReferences(value, refs);
    }

    final boolean equalComponents(Extension that) {
        return extensionData.equals(that.extensionData) &&
                Objects.equals(url, that.url) &&
                Objects.equals(value, that.value);
    }

    final int hashComponents() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(url);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public final String toString() {
        return "Extension{" +
                extensionData +
                ", url=" + (url == null ? null : '\'' + url() + '\'') +
                ", value=" + value +
                '}';
    }

    public static final class Normal extends Extension {

        private Normal(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
            super(extensionData, url, value);
        }

        public static Normal create(IPersistentMap m) {
            return new Normal(ExtensionData.fromMap(m), internUrl((String) m.valAt(URL)),
                    (ExtensionValue) m.valAt(VALUE));
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(value());
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Extension that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hashComponents();
        }
    }

    /**
     * An interned Extension.
     * <p>
     * Interned Extensions with equal components are identical, so they are compared by identity. Interned Extensions
     * can still be equal to not interned Extensions, because values like {@link Coding.Normal} and
     * {@link Coding.Interned} with equal components are equal.
     */
    public static final class Interned extends Extension {

        private static final Interned EMPTY = new Interned(ExtensionData.EMPTY, null, null);

        private final int hash;

        private Interned(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
            super(extensionData, url, value);
            this.hash = hashComponents();
        }

        public static Interned create(IPersistentMap m) {
            if (Extension.create(m) instanceof Interned interned) return interned;
            throw new IllegalArgumentException("Can't create an interned FHIR.Extension using non-interned components.");
        }

        @Override
        public boolean isInterned() {
            return true;
        }

        @Override
        public int memSize() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Normal that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Key of the interner, comparing its components by identity.
     * <p>
     * The extension data and the value are interned and the url is always interned by {@link #URL_INTERNER}, so equal
     * components are identical. Because the key references its components strongly, they can't be collected and
     * recreated as different instances while the key exists.
     */
    private static final class InternerKey {

        private final ExtensionData extensionData;
        private final SerializedString url;
        private final ExtensionValue value;
        private final int hash;

        private InternerKey(ExtensionData extensionData, SerializedString url, ExtensionValue value) {
            this.extensionData = requireNonNull(extensionData);
            this.url = url;
            this.value = value;
            int result = System.identityHashCode(extensionData);
            result = 31 * result + System.identityHashCode(url);
            result = 31 * result + System.identityHashCode(value);
            this.hash = result;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InternerKey that &&
                    hash == that.hash &&
                    extensionData == that.extensionData &&
                    url == that.url &&
                    value == that.value;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
