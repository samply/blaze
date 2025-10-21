package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class BundleEntrySearch extends AbstractElement implements Complex {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - mode reference
     * 4 byte - score reference
     * 4 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir.Bundle.entry", "search");

    private static final Keyword MODE = Keyword.intern("mode");
    private static final Keyword SCORE = Keyword.intern("score");

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

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Code mode() {
        return mode;
    }

    public Decimal score() {
        return score;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == MODE) return mode;
        if (key == SCORE) return score;
        return extensionData.valAt(key, notFound);
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
    @SuppressWarnings("unchecked")
    public BundleEntrySearch assoc(Object key, Object val) {
        if (key == ID) return new BundleEntrySearch(extensionData.withId((java.lang.String) val), mode, score);
        if (key == EXTENSION)
            return new BundleEntrySearch(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), mode, score);
        if (key == MODE) return new BundleEntrySearch(extensionData, (Code) val, score);
        if (key == SCORE) return new BundleEntrySearch(extensionData, mode, (Decimal) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.BundleEntrySearch.");
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
