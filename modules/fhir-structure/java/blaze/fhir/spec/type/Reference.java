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

public final class Reference extends Element implements Complex, ExtensionValue {

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

    private final String reference;
    private final Uri type;
    private final Identifier identifier;
    private final String display;

    public Reference(java.lang.String id, List<Extension> extension, String reference, Uri type, Identifier identifier, String display) {
        super(id, extension);
        this.reference = reference;
        this.type = type;
        this.identifier = identifier;
        this.display = display;
    }

    public static Reference create(IPersistentMap m) {
        return new Reference((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (String) m.valAt(REFERENCE), (Uri) m.valAt(TYPE), (Identifier) m.valAt(IDENTIFIER),
                (String) m.valAt(DISPLAY));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "reference"),
                Symbol.intern(null, "type"), Symbol.intern(null, "identifier"), Symbol.intern(null, "display"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(reference) && Base.isInterned(type) &&
                Base.isInterned(identifier) && Base.isInterned(display);
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
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, DISPLAY, display);
        seq = appendElement(seq, IDENTIFIER, identifier);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, REFERENCE, reference);
        return appendBase(seq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new Reference(null, PersistentVector.EMPTY, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Reference assoc(Object key, Object val) {
        if (key == ID)
            return new Reference((java.lang.String) val, extension, reference, type, identifier, display);
        if (key == EXTENSION)
            return new Reference(id, (List<Extension>) val, reference, type, identifier, display);
        if (key == REFERENCE)
            return new Reference(id, extension, (String) val, type, identifier, display);
        if (key == TYPE)
            return new Reference(id, extension, reference, (Uri) val, identifier, display);
        if (key == IDENTIFIER)
            return new Reference(id, extension, reference, type, (Identifier) val, display);
        if (key == DISPLAY)
            return new Reference(id, extension, reference, type, identifier, (String) val);
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
        hashIntoBase(sink);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reference that = (Reference) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(type, that.type) &&
                Objects.equals(identifier, that.identifier) &&
                Objects.equals(display, that.display);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, reference, type, identifier, display);
    }

    @Override
    public java.lang.String toString() {
        return "Reference{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", reference='" + reference + '\'' +
                ", type=" + type +
                ", identifier=" + identifier +
                ", display='" + display + '\'' +
                '}';
    }
}
