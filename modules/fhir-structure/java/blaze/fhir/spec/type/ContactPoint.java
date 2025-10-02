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

public final class ContactPoint extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "ContactPoint");

    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VALUE = Keyword.intern("value");
    private static final Keyword USE = Keyword.intern("use");
    private static final Keyword RANK = Keyword.intern("rank");
    private static final Keyword PERIOD = Keyword.intern("period");

    private static final Keyword[] FIELDS = {ID, EXTENSION, SYSTEM, VALUE, USE, RANK, PERIOD};

    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_RANK = FieldName.of("rank");
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueContactPoint");

    private static final byte HASH_MARKER = 53;

    private final Code system;
    private final String value;
    private final Code use;
    private final PositiveInt rank;
    private final Period period;

    public ContactPoint(java.lang.String id, PersistentVector extension, Code system, String value, Code use, PositiveInt rank, Period period) {
        super(id, extension);
        this.system = system;
        this.value = value;
        this.use = use;
        this.rank = rank;
        this.period = period;
    }

    public static ContactPoint create(IPersistentMap m) {
        return new ContactPoint((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (Code) m.valAt(SYSTEM), (String) m.valAt(VALUE), (Code) m.valAt(USE), (PositiveInt) m.valAt(RANK), (Period) m.valAt(PERIOD));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "system"),
                Symbol.intern(null, "value"), Symbol.intern(null, "use"), Symbol.intern(null, "rank"), Symbol.intern(null, "period"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code system() {
        return system;
    }

    public String value() {
        return value;
    }

    public Code use() {
        return use;
    }

    public PositiveInt rank() {
        return rank;
    }

    public Period period() {
        return period;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == SYSTEM) return system;
        if (key == VALUE) return value;
        if (key == USE) return use;
        if (key == RANK) return rank;
        if (key == PERIOD) return period;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, RANK, rank);
        seq = appendElement(seq, USE, use);
        seq = appendElement(seq, VALUE, value);
        seq = appendElement(seq, SYSTEM, system);
        return appendBase(seq);
    }

    @Override
    public IPersistentCollection empty() {
        return new ContactPoint(null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public ContactPoint assoc(Object key, Object val) {
        if (key == ID) return new ContactPoint((java.lang.String) val, extension, system, value, use, rank, period);
        if (key == EXTENSION)
            return new ContactPoint(id, (PersistentVector) val, system, value, use, rank, period);
        if (key == SYSTEM) return new ContactPoint(id, extension, (Code) val, value, use, rank, period);
        if (key == VALUE) return new ContactPoint(id, extension, system, (String) val, use, rank, period);
        if (key == USE) return new ContactPoint(id, extension, system, value, (Code) val, rank, period);
        if (key == RANK) return new ContactPoint(id, extension, system, value, use, (PositiveInt) val, period);
        if (key == PERIOD) return new ContactPoint(id, extension, system, value, use, rank, (Period) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.ContactPoint.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (rank != null) {
            rank.serializeAsJsonProperty(generator, FIELD_NAME_RANK);
        }
        if (period != null) {
            generator.writeFieldName(FIELD_NAME_PERIOD);
            period.serializeAsJsonValue(generator);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (system != null) {
            sink.putByte((byte) 2);
            system.hashInto(sink);
        }
        if (value != null) {
            sink.putByte((byte) 3);
            value.hashInto(sink);
        }
        if (use != null) {
            sink.putByte((byte) 4);
            use.hashInto(sink);
        }
        if (rank != null) {
            sink.putByte((byte) 5);
            rank.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 6);
            period.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactPoint that = (ContactPoint) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(system, that.system) &&
                Objects.equals(value, that.value) &&
                Objects.equals(use, that.use) &&
                Objects.equals(rank, that.rank) &&
                Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, system, value, use, rank, period);
    }

    @Override
    public java.lang.String toString() {
        return "ContactPoint{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", system=" + system +
                ", value=" + value +
                ", use=" + use +
                ", rank=" + rank +
                ", period=" + period +
                '}';
    }
}
