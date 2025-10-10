package blaze.fhir.spec.type;

import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;

import java.io.IOException;
import java.util.List;

import static blaze.fhir.spec.type.Complex.serializeJsonComplexList;
import static java.util.Objects.requireNonNull;

abstract class AbstractBackboneElement extends AbstractElement {

    protected final static Keyword MODIFIER_EXTENSION = RT.keyword(null, "modifierExtension");
    private static final SerializedString FIELD_NAME_MODIFIER_EXTENSION = new SerializedString("modifierExtension");

    protected final List<Extension> modifierExtension;

    protected AbstractBackboneElement(ExtensionData extensionData, List<Extension> modifierExtension) {
        super(extensionData);
        this.modifierExtension = requireNonNull(modifierExtension);
    }

    public final List<Extension> modifierExtension() {
        return modifierExtension;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == MODIFIER_EXTENSION) return modifierExtension;
        return super.valAt(key, notFound);
    }

    protected void serializeJsonBase(JsonGenerator generator) throws IOException {
        super.serializeJsonBase(generator);
        if (!modifierExtension.isEmpty()) {
            serializeJsonComplexList(modifierExtension, generator, FIELD_NAME_MODIFIER_EXTENSION);
        }
    }
}
