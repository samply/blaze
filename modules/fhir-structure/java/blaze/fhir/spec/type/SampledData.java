package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class SampledData extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - origin reference
     * 4 or 8 byte - period reference
     * 4 or 8 byte - factor reference
     * 4 or 8 byte - lowerLimit reference
     * 4 or 8 byte - upperLimit reference
     * 4 or 8 byte - dimensions reference
     * 4 or 8 byte - data reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "SampledData");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk ORIGIN_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.origin : this;
        }
    };

    private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.period : this;
        }
    };

    private static final ILookupThunk FACTOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.factor : this;
        }
    };

    private static final ILookupThunk LOWER_LIMIT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.lowerLimit : this;
        }
    };

    private static final ILookupThunk UPPER_LIMIT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.upperLimit : this;
        }
    };

    private static final ILookupThunk DIMENSIONS_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.dimensions : this;
        }
    };

    private static final ILookupThunk DATA_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof SampledData d ? d.data : this;
        }
    };

    private static final Keyword ORIGIN = RT.keyword(null, "origin");
    private static final Keyword PERIOD = RT.keyword(null, "period");
    private static final Keyword FACTOR = RT.keyword(null, "factor");
    private static final Keyword LOWER_LIMIT = RT.keyword(null, "lowerLimit");
    private static final Keyword UPPER_LIMIT = RT.keyword(null, "upperLimit");
    private static final Keyword DIMENSIONS = RT.keyword(null, "dimensions");
    private static final Keyword DATA = RT.keyword(null, "data");

    private static final Keyword[] FIELDS = {ID, EXTENSION, ORIGIN, PERIOD, FACTOR, LOWER_LIMIT, UPPER_LIMIT, DIMENSIONS, DATA};

    private static final FieldName FIELD_NAME_ORIGIN = FieldName.of("origin");
    private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");
    private static final FieldName FIELD_NAME_FACTOR = FieldName.of("factor");
    private static final FieldName FIELD_NAME_LOWER_LIMIT = FieldName.of("lowerLimit");
    private static final FieldName FIELD_NAME_UPPER_LIMIT = FieldName.of("upperLimit");
    private static final FieldName FIELD_NAME_DIMENSIONS = FieldName.of("dimensions");
    private static final FieldName FIELD_NAME_DATA = FieldName.of("data");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueSampledData");

    private static final byte HASH_MARKER = 56;

    private static final SampledData EMPTY = new SampledData(ExtensionData.EMPTY, null, null, null, null, null, null, null);

    private final Quantity origin;
    private final Decimal period;
    private final Decimal factor;
    private final Decimal lowerLimit;
    private final Decimal upperLimit;
    private final PositiveInt dimensions;
    private final String data;

    private SampledData(ExtensionData extensionData, Quantity origin, Decimal period, Decimal factor,
                        Decimal lowerLimit, Decimal upperLimit, PositiveInt dimensions, String data) {
        super(extensionData);
        this.origin = origin;
        this.period = period;
        this.factor = factor;
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.dimensions = dimensions;
        this.data = data;
    }

    public static SampledData create(IPersistentMap m) {
        return new SampledData(ExtensionData.fromMap(m), (Quantity) m.valAt(ORIGIN), (Decimal) m.valAt(PERIOD),
                (Decimal) m.valAt(FACTOR), (Decimal) m.valAt(LOWER_LIMIT), (Decimal) m.valAt(UPPER_LIMIT),
                (PositiveInt) m.valAt(DIMENSIONS), (String) m.valAt(DATA));
    }

    public Quantity origin() {
        return origin;
    }

    public Decimal period() {
        return period;
    }

    public Decimal factor() {
        return factor;
    }

    public Decimal lowerLimit() {
        return lowerLimit;
    }

    public Decimal upperLimit() {
        return upperLimit;
    }

    public PositiveInt dimensions() {
        return dimensions;
    }

    public String data() {
        return data;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == ORIGIN) return ORIGIN_LOOKUP_THUNK;
        if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
        if (key == FACTOR) return FACTOR_LOOKUP_THUNK;
        if (key == LOWER_LIMIT) return LOWER_LIMIT_LOOKUP_THUNK;
        if (key == UPPER_LIMIT) return UPPER_LIMIT_LOOKUP_THUNK;
        if (key == DIMENSIONS) return DIMENSIONS_LOOKUP_THUNK;
        if (key == DATA) return DATA_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == ORIGIN) return origin;
        if (key == PERIOD) return period;
        if (key == FACTOR) return factor;
        if (key == LOWER_LIMIT) return lowerLimit;
        if (key == UPPER_LIMIT) return upperLimit;
        if (key == DIMENSIONS) return dimensions;
        if (key == DATA) return data;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DATA, data);
        seq = appendElement(seq, DIMENSIONS, dimensions);
        seq = appendElement(seq, UPPER_LIMIT, upperLimit);
        seq = appendElement(seq, LOWER_LIMIT, lowerLimit);
        seq = appendElement(seq, FACTOR, factor);
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, ORIGIN, origin);
        return extensionData.append(seq);
    }

    @Override
    public SampledData empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public SampledData assoc(Object key, Object val) {
        if (key == ORIGIN)
            return new SampledData(extensionData, (Quantity) val, period, factor, lowerLimit, upperLimit, dimensions, data);
        if (key == PERIOD)
            return new SampledData(extensionData, origin, (Decimal) val, factor, lowerLimit, upperLimit, dimensions, data);
        if (key == FACTOR)
            return new SampledData(extensionData, origin, period, (Decimal) val, lowerLimit, upperLimit, dimensions, data);
        if (key == LOWER_LIMIT)
            return new SampledData(extensionData, origin, period, factor, (Decimal) val, upperLimit, dimensions, data);
        if (key == UPPER_LIMIT)
            return new SampledData(extensionData, origin, period, factor, lowerLimit, (Decimal) val, dimensions, data);
        if (key == DIMENSIONS)
            return new SampledData(extensionData, origin, period, factor, lowerLimit, upperLimit, (PositiveInt) val, data);
        if (key == DATA)
            return new SampledData(extensionData, origin, period, factor, lowerLimit, upperLimit, dimensions, (String) val);
        if (key == EXTENSION)
            return new SampledData(extensionData.withExtension(val), origin, period, factor, lowerLimit, upperLimit, dimensions, data);
        if (key == ID)
            return new SampledData(extensionData.withId(val), origin, period, factor, lowerLimit, upperLimit, dimensions, data);
        return this;
    }

    @Override
    public SampledData withMeta(IPersistentMap meta) {
        return new SampledData(extensionData.withMeta(meta), origin, period, factor, lowerLimit, upperLimit, dimensions, data);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (origin != null) {
            origin.serializeJsonField(generator, FIELD_NAME_ORIGIN);
        }
        if (period != null) {
            period.serializeAsJsonProperty(generator, FIELD_NAME_PERIOD);
        }
        if (factor != null) {
            factor.serializeAsJsonProperty(generator, FIELD_NAME_FACTOR);
        }
        if (lowerLimit != null) {
            lowerLimit.serializeAsJsonProperty(generator, FIELD_NAME_LOWER_LIMIT);
        }
        if (upperLimit != null) {
            upperLimit.serializeAsJsonProperty(generator, FIELD_NAME_UPPER_LIMIT);
        }
        if (dimensions != null) {
            dimensions.serializeAsJsonProperty(generator, FIELD_NAME_DIMENSIONS);
        }
        if (data != null) {
            data.serializeAsJsonProperty(generator, FIELD_NAME_DATA);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (origin != null) {
            sink.putByte((byte) 2);
            origin.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 3);
            period.hashInto(sink);
        }
        if (factor != null) {
            sink.putByte((byte) 4);
            factor.hashInto(sink);
        }
        if (lowerLimit != null) {
            sink.putByte((byte) 5);
            lowerLimit.hashInto(sink);
        }
        if (upperLimit != null) {
            sink.putByte((byte) 6);
            upperLimit.hashInto(sink);
        }
        if (dimensions != null) {
            sink.putByte((byte) 7);
            dimensions.hashInto(sink);
        }
        if (data != null) {
            sink.putByte((byte) 8);
            data.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(origin) + Base.memSize(period) +
                Base.memSize(factor) + Base.memSize(lowerLimit) + Base.memSize(upperLimit) +
                Base.memSize(dimensions) + Base.memSize(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SampledData that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(origin, that.origin) &&
                Objects.equals(period, that.period) &&
                Objects.equals(factor, that.factor) &&
                Objects.equals(lowerLimit, that.lowerLimit) &&
                Objects.equals(upperLimit, that.upperLimit) &&
                Objects.equals(dimensions, that.dimensions) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(origin);
        result = 31 * result + Objects.hashCode(period);
        result = 31 * result + Objects.hashCode(factor);
        result = 31 * result + Objects.hashCode(lowerLimit);
        result = 31 * result + Objects.hashCode(upperLimit);
        result = 31 * result + Objects.hashCode(dimensions);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "SampledData{" +
                extensionData +
                ", origin=" + origin +
                ", period=" + period +
                ", factor=" + factor +
                ", lowerLimit=" + lowerLimit +
                ", upperLimit=" + upperLimit +
                ", dimensions=" + dimensions +
                ", data=" + data +
                '}';
    }
}
