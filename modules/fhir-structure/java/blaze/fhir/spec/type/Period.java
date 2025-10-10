package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class Period extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - start reference
     * 4 or 8 byte - end reference
     * 1 byte - interned boolean
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "Period");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Period ? FHIR_TYPE : this;
        }
    };

    private static final ILookupThunk START_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Period p ? p.start : this;
        }
    };

    private static final ILookupThunk END_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Period p ? p.end : this;
        }
    };

    private static final Keyword START = RT.keyword(null, "start");
    private static final Keyword END = RT.keyword(null, "end");

    private static final Keyword[] FIELDS = {ID, EXTENSION, START, END};

    private static final FieldName FIELD_NAME_START = FieldName.of("start");
    private static final FieldName FIELD_NAME_END = FieldName.of("end");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valuePeriod");

    private static final byte HASH_MARKER = 41;

    private static final Interner<ExtensionData, Period> INTERNER = Interners.weakInterner(k -> new Period(k, null, null, true));
    private static final Period EMPTY = new Period(ExtensionData.EMPTY, null, null, true);

    private final DateTime start;
    private final DateTime end;
    private final boolean interned;

    private Period(ExtensionData extensionData, DateTime start, DateTime end, boolean interned) {
        super(extensionData);
        this.start = start;
        this.end = end;
        this.interned = interned;
    }

    private static Period maybeIntern(ExtensionData extensionData, DateTime start, DateTime end) {
        return extensionData.isInterned() && start == null && end == null
                ? INTERNER.intern(extensionData)
                : new Period(extensionData, start, end, false);
    }

    public static Period create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (DateTime) m.valAt(START), (DateTime) m.valAt(END));
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public DateTime start() {
        return start;
    }

    public DateTime end() {
        return end;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == START) return START_LOOKUP_THUNK;
        if (key == END) return END_LOOKUP_THUNK;
        return super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == START) return start;
        if (key == END) return end;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, END, end);
        seq = appendElement(seq, START, start);
        return extensionData.append(seq);
    }

    @Override
    public Period empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Period assoc(Object key, Object val) {
        if (key == START) return maybeIntern(extensionData, (DateTime) val, end);
        if (key == END) return maybeIntern(extensionData, start, (DateTime) val);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), start, end);
        if (key == ID) return maybeIntern(extensionData.withId(val), start, end);
        return this;
    }

    @Override
    public Period withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), start, end);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (start != null) {
            start.serializeAsJsonProperty(generator, FIELD_NAME_START);
        }
        if (end != null) {
            end.serializeAsJsonProperty(generator, FIELD_NAME_END);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (start != null) {
            sink.putByte((byte) 2);
            start.hashInto(sink);
        }
        if (end != null) {
            sink.putByte((byte) 3);
            end.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(start) + Base.memSize(end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Period that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(start, that.start) &&
                Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(start);
        result = 31 * result + Objects.hashCode(end);
        return result;
    }

    @Override
    public String toString() {
        return "Period{" + extensionData + ", start=" + start + ", end=" + end + '}';
    }
}
