package blaze.fhir.spec.type;

import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

abstract class AbstractElement implements Element {

    protected final ExtensionData extensionData;

    protected AbstractElement(ExtensionData extensionData) {
        this.extensionData = requireNonNull(extensionData);
    }

    public final String id() {
        return extensionData.id;
    }

    public final List<Extension> extension() {
        return extensionData.extension;
    }

    @Override
    public Stream<PersistentVector> references() {
        return extensionData.references();
    }

    protected void serializeJsonBase(JsonGenerator generator) throws IOException {
        extensionData.serializeJson(generator);
    }

    @Override
    public void serializeJsonPrimitiveExtension(JsonGenerator generator) throws IOException {
        if (extensionData.isNotEmpty()) {
            generator.writeStartObject();
            serializeJsonBase(generator);
            generator.writeEndObject();
        } else {
            generator.writeNull();
        }
    }
}
