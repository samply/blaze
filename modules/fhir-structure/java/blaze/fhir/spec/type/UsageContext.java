package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class UsageContext extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - code reference
     * 4 or 8 byte - value reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "UsageContext");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof UsageContext ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk CODE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof UsageContext c ? c.code : this;
        }
    };

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof UsageContext c ? c.value : this;
        }
    };

    private static final Keyword CODE = RT.keyword(null, "code");
    private static final Keyword VALUE = RT.keyword(null, "value");

    private static final Keyword[] FIELDS = {ID, EXTENSION, CODE, VALUE};

    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUsageContext");

    private static final byte HASH_MARKER = 67;

    private static final UsageContext EMPTY = new UsageContext(ExtensionData.EMPTY, null, null);

    private final Coding code;
    private final ExtensionValue value;

    private UsageContext(ExtensionData extensionData, Coding code, ExtensionValue value) {
        super(extensionData);
        this.code = code;
        this.value = value;
    }

    public static UsageContext create(IPersistentMap m) {
        return new UsageContext(ExtensionData.fromMap(m), (Coding) m.valAt(CODE), (ExtensionValue) m.valAt(VALUE));
    }

    public Coding code() {
        return code;
    }

    public Element value() {
        return value;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == CODE) return CODE_LOOKUP_THUNK;
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == CODE) return code;
        if (key == VALUE) return value;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, CODE, code);
        return extensionData.append(seq);
    }

    @Override
    public UsageContext empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public UsageContext assoc(Object key, Object val) {
        if (key == CODE)
            return new UsageContext(extensionData, (Coding) val, value);
        if (key == VALUE)
            return new UsageContext(extensionData, code, (ExtensionValue) val);
        if (key == EXTENSION)
            return new UsageContext(extensionData.withExtension(val), code, value);
        if (key == ID)
            return new UsageContext(extensionData.withId(val), code, value);
        return this;
    }

    @Override
    public UsageContext withMeta(IPersistentMap meta) {
        return new UsageContext(extensionData.withMeta(meta), code, value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (code != null) {
            code.serializeJsonField(generator, FIELD_NAME_CODE);
        }
        if (value != null) {
            value.serializeJsonField(generator, value.fieldNameExtensionValue());
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (code != null) {
            sink.putByte((byte) 2);
            code.hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 3);
            value.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(code) + Base.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof UsageContext that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(code, that.code) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "UsageContext{"
                + "extensionData=" + extensionData +
                ", code=" + code +
                ", value=" + value +
                '}';
    }
}
