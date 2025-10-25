package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class Quantity extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - value reference
     * 4 byte - comparator reference
     * 4 byte - unit reference
     * 4 byte - system reference
     * 4 byte - code reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 24;

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

    private static final Interner<InternerKey, Quantity> INTERNER = Interners.weakInterner(
            k -> new Quantity(k.extensionData, k.value, k.comparator, k.unit, k.system, k.code)
    );
    private static final Quantity EMPTY = new Quantity(ExtensionData.EMPTY, null, null, null, null, null);

    private final Decimal value;
    private final Code comparator;
    private final String unit;
    private final Uri system;
    private final Code code;

    private Quantity(ExtensionData extensionData, Decimal value, Code comparator, String unit, Uri system, Code code) {
        super(extensionData);
        this.value = value;
        this.comparator = comparator;
        this.unit = unit;
        this.system = system;
        this.code = code;
    }

    private static Quantity maybeIntern(ExtensionData extensionData, Decimal value, Code comparator, String unit,
                                        Uri system, Code code) {
        return extensionData.isInterned() && Base.isInterned(value) && Base.isInterned(comparator) &&
                Base.isInterned(unit) && Base.isInterned(system) && Base.isInterned(code)
                ? INTERNER.intern(new InternerKey(extensionData, value, comparator, unit, system, code))
                : new Quantity(extensionData, value, comparator, unit, system, code);
    }

    public static Quantity create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (Decimal) m.valAt(VALUE), (Code) m.valAt(COMPARATOR),
                (String) m.valAt(UNIT), (Uri) m.valAt(SYSTEM), (Code) m.valAt(CODE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned() && Base.isInterned(value) && Base.isInterned(comparator) &&
                Base.isInterned(unit) && Base.isInterned(system) && Base.isInterned(code);
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
        if (key == COMPARATOR) return comparator;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, SYSTEM, system);
        seq = appendElement(seq, UNIT, unit);
        seq = appendElement(seq, COMPARATOR, comparator);
        seq = appendElement(seq, VALUE, value);
        return extensionData.append(seq);
    }

    @Override
    public Quantity empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Quantity assoc(Object key, Object val) {
        if (key == ID)
            return maybeIntern(extensionData.withId((java.lang.String) val), value, comparator, unit, system, code);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)),
                    value, comparator, unit, system, code);
        if (key == VALUE)
            return maybeIntern(extensionData, (Decimal) val, comparator, unit, system, code);
        if (key == COMPARATOR)
            return maybeIntern(extensionData, value, (Code) val, unit, system, code);
        if (key == UNIT)
            return maybeIntern(extensionData, value, comparator, (String) val, system, code);
        if (key == SYSTEM)
            return maybeIntern(extensionData, value, comparator, unit, (Uri) val, code);
        if (key == CODE)
            return maybeIntern(extensionData, value, comparator, unit, system, (Code) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Quantity.");
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
        extensionData.hashInto(sink);
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
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(value) +
                Base.memSize(comparator) + Base.memSize(unit) + Base.memSize(system) + Base.memSize(code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Quantity that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value) &&
                Objects.equals(comparator, that.comparator) &&
                Objects.equals(unit, that.unit) &&
                Objects.equals(system, that.system) &&
                Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(comparator);
        result = 31 * result + Objects.hashCode(unit);
        result = 31 * result + Objects.hashCode(system);
        result = 31 * result + Objects.hashCode(code);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Quantity{" +
                extensionData +
                ", value=" + value +
                ", comparator=" + comparator +
                ", unit=" + unit +
                ", system=" + system +
                ", code=" + code +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, Decimal value, Code comparator, String unit, Uri system,
                               Code code) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
