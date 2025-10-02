package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Identifier extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Identifier");

    private static final Keyword USE = Keyword.intern("use");
    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VALUE = Keyword.intern("value");
    private static final Keyword PERIOD = Keyword.intern("period");
    private static final Keyword ASSIGNER = Keyword.intern("assigner");

    private static final Keyword[] FIELDS = {ID, EXTENSION, USE, TYPE, SYSTEM, VALUE, PERIOD, ASSIGNER};

    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final SerializedString FIELD_NAME_TYPE = new SerializedString("type");
    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");
    private static final SerializedString FIELD_NAME_ASSIGNER = new SerializedString("assigner");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueIdentifier");

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

    public static Identifier create(IPersistentMap m) {
        return new Identifier((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (Code) m.valAt(USE), (CodeableConcept) m.valAt(TYPE), (Uri) m.valAt(SYSTEM),
                (String) m.valAt(VALUE), (Period) m.valAt(PERIOD), (Reference) m.valAt(ASSIGNER));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "use"),
                Symbol.intern(null, "type"), Symbol.intern(null, "system"), Symbol.intern(null, "value"),
                Symbol.intern(null, "period"), Symbol.intern(null, "assigner"));
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
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, ASSIGNER, assigner);
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, SYSTEM, system);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, USE, use);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new Identifier(null, null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Identifier assoc(Object key, Object val) {
        if (key == ID)
            return new Identifier((java.lang.String) val, extension, use, type, system, value, period, assigner);
        if (key == EXTENSION)
            return new Identifier(id, (PersistentVector) val, use, type, system, value, period, assigner);
        if (key == USE)
            return new Identifier(id, extension, (Code) val, type, system, value, period, assigner);
        if (key == TYPE)
            return new Identifier(id, extension, use, (CodeableConcept) val, system, value, period, assigner);
        if (key == SYSTEM)
            return new Identifier(id, extension, use, type, (Uri) val, value, period, assigner);
        if (key == VALUE)
            return new Identifier(id, extension, use, type, system, (String) val, period, assigner);
        if (key == PERIOD)
            return new Identifier(id, extension, use, type, system, value, (Period) val, assigner);
        if (key == ASSIGNER)
            return new Identifier(id, extension, use, type, system, value, period, (Reference) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Identifier.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (type != null) {
            generator.writeFieldName(FIELD_NAME_TYPE);
            type.serializeAsJsonValue(generator);
        }
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (period != null) {
            generator.writeFieldName(FIELD_NAME_PERIOD);
            period.serializeAsJsonValue(generator);
        }
        if (assigner != null) {
            generator.writeFieldName(FIELD_NAME_ASSIGNER);
            assigner.serializeAsJsonValue(generator);
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
                "id=" + (id == null ? null : '\'' + id + '\'') +
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
