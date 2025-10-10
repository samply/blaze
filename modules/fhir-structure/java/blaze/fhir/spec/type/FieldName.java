package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;

import static java.util.Objects.requireNonNull;

public record FieldName(SerializableString normal, SerializableString extended) {

    private static final Interner<java.lang.String, FieldName> INTERNER = Interners.strongInterner(
            name -> new FieldName(new SerializedString(name), new SerializedString("_" + name))
    );

    public FieldName {
        requireNonNull(normal);
        requireNonNull(extended);
    }

    public static FieldName of(java.lang.String name) {
        return INTERNER.intern(requireNonNull(name));
    }
}
