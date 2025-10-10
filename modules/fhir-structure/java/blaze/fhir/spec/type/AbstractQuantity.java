package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public sealed abstract class AbstractQuantity extends AbstractElement implements Complex, ExtensionValue permits
        Age, Count, Distance, Duration, Quantity {

    protected static final Keyword VALUE = RT.keyword(null, "value");
    protected static final Keyword COMPARATOR = RT.keyword(null, "comparator");
    protected static final Keyword UNIT = RT.keyword(null, "unit");
    protected static final Keyword SYSTEM = RT.keyword(null, "system");
    protected static final Keyword CODE = RT.keyword(null, "code");

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof AbstractQuantity q ? q.value : this;
        }
    };

    private static final ILookupThunk COMPARATOR_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof AbstractQuantity q ? q.comparator : this;
        }
    };

    private static final ILookupThunk UNIT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof AbstractQuantity q ? q.unit : this;
        }
    };

    private static final ILookupThunk SYSTEM_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof AbstractQuantity q ? q.system : this;
        }
    };

    private static final ILookupThunk CODE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof AbstractQuantity q ? q.code : this;
        }
    };
    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - value reference
     * 4 or 8 byte - comparator reference
     * 4 or 8 byte - unit reference
     * 4 or 8 byte - system reference
     * 4 or 8 byte - code reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 6 * MEM_SIZE_REFERENCE;
    private static final Keyword[] FIELDS = {ID, EXTENSION, VALUE, COMPARATOR, UNIT, SYSTEM, CODE};

    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_COMPARATOR = FieldName.of("comparator");
    private static final FieldName FIELD_NAME_UNIT = FieldName.of("unit");
    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");

    private static final byte HASH_MARKER = 40;

    protected final Decimal value;
    protected final Code comparator;
    protected final String unit;
    protected final Uri system;
    protected final Code code;

    protected AbstractQuantity(ExtensionData extensionData, Decimal value, Code comparator, String unit, Uri system, Code code) {
        super(extensionData);
        this.value = value;
        this.comparator = comparator;
        this.unit = unit;
        this.system = system;
        this.code = code;
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned() && Base.isInterned(value) && Base.isInterned(comparator) &&
                Base.isInterned(unit) && Base.isInterned(system) && Base.isInterned(code);
    }

    public Decimal value() {
        return value;
    }

    public Code comparator() {
        return comparator;
    }

    public String unit() {
        return unit;
    }

    public Uri system() {
        return system;
    }

    public Code code() {
        return code;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        if (key == COMPARATOR) return COMPARATOR_LOOKUP_THUNK;
        if (key == UNIT) return UNIT_LOOKUP_THUNK;
        if (key == SYSTEM) return SYSTEM_LOOKUP_THUNK;
        if (key == CODE) return CODE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == CODE) return code;
        if (key == SYSTEM) return system;
        if (key == UNIT) return unit;
        if (key == COMPARATOR) return comparator;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, SYSTEM, system);
        seq = appendElement(seq, UNIT, unit);
        seq = appendElement(seq, COMPARATOR, comparator);
        seq = appendElement(seq, VALUE, value);
        return extensionData.append(seq);
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (comparator != null) {
            comparator.serializeAsJsonProperty(generator, FIELD_NAME_COMPARATOR);
        }
        if (unit != null) {
            unit.serializeAsJsonProperty(generator, FIELD_NAME_UNIT);
        }
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (code != null) {
            code.serializeAsJsonProperty(generator, FIELD_NAME_CODE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            value.hashInto(sink);
        }
        if (comparator != null) {
            sink.putByte((byte) 3);
            comparator.hashInto(sink);
        }
        if (unit != null) {
            sink.putByte((byte) 4);
            unit.hashInto(sink);
        }
        if (system != null) {
            sink.putByte((byte) 5);
            system.hashInto(sink);
        }
        if (code != null) {
            sink.putByte((byte) 6);
            code.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(value) +
                Base.memSize(comparator) + Base.memSize(unit) + Base.memSize(system) + Base.memSize(code);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof AbstractQuantity that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value) &&
                Objects.equals(comparator, that.comparator) &&
                Objects.equals(unit, that.unit) &&
                Objects.equals(system, that.system) &&
                Objects.equals(code, that.code);
    }

    @Override
    public final int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(comparator);
        result = 31 * result + Objects.hashCode(unit);
        result = 31 * result + Objects.hashCode(system);
        result = 31 * result + Objects.hashCode(code);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Quantity{" +
                extensionData +
                ", value=" + value +
                ", comparator=" + comparator +
                ", unit=" + unit +
                ", system=" + system +
                ", code=" + code +
                '}';
    }

    protected record InternerKey(ExtensionData extensionData, Decimal value, Code comparator, String unit, Uri system,
                               Code code) {
        protected InternerKey {
            requireNonNull(extensionData);
        }
    }
}
