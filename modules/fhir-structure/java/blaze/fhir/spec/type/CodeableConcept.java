package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class CodeableConcept extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - coding reference
     * 4 or 8 byte - text reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "CodeableConcept");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof CodeableConcept ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk CODING_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof CodeableConcept c ? c.coding : this;
        }
    };

    private static final ILookupThunk TEXT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof CodeableConcept c ? c.text : this;
        }
    };

    private static final Keyword CODING = RT.keyword(null, "coding");
    private static final Keyword TEXT = RT.keyword(null, "text");

    private static final Keyword[] FIELDS = {ID, EXTENSION, CODING, TEXT};

    private static final SerializedString FIELD_NAME_CODING = new SerializedString("coding");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCodeableConcept");

    private static final byte HASH_MARKER = 71;

    private static final Interner<InternerKey, CodeableConcept> INTERNER = Interners.weakInterner(
            k -> new CodeableConcept(k.extensionData, k.coding, k.text, true)
    );
    @SuppressWarnings("unchecked")
    private static final CodeableConcept EMPTY = new CodeableConcept(ExtensionData.EMPTY, PersistentVector.EMPTY, null, true);

    private final List<Coding> coding;
    private final String text;
    private final boolean interned;

    private CodeableConcept(ExtensionData extensionData, List<Coding> coding, String text, boolean interned) {
        super(extensionData);
        this.coding = requireNonNull(coding);
        this.text = text;
        this.interned = interned;
    }

    private static CodeableConcept maybeIntern(ExtensionData extensionData, List<Coding> coding, String text) {
        return extensionData.isInterned() && Base.areAllInterned(coding) && Base.isInterned(text)
                ? INTERNER.intern(new InternerKey(extensionData, coding, text))
                : new CodeableConcept(extensionData, coding, text, false);
    }

    public static CodeableConcept create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), Base.listFrom(m, CODING), (String) m.valAt(TEXT));
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public List<Coding> coding() {
        return coding;
    }

    public String text() {
        return text;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == CODING) return CODING_LOOKUP_THUNK;
        if (key == TEXT) return TEXT_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == CODING) return coding;
        if (key == TEXT) return text;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, TEXT, text);
        if (!coding.isEmpty()) {
            seq = appendElement(seq, CODING, coding);
        }
        return extensionData.append(seq);
    }

    @Override
    public CodeableConcept empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public CodeableConcept assoc(Object key, Object val) {
        if (key == CODING) return maybeIntern(extensionData, Lists.nullToEmpty(val), text);
        if (key == TEXT) return maybeIntern(extensionData, coding, (String) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), coding, text);
        if (key == ID) return maybeIntern(extensionData.withId(val), coding, text);
        return this;
    }

    @Override
    public CodeableConcept withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), coding, text);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (!coding.isEmpty()) {
            serializeJsonComplexList(coding, generator, FIELD_NAME_CODING);
        }
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (!coding.isEmpty()) {
            sink.putByte((byte) 2);
            Base.hashIntoList(coding, sink);
        }
        if (text != null) {
            sink.putByte((byte) 3);
            text.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(coding) +
                Base.memSize(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof CodeableConcept that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(coding, that.coding) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(coding);
        result = 31 * result + Objects.hashCode(text);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "CodeableConcept{" +
                extensionData +
                ", coding=" + coding +
                ", text=" + text +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, List<Coding> coding, String text) {
        private InternerKey {
            requireNonNull(extensionData);
            requireNonNull(coding);
        }
    }
}
