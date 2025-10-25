package blaze.fhir.spec.type;

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

public final class Reference extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - reference reference
     * 4 byte - type reference
     * 4 byte - identifier reference
     * 4 byte - display reference
     * 4 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 24;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Reference");

    private static final Keyword REFERENCE = Keyword.intern("reference");
    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword IDENTIFIER = Keyword.intern("identifier");
    private static final Keyword DISPLAY = Keyword.intern("display");

    private static final Keyword[] FIELDS = {ID, EXTENSION, REFERENCE, TYPE, IDENTIFIER, DISPLAY};

    private static final FieldName FIELD_NAME_REFERENCE = FieldName.of("reference");
    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final SerializedString FIELD_NAME_IDENTIFIER = new SerializedString("identifier");
    private static final FieldName FIELD_NAME_DISPLAY = FieldName.of("display");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueReference");

    private static final byte HASH_MARKER = 43;

    private static final Reference EMPTY = new Reference(ExtensionData.EMPTY, null, null, null, null);

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

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public Object valAt(Object key, Object notFound) {
        if (key == REFERENCE) return reference;
        if (key == TYPE) return type;
        if (key == IDENTIFIER) return identifier;
        if (key == DISPLAY) return display;
        return extensionData.valAt(key, notFound);
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
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Reference assoc(Object key, Object val) {
        if (key == ID)
            return new Reference(extensionData.withId((java.lang.String) val), reference, type, identifier, display);
        if (key == EXTENSION)
            return new Reference(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), reference, type, identifier, display);
        if (key == REFERENCE)
            return new Reference(extensionData, (String) val, type, identifier, display);
        if (key == TYPE)
            return new Reference(extensionData, reference, (Uri) val, identifier, display);
        if (key == IDENTIFIER)
            return new Reference(extensionData, reference, type, (Identifier) val, display);
        if (key == DISPLAY)
            return new Reference(extensionData, reference, type, identifier, (String) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Reference.");
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
            generator.writeFieldName(FIELD_NAME_IDENTIFIER);
            identifier.serializeAsJsonValue(generator);
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
