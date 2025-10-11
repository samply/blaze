package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class BundleEntrySearch extends Element implements Complex {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir.Bundle.entry", "search");

    private static final Keyword MODE = Keyword.intern("mode");
    private static final Keyword SCORE = Keyword.intern("score");

    private static final Keyword[] FIELDS = {ID, EXTENSION, MODE, SCORE};

    private static final FieldName FIELD_NAME_MODE = FieldName.of("mode");
    private static final FieldName FIELD_NAME_SCORE = FieldName.of("score");

    private static final byte HASH_MARKER = 45;

    private final Code mode;
    private final Decimal score;

    public BundleEntrySearch(java.lang.String id, List<Extension> extension, Code mode, Decimal score) {
        super(id, extension);
        this.mode = mode;
        this.score = score;
    }

    public static BundleEntrySearch create(IPersistentMap m) {
        return new BundleEntrySearch((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION), (Code) m.valAt(MODE),
                (Decimal) m.valAt(SCORE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return isBaseInterned() && Base.isInterned(mode) && Base.isInterned(score);
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
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, SCORE, score);
        seq = appendElement(seq, MODE, mode);
        return appendBase(seq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IPersistentCollection empty() {
        return new BundleEntrySearch(null, PersistentVector.EMPTY, null, null);
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BundleEntrySearch assoc(Object key, Object val) {
        if (key == ID) return new BundleEntrySearch((java.lang.String) val, extension, mode, score);
        if (key == EXTENSION) return new BundleEntrySearch(id, (List<Extension>) val, mode, score);
        if (key == MODE) return new BundleEntrySearch(id, extension, (Code) val, score);
        if (key == SCORE) return new BundleEntrySearch(id, extension, mode, (Decimal) val);
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
        hashIntoBase(sink);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BundleEntrySearch that = (BundleEntrySearch) o;
        return Objects.equals(id, that.id) &&
                extension.equals(that.extension) &&
                Objects.equals(mode, that.mode) &&
                Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, mode, score);
    }

    @Override
    public java.lang.String toString() {
        return "BundleEntrySearch{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", mode=" + mode +
                ", score=" + score +
                '}';
    }
}
