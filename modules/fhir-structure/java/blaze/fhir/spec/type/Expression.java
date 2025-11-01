package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Expression extends AbstractElement implements Complex, ExtensionValue {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - description reference
     * 4 byte - name reference
     * 4 byte - language reference
     * 4 byte - expression reference
     * 4 byte - reference reference
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 24;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Expression");

    private static final Keyword DESCRIPTION = Keyword.intern("description");
    private static final Keyword NAME = Keyword.intern("name");
    private static final Keyword LANGUAGE = Keyword.intern("language");
    private static final Keyword EXPRESSION = Keyword.intern("expression");
    private static final Keyword REFERENCE = Keyword.intern("reference");

    private static final Keyword[] FIELDS = {ID, EXTENSION, DESCRIPTION, NAME, LANGUAGE, EXPRESSION, REFERENCE};

    private static final FieldName FIELD_NAME_DESCRIPTION = FieldName.of("description");
    private static final FieldName FIELD_NAME_NAME = FieldName.of("name");
    private static final FieldName FIELD_NAME_LANGUAGE = FieldName.of("language");
    private static final FieldName FIELD_NAME_EXPRESSION = FieldName.of("expression");
    private static final FieldName FIELD_NAME_REFERENCE = FieldName.of("reference");


    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueExpression");

    private static final byte HASH_MARKER = 55;

    private static final Expression EMPTY = new Expression(ExtensionData.EMPTY, null, null, null, null, null);

    private final String description;
    private final Id name;
    private final Code language;
    private final String expression;
    private final Uri reference;

    private Expression(ExtensionData extensionData, String description, Id name, Code language, String expression,
                       Uri reference) {
        super(extensionData);
        this.description = description;
        this.name = name;
        this.language = language;
        this.expression = expression;
        this.reference = reference;
    }

    public static Expression create(IPersistentMap m) {
        return new Expression(ExtensionData.fromMap(m), (String) m.valAt(DESCRIPTION), (Id) m.valAt(NAME),
                (Code) m.valAt(LANGUAGE), (String) m.valAt(EXPRESSION), (Uri) m.valAt(REFERENCE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public String description() {
        return description;
    }

    public Id name() {
        return name;
    }

    public Code language() {
        return language;
    }

    public String expression() {
        return expression;
    }

    public Uri reference() {
        return reference;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE_KEY) return FHIR_TYPE;
        if (key == DESCRIPTION) return description;
        if (key == NAME) return name;
        if (key == LANGUAGE) return language;
        if (key == EXPRESSION) return expression;
        if (key == REFERENCE) return reference;
        return extensionData.valAt(key, notFound);
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, REFERENCE, reference);
        seq = appendElement(seq, EXPRESSION, expression);
        seq = appendElement(seq, LANGUAGE, language);
        seq = appendElement(seq, NAME, name);
        seq = appendElement(seq, DESCRIPTION, description);
        return extensionData.append(seq);
    }

    @Override
    public Expression empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Expression assoc(Object key, Object val) {
        if (key == ID)
            return new Expression(extensionData.withId((java.lang.String) val), description, name, language, expression, reference);
        if (key == EXTENSION)
            return new Expression(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), description, name, language, expression, reference);
        if (key == DESCRIPTION)
            return new Expression(extensionData, (String) val, name, language, expression, reference);
        if (key == NAME)
            return new Expression(extensionData, description, (Id) val, language, expression, reference);
        if (key == LANGUAGE)
            return new Expression(extensionData, description, name, (Code) val, expression, reference);
        if (key == EXPRESSION)
            return new Expression(extensionData, description, name, language, (String) val, reference);
        if (key == REFERENCE)
            return new Expression(extensionData, description, name, language, expression, (Uri) val);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Expression.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeAsJsonValue(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (description != null) {
            description.serializeAsJsonProperty(generator, FIELD_NAME_DESCRIPTION);
        }
        if (name != null) {
            name.serializeAsJsonProperty(generator, FIELD_NAME_NAME);
        }
        if (language != null) {
            language.serializeAsJsonProperty(generator, FIELD_NAME_LANGUAGE);
        }
        if (expression != null) {
            expression.serializeAsJsonProperty(generator, FIELD_NAME_EXPRESSION);
        }
        if (reference != null) {
            reference.serializeAsJsonProperty(generator, FIELD_NAME_REFERENCE);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (description != null) {
            sink.putByte((byte) 2);
            description.hashInto(sink);
        }
        if (name != null) {
            sink.putByte((byte) 3);
            name.hashInto(sink);
        }
        if (language != null) {
            sink.putByte((byte) 4);
            language.hashInto(sink);
        }
        if (expression != null) {
            sink.putByte((byte) 5);
            expression.hashInto(sink);
        }
        if (reference != null) {
            sink.putByte((byte) 6);
            reference.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return MEM_SIZE_OBJECT + extensionData.memSize() + Base.memSize(description) + Base.memSize(name) +
                Base.memSize(language) + Base.memSize(expression) + Base.memSize(reference);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Expression that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(description, that.description) &&
                Objects.equals(name, that.name) &&
                Objects.equals(language, that.language) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        int result = extensionData.hashCode();
        result = 31 * result + Objects.hashCode(description);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(language);
        result = 31 * result + Objects.hashCode(expression);
        result = 31 * result + Objects.hashCode(reference);
        return result;
    }

    @Override
    public java.lang.String toString() {
        return "Expression{" +
                extensionData +
                ", description=" + description +
                ", name=" + name +
                ", language=" + language +
                ", expression=" + expression +
                ", reference=" + reference +
                '}';
    }
}
