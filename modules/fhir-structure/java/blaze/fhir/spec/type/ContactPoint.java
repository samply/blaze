package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class ContactPoint extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - system reference
     * 4 or 8 byte - value reference
     * 4 or 8 byte - use reference
     * 4 or 8 byte - rank reference
     * 4 or 8 byte - period reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 6 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "ContactPoint");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk SYSTEM_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint p ? p.system : this;
        }
    };

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint p ? p.value : this;
        }
    };

    private static final ILookupThunk USE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint p ? p.use : this;
        }
    };

    private static final ILookupThunk RANK_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint p ? p.rank : this;
        }
    };

    private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ContactPoint p ? p.period : this;
        }
    };

    private static final Keyword SYSTEM = RT.keyword(null, "system");
    private static final Keyword VALUE = RT.keyword(null, "value");
    private static final Keyword USE = RT.keyword(null, "use");
    private static final Keyword RANK = RT.keyword(null, "rank");
    private static final Keyword PERIOD = RT.keyword(null, "period");

    private static final Keyword[] FIELDS = {ID, EXTENSION, SYSTEM, VALUE, USE, RANK, PERIOD};

    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_RANK = FieldName.of("rank");
    private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueContactPoint");

    private static final byte HASH_MARKER = 53;

    private static final ContactPoint EMPTY = new ContactPoint(ExtensionData.EMPTY, null, null, null, null, null);

    private final Code system;
    private final String value;
    private final Code use;
    private final PositiveInt rank;
    private final Period period;

    private ContactPoint(ExtensionData extensionData, Code system, String value, Code use, PositiveInt rank,
                         Period period) {
        super(extensionData);
        this.system = system;
        this.value = value;
        this.use = use;
        this.rank = rank;
        this.period = period;
    }

    public static ContactPoint create(IPersistentMap m) {
        return new ContactPoint(ExtensionData.fromMap(m), (Code) m.valAt(SYSTEM), (String) m.valAt(VALUE),
                (Code) m.valAt(USE), (PositiveInt) m.valAt(RANK), (Period) m.valAt(PERIOD));
    }

    public Code system() {
        return system;
    }

    public String value() {
        return value;
    }

    public Code use() {
        return use;
    }

    public PositiveInt rank() {
        return rank;
    }

    public Period period() {
        return period;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == SYSTEM) return SYSTEM_LOOKUP_THUNK;
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        if (key == USE) return USE_LOOKUP_THUNK;
        if (key == RANK) return RANK_LOOKUP_THUNK;
        if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == SYSTEM) return system;
        if (key == VALUE) return value;
        if (key == USE) return use;
        if (key == RANK) return rank;
        if (key == PERIOD) return period;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, RANK, rank);
        seq = appendElement(seq, USE, use);
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, SYSTEM, system);
        return extensionData.append(seq);
    }

    @Override
    public ContactPoint empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public ContactPoint assoc(Object key, Object val) {
        if (key == SYSTEM) return new ContactPoint(extensionData, (Code) val, value, use, rank, period);
        if (key == VALUE) return new ContactPoint(extensionData, system, (String) val, use, rank, period);
        if (key == USE) return new ContactPoint(extensionData, system, value, (Code) val, rank, period);
        if (key == RANK) return new ContactPoint(extensionData, system, value, use, (PositiveInt) val, period);
        if (key == PERIOD) return new ContactPoint(extensionData, system, value, use, rank, (Period) val);
        if (key == EXTENSION)
            return new ContactPoint(extensionData.withExtension(val), system, value, use, rank, period);
        if (key == ID) return new ContactPoint(extensionData.withId(val), system, value, use, rank, period);
        return this;
    }

    @Override
    public ContactPoint withMeta(IPersistentMap meta) {
        return new ContactPoint(extensionData.withMeta(meta), system, value, use, rank, period);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (rank != null) {
            rank.serializeAsJsonProperty(generator, FIELD_NAME_RANK);
        }
        if (period != null) {
            period.serializeJsonField(generator, FIELD_NAME_PERIOD);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (system != null) {
            sink.putByte((byte) 2);
            system.hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 3);
            value.hashInto(sink);
        }
        if (use != null) {
            sink.putByte((byte) 4);
            use.hashInto(sink);
        }
        if (rank != null) {
            sink.putByte((byte) 5);
            rank.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 6);
            period.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(system) + Base.memSize(value) +
                Base.memSize(use) + Base.memSize(rank) + Base.memSize(period);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ContactPoint that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(system, that.system) &&
                Objects.equals(value, that.value) &&
                Objects.equals(use, that.use) &&
                Objects.equals(rank, that.rank) &&
                Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(system);
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(use);
        result = 31 * result + Objects.hashCode(rank);
        result = 31 * result + Objects.hashCode(period);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "ContactPoint{" +
                extensionData +
                ", system=" + system +
                ", value=" + value +
                ", use=" + use +
                ", rank=" + rank +
                ", period=" + period +
                '}';
    }
}
