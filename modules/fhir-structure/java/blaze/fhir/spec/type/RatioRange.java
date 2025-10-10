package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class RatioRange extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - lowNumerator reference
     * 4 or 8 byte - highNumerator reference
     * 4 or 8 byte - denominator reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 4 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "RatioRange");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof RatioRange ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk LOW_NUMERATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof RatioRange r ? r.lowNumerator : this;
        }
    };

    private static final ILookupThunk HIGH_NUMERATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof RatioRange r ? r.highNumerator : this;
        }
    };

    private static final ILookupThunk DENOMINATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof RatioRange r ? r.denominator : this;
        }
    };

    private static final Keyword LOW_NUMERATOR = RT.keyword(null, "lowNumerator");
    private static final Keyword HIGH_NUMERATOR = RT.keyword(null, "highNumerator");
    private static final Keyword DENOMINATOR = RT.keyword(null, "denominator");

    private static final Keyword[] FIELDS = {ID, EXTENSION, LOW_NUMERATOR, HIGH_NUMERATOR, DENOMINATOR};

    private static final FieldName FIELD_NAME_LOW_NUMERATOR = FieldName.of("lowNumerator");
    private static final FieldName FIELD_NAME_HIGH_NUMERATOR = FieldName.of("highNumerator");
    private static final FieldName FIELD_NAME_DENOMINATOR = FieldName.of("denominator");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueRatioRange");

    private static final byte HASH_MARKER = 51;

    private static final RatioRange EMPTY = new RatioRange(ExtensionData.EMPTY, null, null, null);

    private final Quantity lowNumerator;
    private final Quantity highNumerator;
    private final Quantity denominator;

    private RatioRange(ExtensionData extensionData, Quantity lowNumerator, Quantity highNumerator, Quantity denominator) {
        super(extensionData);
        this.lowNumerator = lowNumerator;
        this.highNumerator = highNumerator;
        this.denominator = denominator;
    }

    public static RatioRange create(IPersistentMap m) {
        return new RatioRange(ExtensionData.fromMap(m), (Quantity) m.valAt(LOW_NUMERATOR), (Quantity) m.valAt(HIGH_NUMERATOR),
                (Quantity) m.valAt(DENOMINATOR));
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned() && Base.isInterned(lowNumerator) && Base.isInterned(highNumerator) &&
                Base.isInterned(denominator);
    }

    public Quantity lowNumerator() {
        return lowNumerator;
    }

    public Quantity highNumerator() {
        return highNumerator;
    }

    public Quantity denominator() {
        return denominator;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == LOW_NUMERATOR) return LOW_NUMERATOR_LOOKUP_THUNK;
        if (key == HIGH_NUMERATOR) return HIGH_NUMERATOR_LOOKUP_THUNK;
        if (key == DENOMINATOR) return DENOMINATOR_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == LOW_NUMERATOR) return lowNumerator;
        if (key == HIGH_NUMERATOR) return highNumerator;
        if (key == DENOMINATOR) return denominator;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DENOMINATOR, denominator);
        seq = appendElement(seq, HIGH_NUMERATOR, highNumerator);
        seq = appendElement(seq, LOW_NUMERATOR, lowNumerator);
        return extensionData.append(seq);
    }

    @Override
    public RatioRange empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public RatioRange assoc(Object key, Object val) {
        if (key == LOW_NUMERATOR) return new RatioRange(extensionData, (Quantity) val, highNumerator, denominator);
        if (key == HIGH_NUMERATOR) return new RatioRange(extensionData, lowNumerator, (Quantity) val, denominator);
        if (key == DENOMINATOR) return new RatioRange(extensionData, lowNumerator, highNumerator, (Quantity) val);
        if (key == ID) return new RatioRange(extensionData.withId(val), lowNumerator, highNumerator, denominator);
        if (key == EXTENSION)
            return new RatioRange(extensionData.withExtension(val), lowNumerator, highNumerator, denominator);
        return this;
    }

    @Override
    public RatioRange withMeta(IPersistentMap meta) {
        return new RatioRange(extensionData.withMeta(meta), lowNumerator, highNumerator, denominator);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (lowNumerator != null) {
            lowNumerator.serializeJsonField(generator, FIELD_NAME_LOW_NUMERATOR);
        }
        if (highNumerator != null) {
            highNumerator.serializeJsonField(generator, FIELD_NAME_HIGH_NUMERATOR);
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
        if (lowNumerator != null) {
            sink.putByte((byte) 2);
            lowNumerator.hashInto(sink);
        }
        if (highNumerator != null) {
            sink.putByte((byte) 3);
            highNumerator.hashInto(sink);
        }
        if (denominator != null) {
            sink.putByte((byte) 4);
            denominator.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(lowNumerator) + Base.memSize(highNumerator) +
                Base.memSize(denominator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RatioRange that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(lowNumerator, that.lowNumerator) &&
                Objects.equals(highNumerator, that.highNumerator) &&
                Objects.equals(denominator, that.denominator);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(lowNumerator);
        result = 31 * result + Objects.hashCode(highNumerator);
        result = 31 * result + Objects.hashCode(denominator);
        return result;
    }

    @Override
    public String toString() {
        return "RatioRange{" +
                extensionData +
                ", lowNumerator=" + lowNumerator +
                ", highNumerator=" + highNumerator +
                ", denominator=" + denominator +
                '}';
    }
}
