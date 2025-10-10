package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Decimals;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Decimal extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "decimal");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDecimal");

    private static final byte HASH_MARKER = 4;

    private final BigDecimal value;

    public Decimal(java.lang.String id, List<Extension> extension, BigDecimal value) {
        super(id, extension);
        this.value = value;
    }

    public static Decimal create(IPersistentMap m) {
        return new Decimal((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), 
                (BigDecimal) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decimal empty() {
        return new Decimal(null, PersistentVector.EMPTY, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decimal assoc(Object key, Object val) {
        if (key == VALUE) return new Decimal(id, extension, (BigDecimal) val);
        if (key == EXTENSION) return new Decimal(id, (List<Extension>) val, value);
        if (key == ID) return new Decimal((java.lang.String) val, extension, value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Decimal.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeNumber(value);
        } else {
            generator.writeNull();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            Decimals.hashInto(value, sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Decimal c = (Decimal) o;
        return Objects.equals(id, c.id) &&
                Objects.equals(extension, c.extension) &&
                Objects.equals(value, c.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, value);
    }

    @Override
    public java.lang.String toString() {
        return "Decimal{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", value=" + value +
                '}';
    }
}
