package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static blaze.fhir.spec.type.Primitive.serializeJsonPrimitiveList;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("DuplicatedCode")
public final class HumanName extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - use reference
     * 4 or 8 byte - text reference
     * 4 or 8 byte - family reference
     * 4 or 8 byte - given reference
     * 4 or 8 byte - prefix reference
     * 4 or 8 byte - suffix reference
     * 4 or 8 byte - period reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 8 * MEM_SIZE_REFERENCE;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "HumanName");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk USE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.use : this;
        }
    };

    private static final ILookupThunk TEXT_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.text : this;
        }
    };

    private static final ILookupThunk FAMILY_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.family : this;
        }
    };

    private static final ILookupThunk GIVEN_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.given : this;
        }
    };

    private static final ILookupThunk PREFIX_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.prefix : this;
        }
    };

    private static final ILookupThunk SUFFIX_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.suffix : this;
        }
    };

    private static final ILookupThunk PERIOD_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof HumanName n ? n.period : this;
        }
    };

    private static final Keyword USE = RT.keyword(null, "use");
    private static final Keyword TEXT = RT.keyword(null, "text");
    private static final Keyword FAMILY = RT.keyword(null, "family");
    private static final Keyword GIVEN = RT.keyword(null, "given");
    private static final Keyword PREFIX = RT.keyword(null, "prefix");
    private static final Keyword SUFFIX = RT.keyword(null, "suffix");
    private static final Keyword PERIOD = RT.keyword(null, "period");

    private static final Keyword[] FIELDS = {ID, EXTENSION, USE, TEXT, FAMILY, GIVEN, PREFIX, SUFFIX, PERIOD};

    private static final FieldName FIELD_NAME_USE = FieldName.of("use");
    private static final FieldName FIELD_NAME_TEXT = FieldName.of("text");
    private static final FieldName FIELD_NAME_FAMILY = FieldName.of("family");
    private static final FieldName FIELD_NAME_GIVEN = FieldName.of("given");
    private static final FieldName FIELD_NAME_PREFIX = FieldName.of("prefix");
    private static final FieldName FIELD_NAME_SUFFIX = FieldName.of("suffix");
    private static final FieldName FIELD_NAME_PERIOD = FieldName.of("period");

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
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == USE) return USE_LOOKUP_THUNK;
        if (key == TEXT) return TEXT_LOOKUP_THUNK;
        if (key == FAMILY) return FAMILY_LOOKUP_THUNK;
        if (key == GIVEN) return GIVEN_LOOKUP_THUNK;
        if (key == PREFIX) return PREFIX_LOOKUP_THUNK;
        if (key == SUFFIX) return SUFFIX_LOOKUP_THUNK;
        if (key == PERIOD) return PERIOD_LOOKUP_THUNK;
        return super.getLookupThunk(key);
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
        return super.valAt(key, notFound);
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
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public HumanName assoc(Object key, Object val) {
        if (key == USE) return new HumanName(extensionData, (Code) val, text, family, given, prefix, suffix, period);
        if (key == TEXT) return new HumanName(extensionData, use, (String) val, family, given, prefix, suffix, period);
        if (key == FAMILY) return new HumanName(extensionData, use, text, (String) val, given, prefix, suffix, period);
        if (key == GIVEN)
            return new HumanName(extensionData, use, text, family, Lists.nullToEmpty(val), prefix, suffix, period);
        if (key == PREFIX)
            return new HumanName(extensionData, use, text, family, given, Lists.nullToEmpty(val), suffix, period);
        if (key == SUFFIX)
            return new HumanName(extensionData, use, text, family, given, prefix, Lists.nullToEmpty(val), period);
        if (key == PERIOD) return new HumanName(extensionData, use, text, family, given, prefix, suffix, (Period) val);
        if (key == EXTENSION)
            return new HumanName(extensionData.withExtension(val), use, text, family, given, prefix, suffix, period);
        if (key == ID)
            return new HumanName(extensionData.withId(val), use, text, family, given, prefix, suffix, period);
        return this;
    }

    @Override
    public HumanName withMeta(IPersistentMap meta) {
        return new HumanName(extensionData.withMeta(meta), use, text, family, given, prefix, suffix, period);
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
            serializeJsonPrimitiveList(given, generator, FIELD_NAME_GIVEN);
        }
        if (!prefix.isEmpty()) {
            serializeJsonPrimitiveList(prefix, generator, FIELD_NAME_PREFIX);
        }
        if (!suffix.isEmpty()) {
            serializeJsonPrimitiveList(suffix, generator, FIELD_NAME_SUFFIX);
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
