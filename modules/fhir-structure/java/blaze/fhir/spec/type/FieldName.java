package blaze.fhir.spec.type;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;

import static java.util.Objects.requireNonNull;

public record FieldName(SerializableString normal, SerializableString extended) {

    public FieldName {
        requireNonNull(normal);
        requireNonNull(extended);
    }

    public static FieldName of(java.lang.String name) {
        return new FieldName(new SerializedString(name), new SerializedString("_" + name));
    }
}
