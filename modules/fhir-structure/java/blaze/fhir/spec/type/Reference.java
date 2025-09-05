package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

public final class Reference extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Reference");

    private static final Keyword REFERENCE = Keyword.intern("reference");
    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword IDENTIFIER = Keyword.intern("identifier");
    private static final Keyword DISPLAY = Keyword.intern("display");

    private static final SerializedString FIELD_NAME_REFERENCE = new SerializedString("reference");
    private static final SerializedString FIELD_NAME_TYPE = new SerializedString("type");
    private static final SerializedString FIELD_NAME_IDENTIFIER = new SerializedString("identifier");
    private static final SerializedString FIELD_NAME_DISPLAY = new SerializedString("display");

    private static final byte HASH_MARKER = 44;

    private final String reference;
    private final Uri type;
    private final Identifier identifier;
    private final String display;

    public Reference(java.lang.String id, PersistentVector extension, String reference, Uri type, Identifier identifier, String display) {
        super(id, extension);
        this.reference = reference;
        this.type = type;
        this.identifier = identifier;
        this.display = display;
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
    public Object valAt(Object key) {
        return valAt(key, null);
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
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (reference != null && reference.value() != null) {
            generator.writeFieldName(FIELD_NAME_REFERENCE);
            reference.serializeJson(generator);
        }
        if (type != null && type.value() != null) {
            generator.writeFieldName(FIELD_NAME_TYPE);
            type.serializeJson(generator);
        }
        if (identifier != null) {
            generator.writeFieldName(FIELD_NAME_IDENTIFIER);
            identifier.serializeJson(generator);
        }
        if (display != null && display.value() != null) {
            generator.writeFieldName(FIELD_NAME_DISPLAY);
            display.serializeJson(generator);
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
                Objects.equals(extension, that.extension) &&
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
                "id='" + id + '\'' +
                ", extension=" + extension +
                ", reference='" + reference + '\'' +
                ", type=" + type +
                ", identifier=" + identifier +
                ", display='" + display + '\'' +
                '}';
    }
}
