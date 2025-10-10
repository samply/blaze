package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;
import static java.util.Objects.requireNonNull;

public final class Coding extends Element implements Complex, ExtensionValue {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Coding");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VERSION = Keyword.intern("version");
    private static final Keyword CODE = Keyword.intern("code");
    private static final Keyword DISPLAY = Keyword.intern("display");
    private static final Keyword USER_SELECTED = Keyword.intern("userSelected");

    private static final Keyword[] FIELDS = {ID, EXTENSION, SYSTEM, VERSION, CODE, DISPLAY, USER_SELECTED};

    private static final FieldName FIELD_NAME_SYSTEM = FieldName.of("system");
    private static final FieldName FIELD_NAME_VERSION = FieldName.of("version");
    private static final FieldName FIELD_NAME_CODE = FieldName.of("code");
    private static final FieldName FIELD_NAME_DISPLAY = FieldName.of("display");
    private static final FieldName FIELD_NAME_USER_SELECTED = FieldName.of("userSelected");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCoding");

    private static final byte HASH_MARKER = 38;

    private static final Interner<InternerKey, Coding> INTERNER = Interners.weakInterner(
            k -> new Coding(null, k.extension, k.system, k.version, k.code, k.display, k.userSelected, true)
    );
    public static final Coding EMPTY = new Coding(null, null, null, null, null, null, null, true);

    private final Uri system;
    private final String version;
    private final Code code;
    private final String display;
    private final Boolean userSelected;
    private final boolean interned;

    public Coding(java.lang.String id, List<Extension> extension, Uri system, String version, Code code, String display,
                  Boolean userSelected, boolean interned) {
        super(id, extension);
        this.system = system;
        this.version = version;
        this.code = code;
        this.display = display;
        this.userSelected = userSelected;
        this.interned = interned;
    }

    private static Coding intern(List<Extension> extension, Uri system, String version, Code code, String display,
                                 Boolean userSelected) {
        return INTERNER.intern(new InternerKey(extension, system, version, code, display, userSelected));
    }

    private static Coding maybeIntern(java.lang.String id, List<Extension> extension, Uri system, String version,
                                      Code code, String display, Boolean userSelected) {
        return id == null && Base.areAllInterned(extension) && Base.isInterned(system) && Base.isInterned(version) &&
                Base.isInterned(code) && Base.isInterned(display) && Base.isInterned(userSelected)
                ? intern(extension, system, version, code, display, userSelected)
                : new Coding(id, extension, system, version, code, display, userSelected, false);
    }

    public static Coding create(IPersistentMap m) {
        return maybeIntern((java.lang.String) m.valAt(ID), Base.listFrom(m, EXTENSION),
                (Uri) m.valAt(SYSTEM), (String) m.valAt(VERSION), (Code) m.valAt(CODE),
                (String) m.valAt(DISPLAY), (Boolean) m.valAt(USER_SELECTED));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return interned;
    }

    public Uri system() {
        return system;
    }

    public String version() {
        return version;
    }

    public Code code() {
        return code;
    }

    public String display() {
        return display;
    }

    public Boolean userSelected() {
        return userSelected;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == CODE) return code;
        if (key == SYSTEM) return system;
        if (key == VERSION) return version;
        if (key == DISPLAY) return display;
        if (key == EXTENSION) return extension;
        if (key == USER_SELECTED) return userSelected;
        if (key == ID) return id;
        return notFound;
    }

    @Override
    public Coding empty() {
        return EMPTY;
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return new BaseIterator(this, FIELDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Coding assoc(Object key, Object val) {
        if (key == CODE) return maybeIntern(id, extension, system, version, (Code) val, display, userSelected);
        if (key == SYSTEM) return maybeIntern(id, extension, (Uri) val, version, code, display, userSelected);
        if (key == VERSION) return maybeIntern(id, extension, system, (String) val, code, display, userSelected);
        if (key == DISPLAY) return maybeIntern(id, extension, system, version, code, (String) val, userSelected);
        if (key == EXTENSION) return maybeIntern(id, (List<Extension>) val, system, version, code, display, userSelected);
        if (key == USER_SELECTED) return maybeIntern(id, extension, system, version, code, display, (Boolean) val);
        if (key == ID) return maybeIntern((java.lang.String) val, extension, system, version, code, display, userSelected);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Coding.");
    }

    @Override
    public ISeq seq() {
        ISeq seq = PersistentList.EMPTY;
        seq = appendElement(seq, USER_SELECTED, userSelected);
        seq = appendElement(seq, DISPLAY, display);
        seq = appendElement(seq, CODE, code);
        seq = appendElement(seq, VERSION, version);
        seq = appendElement(seq, SYSTEM, system);
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
        if (system != null) {
            system.serializeAsJsonProperty(generator, FIELD_NAME_SYSTEM);
        }
        if (version != null) {
            version.serializeAsJsonProperty(generator, FIELD_NAME_VERSION);
        }
        if (code != null) {
            code.serializeAsJsonProperty(generator, FIELD_NAME_CODE);
        }
        if (display != null) {
            display.serializeAsJsonProperty(generator, FIELD_NAME_DISPLAY);
        }
        if (userSelected != null) {
            userSelected.serializeAsJsonProperty(generator, FIELD_NAME_USER_SELECTED);
        }
        generator.writeEndObject();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        hashIntoBase(sink);
        if (system != null) {
            sink.putByte((byte) 2);
            system.hashInto(sink);
        }
        if (version != null) {
            sink.putByte((byte) 3);
            version.hashInto(sink);
        }
        if (code != null) {
            sink.putByte((byte) 4);
            code.hashInto(sink);
        }
        if (display != null) {
            sink.putByte((byte) 5);
            display.hashInto(sink);
        }
        if (userSelected != null) {
            sink.putByte((byte) 6);
            userSelected.hashInto(sink);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Coding e = (Coding) o;
        return Objects.equals(id, e.id) &&
                extension.equals(e.extension) &&
                Objects.equals(system, e.system) &&
                Objects.equals(version, e.version) &&
                Objects.equals(code, e.code) &&
                Objects.equals(display, e.display) &&
                Objects.equals(userSelected, e.userSelected);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, extension, system, version, code, display, userSelected);
    }

    @Override
    public java.lang.String toString() {
        return "Coding{" +
                "id=" + (id == null ? null : '\'' + id + '\'') +
                ", extension=" + extension +
                ", system=" + system +
                ", version=" + version +
                ", code=" + code +
                ", display=" + display +
                ", userSelected=" + userSelected +
                '}';
    }

    private record InternerKey(List<Extension> extension, Uri system, String version, Code code, String display,
                               Boolean userSelected) {
        private InternerKey {
            requireNonNull(extension);
        }
    }
}
