package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class Signature extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - when reference
     * 4 or 8 byte - who reference
     * 4 or 8 byte - onBehalfOf reference
     * 4 or 8 byte - targetFormat reference
     * 4 or 8 byte - sigFormat reference
     * 4 or 8 byte - data reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Signature");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.type : this;
        }
    };

    private static final ILookupThunk WHEN_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.when : this;
        }
    };

    private static final ILookupThunk WHO_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.who : this;
        }
    };

    private static final ILookupThunk ON_BEHALF_OF_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.onBehalfOf : this;
        }
    };

    private static final ILookupThunk TARGET_FORMAT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.targetFormat : this;
        }
    };

    private static final ILookupThunk SIG_FORMAT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.sigFormat : this;
        }
    };

    private static final ILookupThunk DATA_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Signature s ? s.data : this;
        }
    };

    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword WHEN = RT.keyword(null, "when");
    private static final Keyword WHO = RT.keyword(null, "who");
    private static final Keyword ON_BEHALF_OF = RT.keyword(null, "onBehalfOf");
    private static final Keyword TARGET_FORMAT = RT.keyword(null, "targetFormat");
    private static final Keyword SIG_FORMAT = RT.keyword(null, "sigFormat");
    private static final Keyword DATA = RT.keyword(null, "data");

    private static final Keyword[] FIELDS = {ID, EXTENSION, TYPE, WHEN, WHO, ON_BEHALF_OF, TARGET_FORMAT, SIG_FORMAT, DATA};

    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_WHEN = FieldName.of("when");
    private static final FieldName FIELD_NAME_WHO = FieldName.of("who");
    private static final FieldName FIELD_NAME_ON_BEHALF_OF = FieldName.of("onBehalfOf");
    private static final FieldName FIELD_NAME_TARGET_FORMAT = FieldName.of("targetFormat");
    private static final FieldName FIELD_NAME_SIG_FORMAT = FieldName.of("sigFormat");
    private static final FieldName FIELD_NAME_DATA = FieldName.of("data");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueSignature");

    private static final byte HASH_MARKER = 59;

    @SuppressWarnings("unchecked")
    private static final Signature EMPTY = new Signature(ExtensionData.EMPTY, PersistentVector.EMPTY, null, null, null,
            null, null, null);

    private final List<Coding> type;
    private final Instant when;
    private final Reference who;
    private final Reference onBehalfOf;
    private final Code targetFormat;
    private final Code sigFormat;
    private final Base64Binary data;

    private Signature(ExtensionData extensionData, List<Coding> type, Instant when, Reference who,
                      Reference onBehalfOf, Code targetFormat, Code sigFormat, Base64Binary data) {
        super(extensionData);
        this.type = requireNonNull(type);
        this.when = when;
        this.who = who;
        this.onBehalfOf = onBehalfOf;
        this.targetFormat = targetFormat;
        this.sigFormat = sigFormat;
        this.data = data;
    }

    public static Signature create(IPersistentMap m) {
        return new Signature(ExtensionData.fromMap(m), Base.listFrom(m, TYPE), (Instant) m.valAt(WHEN),
                (Reference) m.valAt(WHO), (Reference) m.valAt(ON_BEHALF_OF), (Code) m.valAt(TARGET_FORMAT),
                (Code) m.valAt(SIG_FORMAT), (Base64Binary) m.valAt(DATA));
    }

    public List<Coding> type() {
        return type;
    }

    public Instant when() {
        return when;
    }

    public Reference who() {
        return who;
    }

    public Reference onBehalfOf() {
        return onBehalfOf;
    }

    public Code targetFormat() {
        return targetFormat;
    }

    public Code sigFormat() {
        return sigFormat;
    }

    public Base64Binary data() {
        return data;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == WHEN) return WHEN_LOOKUP_THUNK;
        if (key == WHO) return WHO_LOOKUP_THUNK;
        if (key == ON_BEHALF_OF) return ON_BEHALF_OF_LOOKUP_THUNK;
        if (key == TARGET_FORMAT) return TARGET_FORMAT_LOOKUP_THUNK;
        if (key == SIG_FORMAT) return SIG_FORMAT_LOOKUP_THUNK;
        if (key == DATA) return DATA_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == TYPE) return type;
        if (key == WHEN) return when;
        if (key == WHO) return who;
        if (key == ON_BEHALF_OF) return onBehalfOf;
        if (key == TARGET_FORMAT) return targetFormat;
        if (key == SIG_FORMAT) return sigFormat;
        if (key == DATA) return data;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DATA, data);
        seq = appendElement(seq, SIG_FORMAT, sigFormat);
        seq = appendElement(seq, TARGET_FORMAT, targetFormat);
        seq = appendElement(seq, ON_BEHALF_OF, onBehalfOf);
        seq = appendElement(seq, WHO, who);
        seq = appendElement(seq, WHEN, when);
        if (!type.isEmpty()) {
            seq = appendElement(seq, TYPE, type);
        }
        return extensionData.append(seq);
    }

    @Override
    public Signature empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Signature assoc(Object key, Object val) {
        if (key == TYPE) return new Signature(extensionData, Lists.nullToEmpty(val), when, who, onBehalfOf, targetFormat, sigFormat, data);
        if (key == WHEN) return new Signature(extensionData, type, (Instant) val, who, onBehalfOf, targetFormat, sigFormat, data);
        if (key == WHO) return new Signature(extensionData, type, when, (Reference) val, onBehalfOf, targetFormat, sigFormat, data);
        if (key == ON_BEHALF_OF) return new Signature(extensionData, type, when, who, (Reference) val, targetFormat, sigFormat, data);
        if (key == TARGET_FORMAT) return new Signature(extensionData, type, when, who, onBehalfOf, (Code) val, sigFormat, data);
        if (key == SIG_FORMAT) return new Signature(extensionData, type, when, who, onBehalfOf, targetFormat, (Code) val, data);
        if (key == DATA) return new Signature(extensionData, type, when, who, onBehalfOf, targetFormat, sigFormat, (Base64Binary) val);
        if (key == EXTENSION)
            return new Signature(extensionData.withExtension(val), type, when, who, onBehalfOf, targetFormat, sigFormat, data);
        if (key == ID) return new Signature(extensionData.withId(val), type, when, who, onBehalfOf, targetFormat, sigFormat, data);
        return this;
    }

    @Override
    public Signature withMeta(IPersistentMap meta) {
        return new Signature(extensionData.withMeta(meta), type, when, who, onBehalfOf, targetFormat, sigFormat, data);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (!type.isEmpty()) {
            serializeJsonComplexList(type, generator, FIELD_NAME_TYPE.normal());
        }
        if (when != null) {
            when.serializeAsJsonProperty(generator, FIELD_NAME_WHEN);
        }
        if (who != null) {
            who.serializeJsonField(generator, FIELD_NAME_WHO);
        }
        if (onBehalfOf != null) {
            onBehalfOf.serializeJsonField(generator, FIELD_NAME_ON_BEHALF_OF);
        }
        if (targetFormat != null) {
            targetFormat.serializeAsJsonProperty(generator, FIELD_NAME_TARGET_FORMAT);
        }
        if (sigFormat != null) {
            sigFormat.serializeAsJsonProperty(generator, FIELD_NAME_SIG_FORMAT);
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
        if (!type.isEmpty()) {
            sink.putByte((byte) 2);
            Base.hashIntoList(type, sink);
        }
        if (when != null) {
            sink.putByte((byte) 3);
            when.hashInto(sink);
        }
        if (who != null) {
            sink.putByte((byte) 4);
            who.hashInto(sink);
        }
        if (onBehalfOf != null) {
            sink.putByte((byte) 5);
            onBehalfOf.hashInto(sink);
        }
        if (targetFormat != null) {
            sink.putByte((byte) 6);
            targetFormat.hashInto(sink);
        }
        if (sigFormat != null) {
            sink.putByte((byte) 7);
            sigFormat.hashInto(sink);
        }
        if (data != null) {
            sink.putByte((byte) 8);
            data.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(type) + Base.memSize(when) +
                Base.memSize(who) + Base.memSize(onBehalfOf) + Base.memSize(targetFormat) +
                Base.memSize(sigFormat) + Base.memSize(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Signature that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(type, that.type) &&
                Objects.equals(when, that.when) &&
                Objects.equals(who, that.who) &&
                Objects.equals(onBehalfOf, that.onBehalfOf) &&
                Objects.equals(targetFormat, that.targetFormat) &&
                Objects.equals(sigFormat, that.sigFormat) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(when);
        result = 31 * result + Objects.hashCode(who);
        result = 31 * result + Objects.hashCode(onBehalfOf);
        result = 31 * result + Objects.hashCode(targetFormat);
        result = 31 * result + Objects.hashCode(sigFormat);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Signature{" +
                extensionData +
                ", type=" + type +
                ", when=" + when +
                ", who=" + who +
                ", onBehalfOf=" + onBehalfOf +
                ", targetFormat=" + targetFormat +
                ", sigFormat=" + sigFormat +
                ", data=" + data +
                '}';
    }
}
