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
public final class Range extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - low reference
     * 4 or 8 byte - high reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Range");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Range ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk LOW_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Range r ? r.low : this;
        }
    };

    private static final ILookupThunk HIGH_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Range r ? r.high : this;
        }
    };

    private static final Keyword LOW = RT.keyword(null, "low");
    private static final Keyword HIGH = RT.keyword(null, "high");

    private static final Keyword[] FIELDS = {ID, EXTENSION, LOW, HIGH};

    private static final FieldName FIELD_NAME_LOW = FieldName.of("low");
    private static final FieldName FIELD_NAME_HIGH = FieldName.of("high");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRange");

    private static final byte HASH_MARKER = 50;

    private static final Interner<ExtensionData, Range> INTERNER = Interners.weakInterner(k -> new Range(k, null, null, true));
    private static final Range EMPTY = new Range(ExtensionData.EMPTY, null, null, true);

    private final Quantity low;
    private final Quantity high;
    private final boolean interned;

    private Range(ExtensionData extensionData, Quantity low, Quantity high, boolean interned) {
        super(extensionData);
        this.low = low;
        this.high = high;
        this.interned = interned;
    }

    private static Range maybeIntern(ExtensionData extensionData, Quantity low, Quantity high) {
        return extensionData.isInterned() && low == null && high == null
                ? INTERNER.intern(extensionData)
                : new Range(extensionData, low, high, false);
    }

    public static Range create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Quantity) m.valAt(LOW), (Quantity) m.valAt(HIGH));
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public Quantity low() {
        return low;
    }

    public Quantity high() {
        return high;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == LOW) return LOW_LOOKUP_THUNK;
        if (key == HIGH) return HIGH_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == LOW) return low;
        if (key == HIGH) return high;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, HIGH, high);
        seq = appendElement(seq, LOW, low);
        return extensionData.append(seq);
    }

    @Override
    public Range empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Range assoc(Object key, Object val) {
        if (key == LOW) return maybeIntern(extensionData, (Quantity) val, high);
        if (key == HIGH) return maybeIntern(extensionData, low, (Quantity) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), low, high);
        if (key == ID) return maybeIntern(extensionData.withId(val), low, high);
        return this;
    }

    @Override
    public Range withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), low, high);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (low != null) {
            low.serializeJsonField(generator, FIELD_NAME_LOW);
        }
        if (high != null) {
            high.serializeJsonField(generator, FIELD_NAME_HIGH);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (low != null) {
            sink.putByte((byte) 2);
            low.hashInto(sink);
        }
        if (high != null) {
            sink.putByte((byte) 3);
            high.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(low) + Base.memSize(high);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Range that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(low, that.low) &&
                Objects.equals(high, that.high);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(low);
        result = 31 * result + Objects.hashCode(high);
        return result;
    }

    @Override
    public String toString() {
        return "Range{" + extensionData + ", low=" + low + ", high=" + high + '}';
    }
}
