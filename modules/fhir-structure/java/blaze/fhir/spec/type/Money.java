package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Money extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - value reference
     * 4 or 8 byte - currency reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Money");

    private static final Keyword VALUE = RT.keyword(null, "value");
    private static final Keyword CURRENCY = RT.keyword(null, "currency");

    private static final Keyword[] FIELDS = {ID, EXTENSION, VALUE, CURRENCY};

    private static final FieldName FIELD_NAME_VALUE = FieldName.of("value");
    private static final FieldName FIELD_NAME_CURRENCY = FieldName.of("currency");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueMoney");

    private static final byte HASH_MARKER = 61;

    private static final Money EMPTY = new Money(ExtensionData.EMPTY, null, null);

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Money ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk VALUE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Money m ? m.value : this;
        }
    };

    private static final ILookupThunk CURRENCY_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Money m ? m.currency : this;
        }
    };

    private final Decimal value;
    private final Code currency;

    private Money(ExtensionData extensionData, Decimal value, Code currency) {
        super(extensionData);
        this.value = value;
        this.currency = currency;
    }

    public static Money create(IPersistentMap m) {
        return new Money(ExtensionData.fromMap(m), (Decimal) m.valAt(VALUE), (Code) m.valAt(CURRENCY));
    }

    public Decimal value() {
        return value;
    }

    public Code currency() {
        return currency;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == VALUE) return VALUE_LOOKUP_THUNK;
        if (key == CURRENCY) return CURRENCY_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == VALUE) return value;
        if (key == CURRENCY) return currency;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, CURRENCY, currency);
        seq = appendElement(seq, VALUE, value);
        return extensionData.append(seq);
    }

    @Override
    public Money empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Money assoc(Object key, Object val) {
        if (key == VALUE) return new Money(extensionData, (Decimal) val, currency);
        if (key == CURRENCY) return new Money(extensionData, value, (Code) val);
        if (key == EXTENSION)
            return new Money(extensionData.withExtension(val), value, currency);
        if (key == ID) return new Money(extensionData.withId(val), value, currency);
        return this;
    }

    @Override
    public Money withMeta(IPersistentMap meta) {
        return new Money(extensionData.withMeta(meta), value, currency);
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
        if (currency != null) {
            currency.serializeAsJsonProperty(generator, FIELD_NAME_CURRENCY);
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
        if (currency != null) {
            sink.putByte((byte) 3);
            currency.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(value) + Base.memSize(currency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Money that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value) &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(currency);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Money{" +
                extensionData +
                ", value=" + value +
                ", currency=" + currency +
                '}';
    }
}
