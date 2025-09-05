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

public final class HumanName extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "HumanName");

    private static final Keyword USE = Keyword.intern("use");
    private static final Keyword TEXT = Keyword.intern("text");
    private static final Keyword FAMILY = Keyword.intern("family");
    private static final Keyword GIVEN = Keyword.intern("given");
    private static final Keyword PREFIX = Keyword.intern("prefix");
    private static final Keyword SUFFIX = Keyword.intern("suffix");
    private static final Keyword PERIOD = Keyword.intern("period");

    private static final Keyword[] FIELDS = {ID, EXTENSION, USE, TEXT, FAMILY, GIVEN, PREFIX, SUFFIX, PERIOD};

    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_FAMILY = FieldName.of("family");
    private static final FieldName FIELD_NAME_GIVEN = FieldName.of("given");
    private static final FieldName FIELD_NAME_PREFIX = FieldName.of("prefix");
    private static final FieldName FIELD_NAME_SUFFIX = FieldName.of("suffix");
    private static final SerializedString FIELD_NAME_PERIOD = new SerializedString("period");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueHumanName");

    private static final byte HASH_MARKER = 46;

    private final Code use;
    private final String text;
    private final String family;
    private final List<String> given;
    private final List<String> prefix;
    private final List<String> suffix;
    private final Period period;

    public HumanName(java.lang.String id, PersistentVector extension, Code use, String text, String family, List<String> given, List<String> prefix, List<String> suffix, Period period) {
        super(id, extension);
        this.use = use;
        this.text = text;
        this.family = family;
        this.given = given;
        this.prefix = prefix;
        this.suffix = suffix;
        this.period = period;
    }

    @SuppressWarnings("unchecked")
    public static HumanName create(IPersistentMap m) {
        return new HumanName((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (Code) m.valAt(USE), (String) m.valAt(TEXT), (String) m.valAt(FAMILY),
                (List<String>) m.valAt(GIVEN), (List<String>) m.valAt(PREFIX), (List<String>) m.valAt(SUFFIX),
                (Period) m.valAt(PERIOD));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "use"),
                Symbol.intern(null, "text"), Symbol.intern(null, "family"), Symbol.intern(null, "given"),
                Symbol.intern(null, "prefix"), Symbol.intern(null, "suffix"), Symbol.intern(null, "period"));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code use() {
        return use;
    }

    public String text() {
        return text;
    }

    public String family() {
        return family;
    }

    public List<String> given() {
        return given;
    }

    public List<String> prefix() {
        return prefix;
    }

    public List<String> suffix() {
        return suffix;
    }

    public Period period() {
        return period;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == USE) return use;
        if (key == TEXT) return text;
        if (key == FAMILY) return family;
        if (key == GIVEN) return given;
        if (key == PREFIX) return prefix;
        if (key == SUFFIX) return suffix;
        if (key == PERIOD) return period;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public IPersistentCollection empty() {
        return new HumanName(null, null, null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public HumanName assoc(Object key, Object val) {
        if (key == ID)
            return new HumanName((java.lang.String) val, extension, use, text, family, given, prefix, suffix, period);
        if (key == EXTENSION)
            return new HumanName(id, (PersistentVector) val, use, text, family, given, prefix, suffix, period);
        if (key == USE)
            return new HumanName(id, extension, (Code) val, text, family, given, prefix, suffix, period);
        if (key == TEXT)
            return new HumanName(id, extension, use, (String) val, family, given, prefix, suffix, period);
        if (key == FAMILY)
            return new HumanName(id, extension, use, text, (String) val, given, prefix, suffix, period);
        if (key == GIVEN)
            return new HumanName(id, extension, use, text, family, (List<String>) val, prefix, suffix, period);
        if (key == PREFIX)
            return new HumanName(id, extension, use, text, family, given, (List<String>) val, suffix, period);
        if (key == SUFFIX)
            return new HumanName(id, extension, use, text, family, given, prefix, (List<String>) val, period);
        if (key == PERIOD)
            return new HumanName(id, extension, use, text, family, given, prefix, suffix, (Period) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.HumanName.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PERIOD, period);
        if (suffix != null && !suffix.isEmpty()) {
            seq = appendElement(seq, SUFFIX, suffix);
        }
        if (prefix != null && !prefix.isEmpty()) {
            seq = appendElement(seq, PREFIX, prefix);
        }
        if (given != null && !given.isEmpty()) {
            seq = appendElement(seq, GIVEN, given);
        }
        seq = appendElement(seq, FAMILY, family);
        seq = appendElement(seq, TEXT, text);
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
        if (text != null) {
            text.serializeAsJsonProperty(generator, FIELD_NAME_TEXT);
        }
        if (family != null) {
            family.serializeAsJsonProperty(generator, FIELD_NAME_FAMILY);
        }
        if (given != null && !given.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(given, generator, FIELD_NAME_GIVEN);
        }
        if (prefix != null && !prefix.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(prefix, generator, FIELD_NAME_PREFIX);
        }
        if (suffix != null && !suffix.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(suffix, generator, FIELD_NAME_SUFFIX);
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
        if (text != null) {
            sink.putByte((byte) 3);
            text.hashInto(sink);
        }
        if (family != null) {
            sink.putByte((byte) 4);
            family.hashInto(sink);
        }
        if (given != null && !given.isEmpty()) {
            sink.putByte((byte) 5);
            sink.putByte((byte) 36);
            for (String s : given) {
                s.hashInto(sink);
            }
        }
        if (prefix != null && !prefix.isEmpty()) {
            sink.putByte((byte) 6);
            sink.putByte((byte) 36);
            for (String s : prefix) {
                s.hashInto(sink);
            }
        }
        if (suffix != null && !suffix.isEmpty()) {
            sink.putByte((byte) 7);
            sink.putByte((byte) 36);
            for (String s : suffix) {
                s.hashInto(sink);
            }
        }
        if (period != null) {
            sink.putByte((byte) 8);
            period.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HumanName that = (HumanName) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(use, that.use) &&
                Objects.equals(text, that.text) &&
                Objects.equals(family, that.family) &&
                Objects.equals(given, that.given) &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(suffix, that.suffix) &&
                Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, use, text, family, given, prefix, suffix, period);
    }

    @Override
    public java.lang.String toString() {
        return "HumanName{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", use=" + use +
                ", text=" + text +
                ", family=" + family +
                ", given=" + given +
                ", prefix=" + prefix +
                ", suffix=" + suffix +
                ", period=" + period +
                '}';
    }
}
