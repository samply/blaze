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

public final class Quantity extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Quantity");

    private static final Keyword VALUE = Keyword.intern("value");
    private static final Keyword COMPARATOR = Keyword.intern("comparator");
    private static final Keyword UNIT = Keyword.intern("unit");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword CODE = Keyword.intern("code");

    private static final Keyword[] FIELDS = {ID, EXTENSION, VALUE, COMPARATOR, UNIT, SYSTEM, CODE};

    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_COMPARATOR = FieldName.of("comparator");
    private static final FieldName FIELD_NAME_UNIT = FieldName.of("unit");
    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueQuantity");

    private static final byte HASH_MARKER = 40;

    private final Decimal value;
    private final Code comparator;
    private final String unit;
    private final Uri system;
    private final Code code;

    public Quantity(java.lang.String id, PersistentVector extension, Decimal value, Code comparator, String unit, Uri system, Code code) {
        super(id, extension);
        this.value = value;
        this.comparator = comparator;
        this.unit = unit;
        this.system = system;
        this.code = code;
    }

    public static Quantity create(IPersistentMap m) {
        return new Quantity((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (Decimal) m.valAt(VALUE), (Code) m.valAt(COMPARATOR), (String) m.valAt(UNIT),
                (Uri) m.valAt(SYSTEM), (Code) m.valAt(CODE));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "value"),
                Symbol.intern(null, "comparator"), Symbol.intern(null, "unit"), Symbol.intern(null, "system"),
                Symbol.intern(null, "code"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Decimal value() {
        return value;
    }

    public Code comparator() {
        return comparator;
    }

    public String unit() {
        return unit;
    }

    public Uri system() {
        return system;
    }

    public Code code() {
        return code;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == CODE) return code;
        if (key == SYSTEM) return system;
        if (key == UNIT) return unit;
        if (key == EXTENSION) return extension;
        if (key == COMPARATOR) return comparator;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public IPersistentCollection empty() {
        return new Quantity(null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Quantity assoc(Object key, Object val) {
        if (key == ID)
            return new Quantity((java.lang.String) val, extension, value, comparator, unit, system, code);
        if (key == EXTENSION)
            return new Quantity(id, (PersistentVector) val, value, comparator, unit, system, code);
        if (key == VALUE)
            return new Quantity(id, extension, (Decimal) val, comparator, unit, system, code);
        if (key == COMPARATOR)
            return new Quantity(id, extension, value, (Code) val, unit, system, code);
        if (key == UNIT)
            return new Quantity(id, extension, value, comparator, (String) val, system, code);
        if (key == SYSTEM)
            return new Quantity(id, extension, value, comparator, unit, (Uri) val, code);
        if (key == CODE)
            return new Quantity(id, extension, value, comparator, unit, system, (Code) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.Quantity.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, SYSTEM, system);
        seq = appendElement(seq, UNIT, unit);
        seq = appendElement(seq, COMPARATOR, comparator);
        seq = appendElement(seq, VALUE, value);
        return appendBase(seq);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (value != null) {
            value.serializeAsJsonProperty(generator, FIELD_NAME_VALUE);
        }
        if (comparator != null) {
            comparator.serializeAsJsonProperty(generator, FIELD_NAME_COMPARATOR);
        }
        if (unit != null) {
            unit.serializeAsJsonProperty(generator, FIELD_NAME_UNIT);
        }
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (code != null) {
            code.serializeAsJsonProperty(generator, FIELD_NAME_CODE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            value.hashInto(sink);
        }
        if (comparator != null) {
            sink.putByte((byte) 3);
            comparator.hashInto(sink);
        }
        if (unit != null) {
            sink.putByte((byte) 4);
            unit.hashInto(sink);
        }
        if (system != null) {
            sink.putByte((byte) 5);
            system.hashInto(sink);
        }
        if (code != null) {
            sink.putByte((byte) 6);
            code.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quantity that = (Quantity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(value, that.value) &&
                Objects.equals(comparator, that.comparator) &&
                Objects.equals(unit, that.unit) &&
                Objects.equals(system, that.system) &&
                Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value, comparator, unit, system, code);
    }

    @Override
    public java.lang.String toString() {
        return "Quantity{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                ", comparator=" + comparator +
                ", unit=" + unit +
                ", system=" + system +
                ", code=" + code +
                '}';
    }
}
