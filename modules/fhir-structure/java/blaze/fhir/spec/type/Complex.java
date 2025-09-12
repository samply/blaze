package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

public interface Complex extends Base {

    void serializeAsJsonValue(JsonGenerator generator) throws IOException;
}
