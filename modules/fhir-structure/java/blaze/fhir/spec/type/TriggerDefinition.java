package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;

@SuppressWarnings("DuplicatedCode")
public final class TriggerDefinition extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - name reference
     * 4 or 8 byte - timing reference
     * 4 or 8 byte - data reference
     * 4 or 8 byte - condition reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 6 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "TriggerDefinition");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition d ? d.type : this;
        }
    };

    private static final ILookupThunk NAME_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition d ? d.name : this;
        }
    };

    private static final ILookupThunk TIMING_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition d ? d.timing : this;
        }
    };

    private static final ILookupThunk DATA_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition d ? d.data : this;
        }
    };

    private static final ILookupThunk CONDITION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof TriggerDefinition d ? d.condition : this;
        }
    };

    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword NAME = RT.keyword(null, "name");
    private static final Keyword TIMING = RT.keyword(null, "timing");
    private static final Keyword DATA = RT.keyword(null, "data");
    private static final Keyword CONDITION = RT.keyword(null, "condition");

    private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, NAME, TIMING, DATA, CONDITION};

    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final FieldName FIELD_NAME_TIMING_TIMING = FieldName.of("timingTiming");
    private static final FieldName FIELD_NAME_TIMING_REFERENCE = FieldName.of("timingReference");
    private static final FieldName FIELD_NAME_TIMING_DATE = FieldName.of("timingDate");
    private static final FieldName FIELD_NAME_TIMING_DATE_TIME = FieldName.of("timingDateTime");
    private static final FieldName FIELD_NAME_DATA = FieldName.of("data");
    private static final FieldName FIELD_NAME_CONDITION = FieldName.of("condition");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueTriggerDefinition");

    private static final byte HASH_MARKER = 68;

    @SuppressWarnings("unchecked")
    private static final TriggerDefinition EMPTY = new TriggerDefinition(ExtensionData.EMPTY, null, null, null,
            PersistentVector.EMPTY, null);

    private final Code type;
    private final String name;
    private final Element timing;
    private final List<DataRequirement> data;
    private final Expression condition;

    private TriggerDefinition(ExtensionData extensionData, Code type, String name, Element timing,
                              List<DataRequirement> data, Expression condition) {
        super(extensionData);
        this.type = type;
        this.name = name;
        this.timing = timing;
        this.data = data;
        this.condition = condition;
    }

    public static TriggerDefinition create(IPersistentMap m) {
        return new TriggerDefinition(ExtensionData.fromMap(m), (Code) m.valAt(TYPE), (String) m.valAt(NAME),
                (Element) m.valAt(TIMING), Base.listFrom(m, DATA), (Expression) m.valAt(CONDITION));
    }

    public Code type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Element timing() {
        return timing;
    }

    public List<DataRequirement> data() {
        return data;
    }

    public Expression condition() {
        return condition;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == NAME) return NAME_LOOKUP_THUNK;
        if (key == TIMING) return TIMING_LOOKUP_THUNK;
        if (key == DATA) return DATA_LOOKUP_THUNK;
        if (key == CONDITION) return CONDITION_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == TYPE) return type;
        if (key == NAME) return name;
        if (key == TIMING) return timing;
        if (key == DATA) return data;
        if (key == CONDITION) return condition;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CONDITION, condition);
        seq = appendElement(seq, DATA, data);
        seq = appendElement(seq, TIMING, timing);
        seq = appendElement(seq, NAME, name);
        seq = appendElement(seq, TYPE, type);
        return extensionData.append(seq);
    }

    @Override
    public TriggerDefinition empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TriggerDefinition assoc(Object key, Object val) {
        if (key == TYPE)
            return new TriggerDefinition(extensionData, (Code) val, name, timing, data, condition);
        if (key == NAME)
            return new TriggerDefinition(extensionData, type, (String) val, timing, data, condition);
        if (key == TIMING)
            return new TriggerDefinition(extensionData, type, name, (Element) val, data, condition);
        if (key == DATA)
            return new TriggerDefinition(extensionData, type, name, timing, (List<DataRequirement>) val, condition);
        if (key == CONDITION)
            return new TriggerDefinition(extensionData, type, name, timing, data, (Expression) val);
        if (key == EXTENSION)
            return new TriggerDefinition(extensionData.withExtension(val), type, name, timing, data, condition);
        if (key == ID)
            return new TriggerDefinition(extensionData.withId(val), type, name, timing, data, condition);
        return this;
    }

    @Override
    public TriggerDefinition withMeta(IPersistentMap meta) {
        return new TriggerDefinition(extensionData.withMeta(meta), type, name, timing, data, condition);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (name != null) {
            name.serializeAsJsonProperty(generator, FIELD_NAME_NAME);
        }
        if (timing != null) {
            switch (timing) {
                case Timing timingTiming ->
                        timingTiming.serializeJsonField(generator, FIELD_NAME_TIMING_TIMING);
                case Reference timingReference ->
                        timingReference.serializeJsonField(generator, FIELD_NAME_TIMING_REFERENCE);
                case Date timingDate ->
                        timingDate.serializeAsJsonProperty(generator, FIELD_NAME_TIMING_DATE);
                case DateTime timingDateTime ->
                        timingDateTime.serializeAsJsonProperty(generator, FIELD_NAME_TIMING_DATE_TIME);
                default -> {
                }
            }
        }
        if (!data.isEmpty()) {
            serializeJsonComplexList(data, generator, FIELD_NAME_DATA.normal());
        }
        if (condition != null) {
            condition.serializeJsonField(generator, FIELD_NAME_CONDITION);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (type != null) {
            sink.putByte((byte) 2);
            type.hashInto(sink);
        }
        if (name != null) {
            sink.putByte((byte) 3);
            name.hashInto(sink);
        }
        if (timing != null) {
            sink.putByte((byte) 4);
            timing.hashInto(sink);
        }
        if (!data.isEmpty()) {
            sink.putByte((byte) 5);
            Base.hashIntoList(data, sink);
        }
        if (condition != null) {
            sink.putByte((byte) 6);
            condition.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(type) + Base.memSize(name) +
                Base.memSize(timing) + Base.memSize(data) + Base.memSize(condition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TriggerDefinition that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(type, that.type) &&
                Objects.equals(name, that.name) &&
                Objects.equals(timing, that.timing) &&
                Objects.equals(data, that.data) &&
                Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(timing);
        result = 31 * result + Objects.hashCode(data);
        result = 31 * result + Objects.hashCode(condition);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "TriggerDefinition{" +
                "extensionData=" + extensionData +
                ", type=" + type +
                ", name=" + name +
                ", timing=" + timing +
                ", data=" + data +
                ", condition=" + condition +
                '}';
    }
}
