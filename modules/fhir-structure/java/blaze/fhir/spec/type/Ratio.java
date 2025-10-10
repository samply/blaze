package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Ratio extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - numerator reference
     * 4 or 8 byte - denominator reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Ratio");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Ratio ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk NUMERATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Ratio r ? r.numerator : this;
        }
    };

    private static final ILookupThunk DENOMINATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Ratio r ? r.denominator : this;
        }
    };

    private static final Keyword NUMERATOR = RT.keyword(null, "numerator");
    private static final Keyword DENOMINATOR = RT.keyword(null, "denominator");

    private static final Keyword[] FIELDS = {ID, EXTENSION, NUMERATOR, DENOMINATOR};

    private static final FieldName FIELD_NAME_NUMERATOR = FieldName.of("numerator");
    private static final FieldName FIELD_NAME_DENOMINATOR = FieldName.of("denominator");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRatio");

    private static final byte HASH_MARKER = 48;

    private static final Interner<ExtensionData, Ratio> INTERNER = Interners.weakInterner(k -> new Ratio(k, null, null, true));
    private static final Ratio EMPTY = new Ratio(ExtensionData.EMPTY, null, null, true);

    private final Quantity numerator;
    private final Quantity denominator;
    private final boolean interned;

    private Ratio(ExtensionData extensionData, Quantity numerator, Quantity denominator, boolean interned) {
        super(extensionData);
        this.numerator = numerator;
        this.denominator = denominator;
        this.interned = interned;
    }

    private static Ratio maybeIntern(ExtensionData extensionData, Quantity numerator, Quantity denominator) {
        return extensionData.isInterned() && numerator == null && denominator == null
                ? INTERNER.intern(extensionData)
                : new Ratio(extensionData, numerator, denominator, false);
    }

    public static Ratio create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Quantity) m.valAt(NUMERATOR), (Quantity) m.valAt(DENOMINATOR));
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public Quantity numerator() {
        return numerator;
    }

    public Quantity denominator() {
        return denominator;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == NUMERATOR) return NUMERATOR_LOOKUP_THUNK;
        if (key == DENOMINATOR) return DENOMINATOR_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == NUMERATOR) return numerator;
        if (key == DENOMINATOR) return denominator;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DENOMINATOR, denominator);
        seq = appendElement(seq, NUMERATOR, numerator);
        return extensionData.append(seq);
    }

    @Override
    public Ratio empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Ratio assoc(Object key, Object val) {
        if (key == NUMERATOR) return maybeIntern(extensionData, (Quantity) val, denominator);
        if (key == DENOMINATOR) return maybeIntern(extensionData, numerator, (Quantity) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), numerator, denominator);
        if (key == ID) return maybeIntern(extensionData.withId(val), numerator, denominator);
        return this;
    }

    @Override
    public Ratio withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), numerator, denominator);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (numerator != null) {
            numerator.serializeJsonField(generator, FIELD_NAME_NUMERATOR);
        }
        if (denominator != null) {
            denominator.serializeJsonField(generator, FIELD_NAME_DENOMINATOR);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (numerator != null) {
            sink.putByte((byte) 2);
            numerator.hashInto(sink);
        }
        if (denominator != null) {
            sink.putByte((byte) 3);
            denominator.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(numerator) + Base.memSize(denominator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Ratio that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(numerator, that.numerator) &&
                Objects.equals(denominator, that.denominator);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(numerator);
        result = 31 * result + Objects.hashCode(denominator);
        return result;
    }

    @Override
    public String toString() {
        return "Ratio{" + extensionData + ", numerator=" + numerator + ", denominator=" + denominator + '}';
    }
}
