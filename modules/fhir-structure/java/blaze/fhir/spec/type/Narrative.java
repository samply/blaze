package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Narrative extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - status reference
     * 4 or 8 byte - div reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Narrative");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Narrative ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk STATUS_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Narrative n ? n.status : this;
        }
    };

    private static final ILookupThunk DIV_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Narrative n ? n.div : this;
        }
    };

    private static final Keyword STATUS = RT.keyword(null, "status");
    private static final Keyword DIV = RT.keyword(null, "div");

    private static final Keyword[] FIELDS = {ID, EXTENSION, STATUS, DIV};

    private static final FieldName FIELD_NAME_STATUS = FieldName.of("status");
    private static final FieldName FIELD_NAME_DIV = FieldName.of("div");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueNarrative");

    private static final byte HASH_MARKER = 69;

    private static final Narrative EMPTY = new Narrative(ExtensionData.EMPTY, null, null);

    private final Code status;
    private final Xhtml div;

    private Narrative(ExtensionData extensionData, Code status, Xhtml div) {
        super(extensionData);
        this.status = status;
        this.div = div;
    }

    public static Narrative create(IPersistentMap m) {
        return new Narrative(ExtensionData.fromMap(m), (Code) m.valAt(STATUS), (Xhtml) m.valAt(DIV));
    }

    public Code status() {
        return status;
    }

    public Xhtml div() {
        return div;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == STATUS) return STATUS_LOOKUP_THUNK;
        if (key == DIV) return DIV_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == STATUS) return status;
        if (key == DIV) return div;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DIV, div);
        seq = appendElement(seq, STATUS, status);
        return extensionData.append(seq);
    }

    @Override
    public Narrative empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Narrative assoc(Object key, Object val) {
        if (key == STATUS) return new Narrative(extensionData, (Code) val, div);
        if (key == DIV) return new Narrative(extensionData, status, (Xhtml) val);
        if (key == EXTENSION) return new Narrative(extensionData.withExtension(val), status, div);
        if (key == ID) return new Narrative(extensionData.withId(val), status, div);
        return this;
    }

    @Override
    public Narrative withMeta(IPersistentMap meta) {
        return new Narrative(extensionData.withMeta(meta), status, div);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (status != null) {
            status.serializeAsJsonProperty(generator, FIELD_NAME_STATUS);
        }
        if (div != null) {
            div.serializeAsJsonProperty(generator, FIELD_NAME_DIV);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (status != null) {
            sink.putByte((byte) 2);
            status.hashInto(sink);
        }
        if (div != null) {
            sink.putByte((byte) 3);
            div.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(status) + Base.memSize(div);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Narrative that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(status, that.status) &&
                Objects.equals(div, that.div);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(div);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Narrative{" +
                extensionData +
                ", status=" + status +
                ", div=" + div +
                '}';
    }
}
