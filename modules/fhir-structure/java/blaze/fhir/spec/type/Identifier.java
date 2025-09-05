package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

public final class Identifier extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Identifier");

    private static final Keyword USE = Keyword.intern("use");
    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VALUE = Keyword.intern("value");
    private static final Keyword PERIOD = Keyword.intern("period");
    private static final Keyword ASSIGNER = Keyword.intern("assigner");

    private static final SerializedString FIELD_NAME_USE = new SerializedString("use");
    private static final SerializedString FIELD_NAME_TYPE = new SerializedString("type");
    private static final SerializedString FIELD_NAME_SYSTEM = new SerializedString("system");
    private static final SerializedString FIELD_NAME_VALUE = new SerializedString("value");
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");
    private static final SerializedString FIELD_NAME_ASSIGNER = new SerializedString("assigner");

    private static final byte HASH_MARKER = 42;

    private final Code use;
    private final CodeableConcept type;
    private final Uri system;
    private final String value;
    private final Period period;
    private final Reference assigner;

    public Identifier(java.lang.String id, PersistentVector extension, Code use, CodeableConcept type, Uri system, String value, Period period, Reference assigner) {
        super(id, extension);
        this.use = use;
        this.type = type;
        this.system = system;
        this.value = value;
        this.period = period;
        this.assigner = assigner;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code use() {
        return use;
    }

    public CodeableConcept type() {
        return type;
    }

    public Uri system() {
        return system;
    }

    public String value() {
        return value;
    }

    public Period period() {
        return period;
    }

    public Reference assigner() {
        return assigner;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == USE) return use;
        if (key == TYPE) return type;
        if (key == SYSTEM) return system;
        if (key == VALUE) return value;
        if (key == PERIOD) return period;
        if (key == ASSIGNER) return assigner;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (use != null) {
            generator.writeFieldName(FIELD_NAME_USE);
            use.serializeJson(generator);
        }
        if (type != null) {
            generator.writeFieldName(FIELD_NAME_TYPE);
            type.serializeJson(generator);
        }
        if (system != null) {
            generator.writeFieldName(FIELD_NAME_SYSTEM);
            system.serializeJson(generator);
        }
        if (value != null && value.value() != null) {
            generator.writeFieldName(FIELD_NAME_VALUE);
            value.serializeJson(generator);
        }
        if (period != null) {
            generator.writeFieldName(FIELD_NAME_PERIOD);
            period.serializeJson(generator);
        }
        if (assigner != null) {
            generator.writeFieldName(FIELD_NAME_ASSIGNER);
            assigner.serializeJson(generator);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (use != null) {
            sink.putByte((byte) 2);
            use.hashInto(sink);
        }
        if (type != null) {
            sink.putByte((byte) 3);
            type.hashInto(sink);
        }
        if (system != null) {
            sink.putByte((byte) 4);
            system.hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 5);
            value.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 6);
            period.hashInto(sink);
        }
        if (assigner != null) {
            sink.putByte((byte) 7);
            assigner.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identifier that = (Identifier) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(use, that.use) &&
                Objects.equals(type, that.type) &&
                Objects.equals(system, that.system) &&
                Objects.equals(value, that.value) &&
                Objects.equals(period, that.period) &&
                Objects.equals(assigner, that.assigner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, use, type, system, value, period, assigner);
    }

    @Override
    public java.lang.String toString() {
        return "Identifier{" +
                "id='" + id + '\'' +
                ", extension=" + extension +
                ", use=" + use +
                ", type=" + type +
                ", system=" + system +
                ", value=" + value +
                ", period=" + period +
                ", assigner=" + assigner +
                '}';
    }
}
