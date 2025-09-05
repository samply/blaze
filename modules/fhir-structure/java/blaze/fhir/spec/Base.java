package blaze.fhir.spec;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;

public interface Base extends ILookup {

    Keyword ID = Keyword.intern("id");
    Keyword EXTENSION = Keyword.intern("extension");
    Keyword VALUE = Keyword.intern("value");

    SerializedString FIELD_NAME_ID = new SerializedString("id");
    SerializedString FIELD_NAME_EXTENSION = new SerializedString("extension");

    Keyword fhirType();

    void serializeJson(JsonGenerator generator) throws IOException;

    @SuppressWarnings("UnstableApiUsage")
    void hashInto(PrimitiveSink sink);
}
