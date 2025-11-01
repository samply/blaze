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
import static java.util.Objects.requireNonNull;

public final class HumanName extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - use reference
     * 4 byte - text reference
     * 4 byte - family reference
     * 4 byte - given reference
     * 4 byte - prefix reference
     * 4 byte - suffix reference
     * 4 byte - period reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 32;

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

    @SuppressWarnings("unchecked")
    private static final HumanName EMPTY = new HumanName(ExtensionData.EMPTY, null, null, null, PersistentVector.EMPTY,
            PersistentVector.EMPTY, PersistentVector.EMPTY, null);

    private final Code use;
    private final String text;
    private final String family;
    private final List<String> given;
    private final List<String> prefix;
    private final List<String> suffix;
    private final Period period;

    private HumanName(ExtensionData extensionData, Code use, String text, String family, List<String> given,
                      List<String> prefix, List<String> suffix, Period period) {
        super(extensionData);
        this.use = use;
        this.text = text;
        this.family = family;
        this.given = requireNonNull(given);
        this.prefix = requireNonNull(prefix);
        this.suffix = requireNonNull(suffix);
        this.period = period;
    }

    public static HumanName create(IPersistentMap m) {
        return new HumanName(ExtensionData.fromMap(m), (Code) m.valAt(USE), (String) m.valAt(TEXT),
                (String) m.valAt(FAMILY), Base.listFrom(m, GIVEN), Base.listFrom(m, PREFIX), Base.listFrom(m, SUFFIX),
                (Period) m.valAt(PERIOD));
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
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == USE) return use;
        if (key == TEXT) return text;
        if (key == FAMILY) return family;
        if (key == GIVEN) return given;
        if (key == PREFIX) return prefix;
        if (key == SUFFIX) return suffix;
        if (key == PERIOD) return period;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, PERIOD, period);
        if (!suffix.isEmpty()) {
            seq = appendElement(seq, SUFFIX, suffix);
        }
        if (!prefix.isEmpty()) {
            seq = appendElement(seq, PREFIX, prefix);
        }
        if (!given.isEmpty()) {
            seq = appendElement(seq, GIVEN, given);
        }
        seq = appendElement(seq, FAMILY, family);
        seq = appendElement(seq, TEXT, text);
        seq = appendElement(seq, USE, use);
        return extensionData.append(seq);
    }

    @Override
    public HumanName empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public HumanName assoc(Object key, Object val) {
        if (key == USE) return new HumanName(extensionData, (Code) val, text, family, given, prefix, suffix, period);
        if (key == TEXT) return new HumanName(extensionData, use, (String) val, family, given, prefix, suffix, period);
        if (key == FAMILY) return new HumanName(extensionData, use, text, (String) val, given, prefix, suffix, period);
        if (key == GIVEN)
            return new HumanName(extensionData, use, text, family, (List<String>) (val == null ? PersistentVector.EMPTY : val), prefix, suffix, period);
        if (key == PREFIX)
            return new HumanName(extensionData, use, text, family, given, (List<String>) (val == null ? PersistentVector.EMPTY : val), suffix, period);
        if (key == SUFFIX)
            return new HumanName(extensionData, use, text, family, given, prefix, (List<String>) (val == null ? PersistentVector.EMPTY : val), period);
        if (key == PERIOD) return new HumanName(extensionData, use, text, family, given, prefix, suffix, (Period) val);
        if (key == EXTENSION)
            return new HumanName(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), use, text, family, given, prefix, suffix, period);
        if (key == ID)
            return new HumanName(extensionData.withId((java.lang.String) val), use, text, family, given, prefix, suffix, period);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.HumanName.");
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
        if (!given.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(given, generator, FIELD_NAME_GIVEN);
        }
        if (!prefix.isEmpty()) {
            Primitives.serializeJsonPrimitiveList(prefix, generator, FIELD_NAME_PREFIX);
        }
        if (!suffix.isEmpty()) {
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
        extensionData.hashInto(sink);
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
        if (!given.isEmpty()) {
            sink.putByte((byte) 5);
            Base.hashIntoList(given, sink);
        }
        if (!prefix.isEmpty()) {
            sink.putByte((byte) 6);
            Base.hashIntoList(prefix, sink);
        }
        if (!suffix.isEmpty()) {
            sink.putByte((byte) 7);
            Base.hashIntoList(suffix, sink);
        }
        if (period != null) {
            sink.putByte((byte) 8);
            period.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(use) + Base.memSize(text) + Base.memSize(family) +
                Base.memSize(given) + Base.memSize(prefix) + Base.memSize(suffix) + Base.memSize(period);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof HumanName that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(use, that.use) &&
                Objects.equals(text, that.text) &&
                Objects.equals(family, that.family) &&
                given.equals(that.given) &&
                prefix.equals(that.prefix) &&
                suffix.equals(that.suffix) &&
                Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(use);
        result = 31 * result + Objects.hashCode(text);
        result = 31 * result + Objects.hashCode(family);
        result = 31 * result + given.hashCode();
        result = 31 * result + prefix.hashCode();
        result = 31 * result + suffix.hashCode();
        result = 31 * result + Objects.hashCode(period);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "HumanName{" +
                extensionData +
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
