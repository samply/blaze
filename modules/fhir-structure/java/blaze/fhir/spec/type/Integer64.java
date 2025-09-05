package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Longs;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

public final class Integer64 extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "integer64");

    private static final byte HASH_MARKER = 2;

    private final Long value;

    public Integer64(java.lang.String id, PersistentVector extension, Long value) {
        super(id, extension);
        this.value = value;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public Long value() {
        return value;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == VALUE) return value;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public void serializeJson(JsonGenerator generator) throws IOException {
        if (value != null) {
            generator.writeNumber(value);
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (value != null) {
            sink.putByte((byte) 2);
            Longs.hashInto(value, sink);
        }
    }
}
