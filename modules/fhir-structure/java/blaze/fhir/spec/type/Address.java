package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class Address extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - use reference
     * 4 or 8 byte - type reference
     * 4 or 8 byte - text reference
     * 4 or 8 byte - line reference
     * 4 or 8 byte - city reference
     * 4 or 8 byte - district reference
     * 4 or 8 byte - state reference
     * 4 or 8 byte - postalCode reference
     * 4 or 8 byte - country reference
     * 4 or 8 byte - period reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 11 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Address");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk USE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.use : this;
        }
    };

    private static final ILookupThunk TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.type : this;
        }
    };

    private static final ILookupThunk TEXT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.text : this;
        }
    };

    private static final ILookupThunk LINE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.line : this;
        }
    };

    private static final ILookupThunk CITY_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.city : this;
        }
    };

    private static final ILookupThunk DISTRICT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.district : this;
        }
    };

    private static final ILookupThunk STATE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.state : this;
        }
    };

    private static final ILookupThunk POSTAL_CODE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.postalCode : this;
        }
    };

    private static final ILookupThunk COUNTRY_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.country : this;
        }
    };

    private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Address a ? a.period : this;
        }
    };

    private static final Keyword USE = RT.keyword(null, "use");
    private static final Keyword TYPE = RT.keyword(null, "type");
    private static final Keyword TEXT = RT.keyword(null, "text");
    private static final Keyword LINE = RT.keyword(null, "line");
    private static final Keyword CITY = RT.keyword(null, "city");
    private static final Keyword DISTRICT = RT.keyword(null, "district");
    private static final Keyword STATE = RT.keyword(null, "state");
    private static final Keyword POSTAL_CODE = RT.keyword(null, "postalCode");
    private static final Keyword COUNTRY = RT.keyword(null, "country");
    private static final Keyword PERIOD = RT.keyword(null, "period");

    private static final Keyword[] FIELDS = {ID, EXTENSION, USE, TYPE, TEXT, LINE, CITY, DISTRICT, STATE, POSTAL_CODE, COUNTRY, PERIOD};

    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_TYPE = FieldName.of("type");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_LINE = FieldName.of("line");
    private static final FieldName FIELD_NAME_CITY = FieldName.of("city");
    private static final FieldName FIELD_NAME_DISTRICT = FieldName.of("district");
    private static final FieldName FIELD_NAME_STATE = FieldName.of("state");
    private static final FieldName FIELD_NAME_POSTAL_CODE = FieldName.of("postalCode");
    private static final FieldName FIELD_NAME_COUNTRY = FieldName.of("country");
    private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueAddress");

    private static final byte HASH_MARKER = 47;

    @SuppressWarnings("unchecked")
    private static final Address EMPTY = new Address(ExtensionData.EMPTY, null, null, null, PersistentVector.EMPTY,
            null, null, null, null, null, null);

    private final Code use;
    private final Code type;
    private final String text;
    private final List<String> line;
    private final String city;
    private final String district;
    private final String state;
    private final String postalCode;
    private final String country;
    private final Period period;

    private Address(ExtensionData extensionData, Code use, Code type, String text, List<String> line,
                    String city, String district, String state, String postalCode, String country, Period period) {
        super(extensionData);
        this.use = use;
        this.type = type;
        this.text = text;
        this.line = requireNonNull(line);
        this.city = city;
        this.district = district;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.period = period;
    }

    public static Address create(IPersistentMap m) {
        return new Address(ExtensionData.fromMap(m), (Code) m.valAt(USE), (Code) m.valAt(TYPE), (String) m.valAt(TEXT),
                Base.listFrom(m, LINE), (String) m.valAt(CITY), (String) m.valAt(DISTRICT), (String) m.valAt(STATE),
                (String) m.valAt(POSTAL_CODE), (String) m.valAt(COUNTRY), (Period) m.valAt(PERIOD));
    }

    public Code use() {
        return use;
    }

    public Code type() {
        return type;
    }

    public String text() {
        return text;
    }

    public List<String> line() {
        return line;
    }

    public String city() {
        return city;
    }

    public String district() {
        return district;
    }

    public String state() {
        return state;
    }

    public String postalCode() {
        return postalCode;
    }

    public String country() {
        return country;
    }

    public Period period() {
        return period;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == USE) return USE_LOOKUP_THUNK;
        if (key == TYPE) return TYPE_LOOKUP_THUNK;
        if (key == TEXT) return TEXT_LOOKUP_THUNK;
        if (key == LINE) return LINE_LOOKUP_THUNK;
        if (key == CITY) return CITY_LOOKUP_THUNK;
        if (key == DISTRICT) return DISTRICT_LOOKUP_THUNK;
        if (key == STATE) return STATE_LOOKUP_THUNK;
        if (key == POSTAL_CODE) return POSTAL_CODE_LOOKUP_THUNK;
        if (key == COUNTRY) return COUNTRY_LOOKUP_THUNK;
        if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == USE) return use;
        if (key == TYPE) return type;
        if (key == TEXT) return text;
        if (key == LINE) return line;
        if (key == CITY) return city;
        if (key == DISTRICT) return district;
        if (key == STATE) return state;
        if (key == POSTAL_CODE) return postalCode;
        if (key == COUNTRY) return country;
        if (key == PERIOD) return period;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PERIOD, period);
        seq = appendElement(seq, COUNTRY, country);
        seq = appendElement(seq, POSTAL_CODE, postalCode);
        seq = appendElement(seq, STATE, state);
        seq = appendElement(seq, DISTRICT, district);
        seq = appendElement(seq, CITY, city);
        if (!line.isEmpty()) {
            seq = appendElement(seq, LINE, line);
        }
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, USE, use);
        return extensionData.append(seq);
    }

    @Override
    public Address empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Address assoc(Object key, Object val) {
        if (key == USE)
            return new Address(extensionData, (Code) val, type, text, line, city, district, state, postalCode, country, period);
        if (key == TYPE)
            return new Address(extensionData, use, (Code) val, text, line, city, district, state, postalCode, country, period);
        if (key == TEXT)
            return new Address(extensionData, use, type, (String) val, line, city, district, state, postalCode, country, period);
        if (key == LINE)
            return new Address(extensionData, use, type, text, Lists.nullToEmpty(val), city, district, state, postalCode, country, period);
        if (key == CITY)
            return new Address(extensionData, use, type, text, line, (String) val, district, state, postalCode, country, period);
        if (key == DISTRICT)
            return new Address(extensionData, use, type, text, line, city, (String) val, state, postalCode, country, period);
        if (key == STATE)
            return new Address(extensionData, use, type, text, line, city, district, (String) val, postalCode, country, period);
        if (key == POSTAL_CODE)
            return new Address(extensionData, use, type, text, line, city, district, state, (String) val, country, period);
        if (key == COUNTRY)
            return new Address(extensionData, use, type, text, line, city, district, state, postalCode, (String) val, period);
        if (key == PERIOD)
            return new Address(extensionData, use, type, text, line, city, district, state, postalCode, country, (Period) val);
        if (key == EXTENSION)
            return new Address(extensionData.withExtension(val), use, type, text, line, city, district, state, postalCode, country, period);
        if (key == ID)
            return new Address(extensionData.withId(val), use, type, text, line, city, district, state, postalCode, country, period);
        return this;
    }

    @Override
    public Address withMeta(IPersistentMap meta) {
        return new Address(extensionData.withMeta(meta), use, type, text, line, city, district, state, postalCode, country, period);
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
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        if (!line.isEmpty()) {
            Primitive.serializeJsonPrimitiveList(line, generator, FIELD_NAME_LINE);
        }
        if (city != null) {
            city.serializeAsJsonProperty(generator, FIELD_NAME_CITY);
        }
        if (district != null) {
            district.serializeAsJsonProperty(generator, FIELD_NAME_DISTRICT);
        }
        if (state != null) {
            state.serializeAsJsonProperty(generator, FIELD_NAME_STATE);
        }
        if (postalCode != null) {
            postalCode.serializeAsJsonProperty(generator, FIELD_NAME_POSTAL_CODE);
        }
        if (country != null) {
            country.serializeAsJsonProperty(generator, FIELD_NAME_COUNTRY);
        }
        if (period != null) {
            period.serializeJsonField(generator, FIELD_NAME_PERIOD);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (use != null) {
            sink.putByte((byte) 2);
            use.hashInto(sink);
        }
        if (type != null) {
            sink.putByte((byte) 3);
            type.hashInto(sink);
        }
        if (text != null) {
            sink.putByte((byte) 4);
            text.hashInto(sink);
        }
        if (!line.isEmpty()) {
            sink.putByte((byte) 5);
            Base.hashIntoList(line, sink);
        }
        if (city != null) {
            sink.putByte((byte) 6);
            city.hashInto(sink);
        }
        if (district != null) {
            sink.putByte((byte) 7);
            district.hashInto(sink);
        }
        if (state != null) {
            sink.putByte((byte) 8);
            state.hashInto(sink);
        }
        if (postalCode != null) {
            sink.putByte((byte) 9);
            postalCode.hashInto(sink);
        }
        if (country != null) {
            sink.putByte((byte) 10);
            country.hashInto(sink);
        }
        if (period != null) {
            sink.putByte((byte) 11);
            period.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(use) + Base.memSize(type) + Base.memSize(text) +
                Base.memSize(line) + Base.memSize(city) + Base.memSize(district) + Base.memSize(state) +
                Base.memSize(postalCode) + Base.memSize(country) + Base.memSize(period);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Address that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(use, that.use) &&
                Objects.equals(type, that.type) &&
                Objects.equals(text, that.text) &&
                Objects.equals(line, that.line) &&
                Objects.equals(city, that.city) &&
                Objects.equals(district, that.district) &&
                Objects.equals(state, that.state) &&
                Objects.equals(postalCode, that.postalCode) &&
                Objects.equals(country, that.country) &&
                Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(use);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(text);
        result = 31 * result + Objects.hashCode(line);
        result = 31 * result + Objects.hashCode(city);
        result = 31 * result + Objects.hashCode(district);
        result = 31 * result + Objects.hashCode(state);
        result = 31 * result + Objects.hashCode(postalCode);
        result = 31 * result + Objects.hashCode(country);
        result = 31 * result + Objects.hashCode(period);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Address{" +
                extensionData +
                ", use=" + use +
                ", type=" + type +
                ", text=" + text +
                ", line=" + line +
                ", city=" + city +
                ", district=" + district +
                ", state=" + state +
                ", postalCode=" + postalCode +
                ", country=" + country +
                ", period=" + period +
                '}';
    }
}
