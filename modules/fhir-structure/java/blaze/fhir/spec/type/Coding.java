package blaze.fhir.spec.type;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.util.Objects;

import static blaze.fhir.spec.type.Base.appendElement;

public final class Coding extends Element {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "Coding");
    private static final Keyword SYSTEM = Keyword.intern("system");
    private static final Keyword VERSION = Keyword.intern("version");
    private static final Keyword CODE = Keyword.intern("code");
    private static final Keyword DISPLAY = Keyword.intern("display");
    private static final Keyword USER_SELECTED = Keyword.intern("userSelected");

    private static final SerializedString FIELD_NAME_SYSTEM = new SerializedString("system");
    private static final SerializedString FIELD_NAME_VERSION = new SerializedString("version");
    private static final SerializedString FIELD_NAME_CODE = new SerializedString("code");
    private static final SerializedString FIELD_NAME_DISPLAY = new SerializedString("display");
    private static final SerializedString FIELD_NAME_USER_SELECTED = new SerializedString("userSelected");

    private static final byte HASH_MARKER = 38;

    private final Uri system;
    private final String version;
    private final Code code;
    private final String display;
    private final Boolean userSelected;

    public Coding(java.lang.String id, PersistentVector extension, Uri system, String version, Code code, String display, Boolean userSelected) {
        super(id, extension);
        this.system = system;
        this.version = version;
        this.code = code;
        this.display = display;
        this.userSelected = userSelected;
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public void serializeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        serializeJsonBase(generator);
        if (system != null && system.value() != null) {
            generator.writeFieldName(FIELD_NAME_SYSTEM);
            system.serializeJson(generator);
        }
        if (version != null && version.value() != null) {
            generator.writeFieldName(FIELD_NAME_VERSION);
            version.serializeJson(generator);
        }
        if (code != null && code.value() != null) {
            generator.writeFieldName(FIELD_NAME_CODE);
            code.serializeJson(generator);
        }
        if (display != null && display.value() != null) {
            generator.writeFieldName(FIELD_NAME_DISPLAY);
            display.serializeJson(generator);
        }
        if (userSelected != null && userSelected.value() != null) {
            generator.writeFieldName(FIELD_NAME_USER_SELECTED);
            userSelected.serializeJson(generator);
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
                Objects.equals(extension, e.extension) &&
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
                ", system='" + system + '\'' +
                ", version='" + version + '\'' +
                ", code='" + code + '\'' +
                ", display='" + display + '\'' +
                ", userSelected=" + userSelected +
                '}';
    }
}
