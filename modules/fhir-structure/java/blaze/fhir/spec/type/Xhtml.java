package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Objects;

public final class Xhtml extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "xhtml");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Xhtml ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueXhtml");

    private static final byte HASH_MARKER = 20;

    private static final Interner<ExtensionData, Xhtml> INTERNER = Interners.weakInterner(k -> new Xhtml(k, null));
    private static final Xhtml EMPTY = new Xhtml(ExtensionData.EMPTY, null);

    private final String value;

    private Xhtml(ExtensionData extensionData, String value) {
        super(extensionData);
        this.value = value;
    }

    private static Xhtml maybeIntern(ExtensionData extensionData, String value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Xhtml(extensionData, value);
    }

    public static Xhtml create(String value) {
        return value == null ? EMPTY : new Xhtml(ExtensionData.EMPTY, value);
    }

    public static Xhtml create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (String) m.valAt(VALUE));
    }

    public String value() {
        return value;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Xhtml empty() {
        return EMPTY;
    }

    @Override
    public Xhtml assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (String) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public Xhtml withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value);
        } else {
            generator.writeNull();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + Strings.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Xhtml that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Xhtml{" +
                extensionData +
                ", value=" + (value == null ? null : '\'' + value + '\'') +
                '}';
    }
}
