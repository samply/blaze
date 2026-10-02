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
public sealed abstract class CodeableConcept extends AbstractElement implements Complex, ExtensionValue
        permits CodeableConcept.Normal, CodeableConcept.Interned {

    /**
     * Memory size of a not interned CodeableConcept.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - coding reference
     * 4 or 8 byte - text reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

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
            k -> new Interned(k.extensionData, k.coding, k.text)
    );

    private final PersistentVector coding;
    private final String text;

    private CodeableConcept(ExtensionData extensionData, PersistentVector coding, String text) {
        super(extensionData);
        this.coding = requireNonNull(coding);
        this.text = text;
    }

    private static CodeableConcept maybeIntern(ExtensionData extensionData, PersistentVector coding, String text) {
        if (extensionData.isInterned() && Base.areAllInterned(coding) && Base.isInterned(text)) {
            return extensionData == ExtensionData.EMPTY && coding.isEmpty() && text == null
                    ? Interned.EMPTY
                    : INTERNER.intern(new InternerKey(extensionData, coding, text));
        }
        return new Normal(extensionData, coding, text);
    }

    public static CodeableConcept create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), Base.listFrom(m, CODING), (String) m.valAt(TEXT));
    }

    /**
     * Creates a CodeableConcept from {@code slots} holding the values of the keys of
     * {@link #fields()} at the same index.
     */
    public static CodeableConcept fromSlots(Object[] slots) {
        return maybeIntern(ExtensionData.fromSlots(slots), Lists.nullToEmpty(slots[2]), (String) slots[3]);
    }

    /**
     * Returns the keys of all fields of CodeableConcept in slot order.
     */
    public static Keyword[] fields() {
        return FIELDS.clone();
    }

    @Override
    public boolean isInterned() {
        return false;
    }

    @SuppressWarnings("unchecked")
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
    public Interned empty() {
        return Interned.EMPTY;
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
    public void collectReferences(List<PersistentVector> refs) {
        if (isInterned()) return;
        super.collectReferences(refs);
        Base.collectReferences(coding, refs);
        Base.collectReferences(text, refs);
    }

    final boolean equalComponents(CodeableConcept that) {
        return extensionData.equals(that.extensionData) &&
                Objects.equals(coding, that.coding) &&
                Objects.equals(text, that.text);
    }

    final int hashComponents() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(coding);
        result = 31 * result + Objects.hashCode(text);
        return result;
    }

    @Override
    public final java.lang.String toString() {
        return "CodeableConcept{" +
                extensionData +
                ", coding=" + coding +
                ", text=" + text +
                '}';
    }

    public static final class Normal extends CodeableConcept {

        private Normal(ExtensionData extensionData, PersistentVector coding, String text) {
            super(extensionData, coding, text);
        }

        public static Normal create(IPersistentMap m) {
            return new Normal(ExtensionData.fromMap(m), Base.listFrom(m, CODING), (String) m.valAt(TEXT));
        }

        @Override
        public int memSize() {
            return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(coding()) + Base.memSize(text());
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof CodeableConcept that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hashComponents();
        }
    }

    /**
     * An interned CodeableConcept.
     * <p>
     * Interned CodeableConcepts with equal components are identical, so they are compared by identity. Interned
     * CodeableConcepts can still be equal to not interned CodeableConcepts, because components like
     * {@link String.Normal} and {@link String.Interned} with equal values are equal.
     */
    public static final class Interned extends CodeableConcept {

        private static final Interned EMPTY = new Interned(ExtensionData.EMPTY, PersistentVector.EMPTY, null);

        private final int hash;

        private Interned(ExtensionData extensionData, PersistentVector coding, String text) {
            super(extensionData, coding, text);
            this.hash = hashComponents();
        }

        public static Interned create(IPersistentMap m) {
            if (CodeableConcept.create(m) instanceof Interned interned) return interned;
            throw new IllegalArgumentException("Can't create an interned FHIR.CodeableConcept using non-interned components.");
        }

        @Override
        public boolean isInterned() {
            return true;
        }

        @Override
        public int memSize() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Normal that && equalComponents(that);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Key of the interner.
     * <p>
     * The extension data and the text are interned, so equal ones are identical and they are compared by identity.
     * Because the key references them strongly, they can't be collected and recreated as different instances while
     * the key exists. The coding list is compared structurally, because lists aren't interned.
     */
    private static final class InternerKey {

        private final ExtensionData extensionData;
        private final PersistentVector coding;
        private final String text;
        private final int hash;

        private InternerKey(ExtensionData extensionData, PersistentVector coding, String text) {
            this.extensionData = requireNonNull(extensionData);
            this.coding = requireNonNull(coding);
            this.text = text;
            int result = System.identityHashCode(extensionData);
            result = 31 * result + coding.hashCode();
            result = 31 * result + System.identityHashCode(text);
            this.hash = result;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InternerKey that &&
                    hash == that.hash &&
                    extensionData == that.extensionData &&
                    text == that.text &&
                    coding.equals(that.coding);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
