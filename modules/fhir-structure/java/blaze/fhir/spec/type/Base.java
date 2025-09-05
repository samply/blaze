package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

public interface Base extends Seqable, ILookup {

    Keyword ID = Keyword.intern("id");
    Keyword EXTENSION = Keyword.intern("extension");
    Keyword VALUE = Keyword.intern("value");

    SerializedString FIELD_NAME_ID = new SerializedString("id");
    SerializedString FIELD_NAME_EXTENSION = new SerializedString("extension");

    Keyword fhirType();

    void serializeJson(JsonGenerator generator) throws IOException;

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink);

    @Override
    default Object valAt(Object key) {
        return valAt(key, null);
    }

    static ISeq appendElement(ISeq base, Keyword name, Object value) {
        return value == null ? base : base.cons(MapEntry.create(name, value));
    }
}
