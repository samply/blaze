package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Address extends Element {

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

    private static final SerializedString FIELD_NAME_USE = new SerializedString("use");
    private static final SerializedString FIELD_NAME_TYPE = new SerializedString("type");
    private static final SerializedString FIELD_NAME_TEXT = new SerializedString("text");
    private static final SerializedString FIELD_NAME_LINE = new SerializedString("line");
    private static final SerializedString FIELD_NAME_CITY = new SerializedString("city");
    private static final SerializedString FIELD_NAME_DISTRICT = new SerializedString("district");
    private static final SerializedString FIELD_NAME_STATE = new SerializedString("state");
    private static final SerializedString FIELD_NAME_POSTAL_CODE = new SerializedString("postalCode");
    private static final SerializedString FIELD_NAME_COUNTRY = new SerializedString("country");
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");

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
        if (text != null && text.value() != null) {
            generator.writeFieldName(FIELD_NAME_TEXT);
            text.serializeJson(generator);
        }
        if (line != null && !line.isEmpty()) {
            generator.writeFieldName(FIELD_NAME_LINE);
            generator.writeStartArray();
            for (String s : line) {
                s.serializeJson(generator);
            }
            generator.writeEndArray();
        }
        if (city != null && city.value() != null) {
            generator.writeFieldName(FIELD_NAME_CITY);
            city.serializeJson(generator);
        }
        if (district != null && district.value() != null) {
            generator.writeFieldName(FIELD_NAME_DISTRICT);
            district.serializeJson(generator);
        }
        if (state != null && state.value() != null) {
            generator.writeFieldName(FIELD_NAME_STATE);
            state.serializeJson(generator);
        }
        if (postalCode != null && postalCode.value() != null) {
            generator.writeFieldName(FIELD_NAME_POSTAL_CODE);
            postalCode.serializeJson(generator);
        }
        if (country != null && country.value() != null) {
            generator.writeFieldName(FIELD_NAME_COUNTRY);
            country.serializeJson(generator);
        }
        if (period != null) {
            generator.writeFieldName(FIELD_NAME_PERIOD);
            period.serializeJson(generator);
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
                ", text='" + text + "'" +
                ", line=" + line +
                ", city='" + city + "'" +
                ", district='" + district + "'" +
                ", state='" + state + "'" +
                ", postalCode='" + postalCode + "'" +
                ", country='" + country + "'" +
                ", period=" + period +
                '}';
    }
}
