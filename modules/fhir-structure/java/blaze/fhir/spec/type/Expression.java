package blaze.fhir.spec.type;

import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Expression extends Element implements Complex, ExtensionValue {

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

    private static final byte HASH_MARKER = 52;

    private final String description;
    private final Id name;
    private final Code language;
    private final String expression;
    private final Uri reference;


    public Expression(java.lang.String id, PersistentVector extension, String description, Id name, Code language, String expression, Uri reference) {
        super(id, extension);
        this.description = description;
        this.name = name;
        this.language = language;
        this.expression = expression;
        this.reference = reference;
    }

    public static Expression create(IPersistentMap m) {
        return new Expression((java.lang.String) m.valAt(ID), (PersistentVector) m.valAt(EXTENSION),
                (String) m.valAt(DESCRIPTION), (Id) m.valAt(NAME), (Code) m.valAt(LANGUAGE),
                (String) m.valAt(EXPRESSION), (Uri) m.valAt(REFERENCE));
    }

    public static IPersistentVector getBasis() {
        return RT.vector(Symbol.intern(null, "id"), Symbol.intern(null, "extension"), Symbol.intern(null, "description"),
                Symbol.intern(null, "name"), Symbol.intern(null, "language"), Symbol.intern(null, "expression"),
                Symbol.intern(null, "reference"));
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
        if (key == DESCRIPTION) return description;
        if (key == NAME) return name;
        if (key == LANGUAGE) return language;
        if (key == EXPRESSION) return expression;
        if (key == REFERENCE) return reference;
        if (key == EXTENSION) return extension;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public IPersistentCollection empty() {
        return new Expression(null, null, null, null, null, null, null);
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    public Expression assoc(Object key, Object val) {
        if (key == ID)
            return new Expression((java.lang.String) val, extension, description, name, language, expression, reference);
        if (key == EXTENSION)
            return new Expression(id, (PersistentVector) val, description, name, language, expression, reference);
        if (key == DESCRIPTION)
            return new Expression(id, extension, (String) val, name, language, expression, reference);
        if (key == NAME)
            return new Expression(id, extension, description, (Id) val, language, expression, reference);
        if (key == LANGUAGE)
            return new Expression(id, extension, description, name, (Code) val, expression, reference);
        if (key == EXPRESSION)
            return new Expression(id, extension, description, name, language, (String) val, reference);
        if (key == REFERENCE)
            return new Expression(id, extension, description, name, language, expression, (Uri) val);
        throw new UnsupportedOperationException("The key `''' + key + '''` isn't supported on FHIR.Expression.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, REFERENCE, reference);
        seq = appendElement(seq, EXPRESSION, expression);
        seq = appendElement(seq, LANGUAGE, language);
        seq = appendElement(seq, NAME, name);
        seq = appendElement(seq, DESCRIPTION, description);
        return appendBase(seq);
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
        hashIntoBase(sink);
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
            ;
        }
        if (reference != null) {
            sink.putByte((byte) 6);
            reference.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expression that = (Expression) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(description, that.description) &&
                Objects.equals(name, that.name) &&
                Objects.equals(language, that.language) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, description, name, language, expression, reference);
    }

    @Override
    public java.lang.String toString() {
        return "Expression{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", description=" + description +
                ", name=" + name +
                ", language=" + language +
                ", expression=" + expression +
                ", reference=" + reference +
                '}';
    }
}
