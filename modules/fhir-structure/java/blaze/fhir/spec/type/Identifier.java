package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Identifier extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - use reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - system reference
     * 4 or 8 byte - value reference
     * 4 or 8 byte - period reference
     * 4 or 8 byte - assigner reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 7 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Identifier");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk USE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.use : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.type : this;
        }
    };

    private static final ILookupThunk SYSTEM_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.system : this;
        }
    };

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.value : this;
        }
    };

    private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.period : this;
        }
    };

    private static final ILookupThunk ASSIGNER_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Identifier i ? i.assigner : this;
        }
    };

    private static final Keyword USE = RT.keyword(null, "use");
    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword SYSTEM = RT.keyword(null, "system");
    private static final Keyword VALUE = RT.keyword(null, "value");
    private static final Keyword PERIOD = RT.keyword(null, "period");
    private static final Keyword ASSIGNER = RT.keyword(null, "assigner");

    private static final Keyword[] FIELDS = {ID, EXTENSION, USE, TYPE, SYSTEM, VALUE, PERIOD, ASSIGNER};

    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");
    private static final FieldName FIELD_NAME_ASSIGNER = FieldName.of("assigner");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueIdentifier");

    private static final byte HASH_MARKER = 42;

    private static final Identifier EMPTY = new Identifier(ExtensionData.EMPTY, null, null, null, null, null, null);

    private final Code use;
    private final CodeableConcept type;
    private final Uri system;
    private final String value;
    private final Period period;
    private final Reference assigner;

    private Identifier(ExtensionData extensionData, Code use, CodeableConcept type, Uri system, String value,
                       Period period, Reference assigner) {
        super(extensionData);
        this.use = use;
        this.type = type;
        this.system = system;
        this.value = value;
        this.period = period;
        this.assigner = assigner;
    }

    public static Identifier create(IPersistentMap m) {
        return new Identifier(ExtensionData.fromMap(m), (Code) m.valAt(USE),
                (CodeableConcept) m.valAt(TYPE), (Uri) m.valAt(SYSTEM), (String) m.valAt(VALUE), (Period) m.valAt(PERIOD),
                (Reference) m.valAt(ASSIGNER));
    }

    public Code use() {
        return use;
    }

    public CodeableConcept type() {
        return type;
    }

    public Uri system() {
        return system;
    }

    public String value() {
        return value;
    }

    public Period period() {
        return period;
    }

    public Reference assigner() {
        return assigner;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == USE) return USE_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == SYSTEM) return SYSTEM_LOOKUP_THUNK;
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
        if (key == ASSIGNER) return ASSIGNER_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == USE) return use;
        if (key == TYPE) return type;
        if (key == SYSTEM) return system;
        if (key == VALUE) return value;
        if (key == PERIOD) return period;
        if (key == ASSIGNER) return assigner;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, ASSIGNER, assigner);
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, SYSTEM, system);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, USE, use);
        return extensionData.append(seq);
    }

    @Override
    public Identifier empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Identifier assoc(Object key, Object val) {
        if (key == USE) return new Identifier(extensionData, (Code) val, type, system, value, period, assigner);
        if (key == TYPE)
            return new Identifier(extensionData, use, (CodeableConcept) val, system, value, period, assigner);
        if (key == SYSTEM) return new Identifier(extensionData, use, type, (Uri) val, value, period, assigner);
        if (key == VALUE) return new Identifier(extensionData, use, type, system, (String) val, period, assigner);
        if (key == PERIOD) return new Identifier(extensionData, use, type, system, value, (Period) val, assigner);
        if (key == ASSIGNER) return new Identifier(extensionData, use, type, system, value, period, (Reference) val);
        if (key == EXTENSION)
            return new Identifier(extensionData.withExtension(val), use, type, system, value, period, assigner);
        if (key == ID) return new Identifier(extensionData.withId(val), use, type, system, value, period, assigner);
        return this;
    }

    @Override
    public Identifier withMeta(IPersistentMap meta) {
        return new Identifier(extensionData.withMeta(meta), use, type, system, value, period, assigner);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (type != null) {
            type.serializeJsonField(generator, FIELD_NAME_TYPE);
        }
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (period != null) {
            period.serializeJsonField(generator, FIELD_NAME_PERIOD);
        }
        if (assigner != null) {
            assigner.serializeJsonField(generator, FIELD_NAME_ASSIGNER);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (use != null) {
            sink.putByte((byte) 2);
            use.hashInto(sink);
        }
        if (type != null) {
            sink.putByte((byte) 3);
            type.hashInto(sink);
        }
        if (system != null) {
            sink.putByte((byte) 4);
            system.hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 5);
            value.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 6);
            period.hashInto(sink);
        }
        if (assigner != null) {
            sink.putByte((byte) 7);
            assigner.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(use) + Base.memSize(type) + Base.memSize(system) +
                Base.memSize(value) + Base.memSize(period);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Identifier that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(use, that.use) &&
                Objects.equals(type, that.type) &&
                Objects.equals(system, that.system) &&
                Objects.equals(value, that.value) &&
                Objects.equals(period, that.period) &&
                Objects.equals(assigner, that.assigner);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(use);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(system);
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(period);
        result = 31 * result + Objects.hashCode(assigner);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Identifier{" +
                extensionData +
                ", use=" + use +
                ", type=" + type +
                ", system=" + system +
                ", value=" + value +
                ", period=" + period +
                ", assigner=" + assigner +
                '}';
    }
}
