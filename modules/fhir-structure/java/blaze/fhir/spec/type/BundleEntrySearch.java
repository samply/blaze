package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

@SuppressWarnings("DuplicatedCode")
public final class BundleEntrySearch extends AbstractElement implements Complex {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 or 8 byte - mode reference
     * 4 or 8 byte - score reference
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + 3 * MEM_SIZE_REFERENCE + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir.Bundle.entry", "search");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof BundleEntrySearch ? FHIR_TYPE : this;
        }
    };

    private static final Keyword MODE = RT.keyword(null, "mode");
    private static final Keyword SCORE = RT.keyword(null, "score");

    private static final Keyword[] FIELDS = {ID, EXTENSION, MODE, SCORE};

    private static final FieldName FIELD_NAME_MODE = FieldName.of("mode");
    private static final FieldName FIELD_NAME_SCORE = FieldName.of("score");

    private static final byte HASH_MARKER = 45;

    private static final BundleEntrySearch EMPTY = new BundleEntrySearch(ExtensionData.EMPTY, null, null);

    private final Code mode;
    private final Decimal score;

    private BundleEntrySearch(ExtensionData extensionData, Code mode, Decimal score) {
        super(extensionData);
        this.mode = mode;
        this.score = score;
    }

    public static BundleEntrySearch create(IPersistentMap m) {
        return new BundleEntrySearch(ExtensionData.fromMap(m), (Code) m.valAt(MODE), (Decimal) m.valAt(SCORE));
    }

    public Code mode() {
        return mode;
    }

    public Decimal score() {
        return score;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == MODE) return mode;
        if (key == SCORE) return score;
        return super.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, SCORE, score);
        seq = appendElement(seq, MODE, mode);
        return extensionData.append(seq);
    }

    @Override
    public BundleEntrySearch empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public BundleEntrySearch assoc(Object key, Object val) {
        if (key == MODE) return new BundleEntrySearch(extensionData, (Code) val, score);
        if (key == SCORE) return new BundleEntrySearch(extensionData, mode, (Decimal) val);
        if (key == EXTENSION) return new BundleEntrySearch(extensionData.withExtension(val), mode, score);
        if (key == ID) return new BundleEntrySearch(extensionData.withId(val), mode, score);
        return this;
    }

    @Override
    public BundleEntrySearch withMeta(IPersistentMap meta) {
        return new BundleEntrySearch(extensionData.withMeta(meta), mode, score);
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (mode != null) {
            mode.serializeAsJsonProperty(generator, FIELD_NAME_MODE);
        }
        if (score != null) {
            score.serializeAsJsonProperty(generator, FIELD_NAME_SCORE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (mode != null) {
            sink.putByte((byte) 2);
            mode.hashInto(sink);
        }
        if (score != null) {
            sink.putByte((byte) 3);
            score.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(mode) + Base.memSize(score);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BundleEntrySearch that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(mode, that.mode) &&
                Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(mode);
        result = 31 * result + Objects.hashCode(score);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "BundleEntrySearch{" +
                extensionData +
                ", mode=" + mode +
                ", score=" + score +
                '}';
    }
}
