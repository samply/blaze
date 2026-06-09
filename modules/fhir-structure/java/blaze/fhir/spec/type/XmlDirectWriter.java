package blaze.fhir.spec.type;

import blaze.fhir.XmlUtil;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public final class XmlDirectWriter {

    private XmlDirectWriter() {
    }

    private static boolean canWritePrimitive(Primitive value) {
        return value == null || value.id() == null && value.extension().isEmpty();
    }

    private static boolean canWriteCoding(Coding value) {
        return value.id() == null && value.extension().isEmpty() &&
                canWritePrimitive(value.system()) &&
                canWritePrimitive(value.version()) &&
                canWritePrimitive(value.code()) &&
                canWritePrimitive(value.display()) &&
                canWritePrimitive(value.userSelected());
    }

    private static boolean canWriteCodeableConcept(CodeableConcept value) {
        if (value.id() != null || !value.extension().isEmpty() || !canWritePrimitive(value.text())) {
            return false;
        }
        for (Coding coding : value.coding()) {
            if (!canWriteCoding(coding)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canWritePeriod(Period value) {
        return value.id() == null && value.extension().isEmpty() &&
                canWritePrimitive(value.start()) &&
                canWritePrimitive(value.end());
    }

    private static void writePrimitiveField(Writer writer, java.lang.String tag, Primitive value) throws IOException {
        if (value == null) return;
        var stringValue = value.valueAsString();
        if (stringValue == null) {
            writer.write("<");
            writer.write(tag);
            writer.write("/>");
        } else {
            writer.write("<");
            writer.write(tag);
            writer.write(" value=\"");
            XmlUtil.writeEscaped(writer, stringValue);
            writer.write("\"/>");
        }
    }

    private static void writeDateTimeField(Writer writer, java.lang.String tag, DateTime value) throws IOException {
        if (value == null) return;
        var dateTime = value.value();
        if (dateTime == null) {
            writer.write("<");
            writer.write(tag);
            writer.write("/>");
        } else {
            writer.write("<");
            writer.write(tag);
            writer.write(" value=\"");
            switch (dateTime) {
                case LocalDateTime localDateTime ->
                        blaze.fhir.spec.type.system.DateTime.LOCAL_DATE_TIME.formatTo(localDateTime, writer);
                case OffsetDateTime offsetDateTime ->
                        blaze.fhir.spec.type.system.DateTime.DATE_TIME.formatTo(offsetDateTime, writer);
                default -> writer.write(dateTime.toString());
            }
            writer.write("\"/>");
        }
    }

    public static boolean writeCoding(Writer writer, java.lang.String tag, Coding value) throws IOException {
        if (!canWriteCoding(value)) {
            return false;
        }
        writer.write("<");
        writer.write(tag);
        if (value.system() == null && value.version() == null && value.code() == null &&
                value.display() == null && value.userSelected() == null) {
            writer.write("/>");
        } else {
            writer.write(">");
            writePrimitiveField(writer, "system", value.system());
            writePrimitiveField(writer, "version", value.version());
            writePrimitiveField(writer, "code", value.code());
            writePrimitiveField(writer, "display", value.display());
            writePrimitiveField(writer, "userSelected", value.userSelected());
            writer.write("</");
            writer.write(tag);
            writer.write(">");
        }
        return true;
    }

    public static boolean writeCodeableConcept(Writer writer, java.lang.String tag, CodeableConcept value) throws IOException {
        if (!canWriteCodeableConcept(value)) {
            return false;
        }
        writer.write("<");
        writer.write(tag);
        if (value.coding().isEmpty() && value.text() == null) {
            writer.write("/>");
        } else {
            writer.write(">");
            for (Coding coding : value.coding()) {
                writeCoding(writer, "coding", coding);
            }
            writePrimitiveField(writer, "text", value.text());
            writer.write("</");
            writer.write(tag);
            writer.write(">");
        }
        return true;
    }

    public static boolean writePeriod(Writer writer, java.lang.String tag, Period value) throws IOException {
        if (!canWritePeriod(value)) {
            return false;
        }
        writer.write("<");
        writer.write(tag);
        if (value.start() == null && value.end() == null) {
            writer.write("/>");
        } else {
            writer.write(">");
            writeDateTimeField(writer, "start", value.start());
            writeDateTimeField(writer, "end", value.end());
            writer.write("</");
            writer.write(tag);
            writer.write(">");
        }
        return true;
    }
}
