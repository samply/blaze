package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class ParameterDefinition extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - name reference
     * 4 or 8 byte - use reference
     * 4 or 8 byte - min reference
     * 4 or 8 byte - max reference
     * 4 or 8 byte - documentation reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - profile reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "ParameterDefinition");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk NAME_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.name : this;
        }
    };

    private static final ILookupThunk USE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.use : this;
        }
    };

    private static final ILookupThunk MIN_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.min : this;
        }
    };

    private static final ILookupThunk MAX_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.max : this;
        }
    };

    private static final ILookupThunk DOCUMENTATION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.documentation : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.type : this;
        }
    };

    private static final ILookupThunk PROFILE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ParameterDefinition d ? d.profile : this;
        }
    };

    private static final Keyword NAME = RT.keyword(null, "name");
    private static final Keyword USE = RT.keyword(null, "use");
    private static final Keyword MIN = RT.keyword(null, "min");
    private static final Keyword MAX = RT.keyword(null, "max");
    private static final Keyword DOCUMENTATION = RT.keyword(null, "documentation");
    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword PROFILE = RT.keyword(null, "profile");

    private static final Keyword[] FIELDS = {ID, EXTENSION, NAME, USE, MIN, MAX, DOCUMENTATION, TYPE, PROFILE};

    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_MIN = FieldName.of("min");
    private static final FieldName FIELD_NAME_MAX = FieldName.of("max");
    private static final FieldName FIELD_NAME_DOCUMENTATION = FieldName.of("documentation");
    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_PROFILE = FieldName.of("profile");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueParameterDefinition");

    private static final byte HASH_MARKER = 66;

    private static final ParameterDefinition EMPTY = new ParameterDefinition(ExtensionData.EMPTY, null, null, null, null, null, null, null);

    private final Code name;
    private final Code use;
    private final Integer min;
    private final String max;
    private final String documentation;
    private final Code type;
    private final Canonical profile;

    private ParameterDefinition(ExtensionData extensionData, Code name, Code use, Integer min, String max, String documentation, Code type, Canonical profile) {
        super(extensionData);
        this.name = name;
        this.use = use;
        this.min = min;
        this.max = max;
        this.documentation = documentation;
        this.type = type;
        this.profile = profile;
    }

    public static ParameterDefinition create(IPersistentMap m) {
        return new ParameterDefinition(ExtensionData.fromMap(m), (Code) m.valAt(NAME), (Code) m.valAt(USE),
                (Integer) m.valAt(MIN), (String) m.valAt(MAX), (String) m.valAt(DOCUMENTATION), (Code) m.valAt(TYPE),
                (Canonical) m.valAt(PROFILE));
    }

    public Code name() {
        return name;
    }

    public Code use() {
        return use;
    }

    public Integer min() {
        return min;
    }

    public String max() {
        return max;
    }

    public String documentation() {
        return documentation;
    }

    public Code type() {
        return type;
    }

    public Canonical profile() {
        return profile;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == NAME) return NAME_LOOKUP_THUNK;
        if (key == USE) return USE_LOOKUP_THUNK;
        if (key == MIN) return MIN_LOOKUP_THUNK;
        if (key == MAX) return MAX_LOOKUP_THUNK;
        if (key == DOCUMENTATION) return DOCUMENTATION_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == PROFILE) return PROFILE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == NAME) return name;
        if (key == USE) return use;
        if (key == MIN) return min;
        if (key == MAX) return max;
        if (key == DOCUMENTATION) return documentation;
        if (key == TYPE) return type;
        if (key == PROFILE) return profile;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PROFILE, profile);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, DOCUMENTATION, documentation);
        seq = appendElement(seq, MAX, max);
        seq = appendElement(seq, MIN, min);
        seq = appendElement(seq, USE, use);
        seq = appendElement(seq, NAME, name);
        return extensionData.append(seq);
    }

    @Override
    public ParameterDefinition empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public ParameterDefinition assoc(Object key, Object val) {
        if (key == NAME)
            return new ParameterDefinition(extensionData, (Code) val, use, min, max, documentation, type, profile);
        if (key == USE)
            return new ParameterDefinition(extensionData, name, (Code) val, min, max, documentation, type, profile);
        if (key == MIN)
            return new ParameterDefinition(extensionData, name, use, (Integer) val, max, documentation, type, profile);
        if (key == MAX)
            return new ParameterDefinition(extensionData, name, use, min, (String) val, documentation, type, profile);
        if (key == DOCUMENTATION)
            return new ParameterDefinition(extensionData, name, use, min, max, (String) val, type, profile);
        if (key == TYPE)
            return new ParameterDefinition(extensionData, name, use, min, max, documentation, (Code) val, profile);
        if (key == PROFILE)
            return new ParameterDefinition(extensionData, name, use, min, max, documentation, type, (Canonical) val);
        if (key == EXTENSION)
            return new ParameterDefinition(extensionData.withExtension(val), name, use, min, max, documentation, type, profile);
        if (key == ID)
            return new ParameterDefinition(extensionData.withId(val), name, use, min, max, documentation, type, profile);
        return this;
    }

    @Override
    public ParameterDefinition withMeta(IPersistentMap meta) {
        return new ParameterDefinition(extensionData.withMeta(meta), name, use, min, max, documentation, type, profile);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (name != null) {
            name.serializeAsJsonProperty(generator, FIELD_NAME_NAME);
        }
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (min != null) {
            min.serializeAsJsonProperty(generator, FIELD_NAME_MIN);
        }
        if (max != null) {
            max.serializeAsJsonProperty(generator, FIELD_NAME_MAX);
        }
        if (documentation != null) {
            documentation.serializeAsJsonProperty(generator, FIELD_NAME_DOCUMENTATION);
        }
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (profile != null) {
            profile.serializeAsJsonProperty(generator, FIELD_NAME_PROFILE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (name != null) {
            sink.putByte((byte) 2);
            name.hashInto(sink);
        }
        if (use != null) {
            sink.putByte((byte) 3);
            use.hashInto(sink);
        }
        if (min != null) {
            sink.putByte((byte) 4);
            min.hashInto(sink);
        }
        if (max != null) {
            sink.putByte((byte) 5);
            max.hashInto(sink);
        }
        if (documentation != null) {
            sink.putByte((byte) 6);
            documentation.hashInto(sink);
        }
        if (type != null) {
            sink.putByte((byte) 7);
            type.hashInto(sink);
        }
        if (profile != null) {
            sink.putByte((byte) 8);
            profile.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(name) + Base.memSize(use) + Base.memSize(min) +
                Base.memSize(max) + Base.memSize(documentation) + Base.memSize(type) + Base.memSize(profile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ParameterDefinition that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(name, that.name) &&
                Objects.equals(use, that.use) &&
                Objects.equals(min, that.min) &&
                Objects.equals(max, that.max) &&
                Objects.equals(documentation, that.documentation) &&
                Objects.equals(type, that.type) &&
                Objects.equals(profile, that.profile);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(use);
        result = 31 * result + Objects.hashCode(min);
        result = 31 * result + Objects.hashCode(max);
        result = 31 * result + Objects.hashCode(documentation);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(profile);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "ParameterDefinition{"
                + "extensionData=" + extensionData +
                ", name=" + name +
                ", use=" + use +
                ", min=" + min +
                ", max=" + max +
                ", documentation=" + documentation +
                ", type=" + type +
                ", profile=" + profile +
                '}';
    }
}
