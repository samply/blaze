package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Reference extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - reference reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - identifier reference
     * 4 or 8 byte - display reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 5 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Reference");

    private static final Keyword REFERENCE = RT.keyword(null, "reference");
    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword IDENTIFIER = RT.keyword(null, "identifier");
    private static final Keyword DISPLAY = RT.keyword(null, "display");

    private static final Keyword[] FIELDS = {ID, EXTENSION, REFERENCE, TYPE, IDENTIFIER, DISPLAY};

    private static final FieldName FIELD_NAME_REFERENCE = FieldName.of("reference");
    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_IDENTIFIER = FieldName.of("identifier");
    private static final FieldName FIELD_NAME_DISPLAY = FieldName.of("display");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueReference");

    private static final byte HASH_MARKER = 43;

    private static final Reference EMPTY = new Reference(ExtensionData.EMPTY, null, null, null, null);

    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Z]([A-Za-z0-9_]){0,254}");
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Reference ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk REFERENCE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Reference r ? r.reference : this;
        }
    };

    private final String reference;
    private final Uri type;
    private final Identifier identifier;
    private final String display;

    private Reference(ExtensionData extensionData, String reference, Uri type, Identifier identifier, String display) {
        super(extensionData);
        this.reference = reference;
        this.type = type;
        this.identifier = identifier;
        this.display = display;
    }

    public static Reference create(IPersistentMap m) {
        return new Reference(ExtensionData.fromMap(m), (String) m.valAt(REFERENCE), (Uri) m.valAt(TYPE),
                (Identifier) m.valAt(IDENTIFIER), (String) m.valAt(DISPLAY));
    }

    public String reference() {
        return reference;
    }

    public Uri type() {
        return type;
    }

    public Identifier identifier() {
        return identifier;
    }

    public String display() {
        return display;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == REFERENCE) return REFERENCE_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == REFERENCE) return reference;
        if (key == TYPE) return type;
        if (key == IDENTIFIER) return identifier;
        if (key == DISPLAY) return display;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DISPLAY, display);
        seq = appendElement(seq, IDENTIFIER, identifier);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, REFERENCE, reference);
        return extensionData.append(seq);
    }

    @Override
    public Reference empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Reference assoc(Object key, Object val) {
        if (key == REFERENCE) return new Reference(extensionData, (String) val, type, identifier, display);
        if (key == TYPE) return new Reference(extensionData, reference, (Uri) val, identifier, display);
        if (key == IDENTIFIER) return new Reference(extensionData, reference, type, (Identifier) val, display);
        if (key == DISPLAY) return new Reference(extensionData, reference, type, identifier, (String) val);
        if (key == EXTENSION)
            return new Reference(extensionData.withExtension(val), reference, type, identifier, display);
        if (key == ID) return new Reference(extensionData.withId(val), reference, type, identifier, display);
        return this;
    }

    @Override
    public Reference withMeta(IPersistentMap meta) {
        return new Reference(extensionData.withMeta(meta), reference, type, identifier, display);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (reference != null) {
            reference.serializeAsJsonProperty(generator, FIELD_NAME_REFERENCE);
        }
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (identifier != null) {
            identifier.serializeJsonField(generator, FIELD_NAME_IDENTIFIER);
        }
        if (display != null) {
            display.serializeAsJsonProperty(generator, FIELD_NAME_DISPLAY);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (reference != null) {
            sink.putByte((byte) 2);
            reference.hashInto(sink);
        }
        if (type != null) {
            sink.putByte((byte) 3);
            type.hashInto(sink);
        }
        if (identifier != null) {
            sink.putByte((byte) 4);
            identifier.hashInto(sink);
        }
        if (display != null) {
            sink.putByte((byte) 5);
            display.hashInto(sink);
        }
    }

    private static boolean isType(java.lang.String x) {
        return TYPE_PATTERN.matcher(x).matches();
    }

    private static boolean isId(java.lang.String x) {
        return ID_PATTERN.matcher(x).matches();
    }

    private Stream<PersistentVector> ref() {
        var ref = reference == null ? null : reference.value();
        if (ref != null) {
            var parts = ref.split("/");
            if (parts.length == 2 && isType(parts[0]) && isId(parts[1])) {
                return Stream.of(PersistentVector.adopt(parts));
            }
        }
        return Stream.empty();
    }

    @Override
    public Stream<PersistentVector> references() {
        return Stream.concat(super.references(), ref());
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(reference) + Base.memSize(type) +
                Base.memSize(identifier) + Base.memSize(display);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Reference that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(type, that.type) &&
                Objects.equals(identifier, that.identifier) &&
                Objects.equals(display, that.display);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(reference);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(identifier);
        result = 31 * result + Objects.hashCode(display);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Reference{" +
                extensionData +
                ", reference='" + reference + '\'' +
                ", type=" + type +
                ", identifier=" + identifier +
                ", display='" + display + '\'' +
                '}';
    }
}
