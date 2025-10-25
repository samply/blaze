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
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class CodeableConcept extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - coding reference
     * 4 byte - text reference
     * 1 byte - interned boolean
     * 3 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "CodeableConcept");

    private static final Keyword CODING = Keyword.intern("coding");
    private static final Keyword TEXT = Keyword.intern("text");

    private static final Keyword[] FIELDS = {ID, EXTENSION, CODING, TEXT};

    private static final SerializedString FIELD_NAME_CODING = new SerializedString("coding");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCodeableConcept");

    private static final byte HASH_MARKER = 39;

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
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public Object valAt(Object key, Object notFound) {
        if (key == CODING) return coding;
        if (key == TEXT) return text;
        return extensionData.valAt(key, notFound);
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
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CodeableConcept assoc(Object key, Object val) {
        if (key == CODING)
            return maybeIntern(extensionData, (List<Coding>) (val == null ? PersistentVector.EMPTY : val), text);
        if (key == TEXT) return maybeIntern(extensionData, coding, (String) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), coding, text);
        if (key == ID) return maybeIntern(extensionData.withId((java.lang.String) val), coding, text);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.CodeableConcept.");
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
            generator.writeFieldName(FIELD_NAME_CODING);
            generator.writeStartArray();
            for (Coding coding : coding) {
                coding.serializeAsJsonValue(generator);
            }
            generator.writeEndArray();
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
            sink.putByte((byte) 36);
            for (Coding coding : coding) {
                coding.hashInto(sink);
            }
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
