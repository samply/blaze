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

public final class Address extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Address");

    private static final Keyword USE = Keyword.intern("use");
    private static final Keyword TYPE = Keyword.intern("type");
    private static final Keyword TEXT = Keyword.intern("text");
    private static final Keyword LINE = Keyword.intern("line");
    private static final Keyword CITY = Keyword.intern("city");
    private static final Keyword DISTRICT = Keyword.intern("district");
    private static final Keyword STATE = Keyword.intern("state");
    private static final Keyword POSTAL_CODE = Keyword.intern("postalCode");
    private static final Keyword COUNTRY = Keyword.intern("country");
    private static final Keyword PERIOD = Keyword.intern("period");

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
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueAddress");

    private static final byte HASH_MARKER = 47;

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

    public Address(java.lang.String id, PersistentVector extension, Code use, Code type, String text, List<String> line, String city, String district, String state, String postalCode, String country, Period period) {
        super(id, extension);
        this.use = use;
        this.type = type;
        this.text = text;
        this.line = line;
        this.city = city;
        this.district = district;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.period = period;
    }

    @SuppressWarnings("unchecked")
    public static Address create(IPersistentMap m) {
        return new Address((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (Code) m.valAt(USE), (Code) m.valAt(TYPE), (String) m.valAt(TEXT), (List<String>) m.valAt(LINE),
                (String) m.valAt(CITY), (String) m.valAt(DISTRICT), (String) m.valAt(STATE),
                (String) m.valAt(POSTAL_CODE), (String) m.valAt(COUNTRY), (Period) m.valAt(PERIOD));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "use"),
                Symbol.intern(null, "type"), Symbol.intern(null, "text"), Symbol.intern(null, "line"),
                Symbol.intern(null, "city"), Symbol.intern(null, "district"), Symbol.intern(null, "state"),
                Symbol.intern(null, "postalCode"), Symbol.intern(null, "country"), Symbol.intern(null, "period"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public Object valAt(Object key, Object notFound) {
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
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public IPersistentCollection empty() {
        return new Address(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Address assoc(Object key, Object val) {
        if (key == ID)
            return new Address((java.lang.String) val, extension, use, type, text, line, city, district, state, postalCode, country, period);
        if (key == EXTENSION)
            return new Address(id, (PersistentVector) val, use, type, text, line, city, district, state, postalCode, country, period);
        if (key == USE)
            return new Address(id, extension, (Code) val, type, text, line, city, district, state, postalCode, country, period);
        if (key == TYPE)
            return new Address(id, extension, use, (Code) val, text, line, city, district, state, postalCode, country, period);
        if (key == TEXT)
            return new Address(id, extension, use, type, (String) val, line, city, district, state, postalCode, country, period);
        if (key == LINE)
            return new Address(id, extension, use, type, text, (List<String>) val, city, district, state, postalCode, country, period);
        if (key == CITY)
            return new Address(id, extension, use, type, text, line, (String) val, district, state, postalCode, country, period);
        if (key == DISTRICT)
            return new Address(id, extension, use, type, text, line, city, (String) val, state, postalCode, country, period);
        if (key == STATE)
            return new Address(id, extension, use, type, text, line, city, district, (String) val, postalCode, country, period);
        if (key == POSTAL_CODE)
            return new Address(id, extension, use, type, text, line, city, district, state, (String) val, country, period);
        if (key == COUNTRY)
            return new Address(id, extension, use, type, text, line, city, district, state, postalCode, (String) val, period);
        if (key == PERIOD)
            return new Address(id, extension, use, type, text, line, city, district, state, postalCode, country, (Period) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Address.");
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
        if (line != null && !line.isEmpty()) {
            seq = appendElement(seq, LINE, line);
        }
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, TYPE, type);
        seq = appendElement(seq, USE, use);
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
        if (use != null) {
            use.serializeAsJsonProperty(generator, FIELD_NAME_USE);
        }
        if (type != null) {
            type.serializeAsJsonProperty(generator, FIELD_NAME_TYPE);
        }
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        if (line != null && !line.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(line, generator, FIELD_NAME_LINE);
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
        if (line != null && !line.isEmpty()) {
            sink.putByte((byte) 5);
            sink.putByte((byte) 36);
            for (String s : line) {
                s.hashInto(sink);
            }
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address that = (Address) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
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
        return Objects.hash(id, extension, use, type, text, line, city,
                district, state, postalCode, country, period);
    }

    @Override
    public java.lang.String toString() {
        return "Address{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
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
