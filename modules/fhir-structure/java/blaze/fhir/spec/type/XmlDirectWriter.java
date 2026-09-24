package blaze.fhir.spec.type;

import blaze.fhir.XmlUtil;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

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

    private static boolean canWriteIdentifier(Identifier value) {
        return value.id() == null && value.extension().isEmpty() &&
                canWritePrimitive(value.use()) &&
                (value.type() == null || canWriteCodeableConcept(value.type())) &&
                canWritePrimitive(value.system()) &&
                canWritePrimitive(value.value()) &&
                (value.period() == null || canWritePeriod(value.period())) &&
                value.assigner() == null;
    }

    private static boolean canWriteCodings(List<Coding> codings) {
        for (Coding coding : codings) {
            if (!canWriteCoding(coding)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canWritePrimitives(List<? extends Primitive> primitives) {
        for (Primitive primitive : primitives) {
            if (!canWritePrimitive(primitive)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canWriteMeta(Meta value) {
        return value.id() == null && value.extension().isEmpty() &&
                canWritePrimitive(value.versionId()) &&
                canWritePrimitive(value.lastUpdated()) &&
                canWritePrimitive(value.source()) &&
                canWritePrimitives(value.profile()) &&
                canWriteCodings(value.security()) &&
                canWriteCodings(value.tag());
    }

    private static void writePrimitiveField(Writer writer, java.lang.String tag, Primitive value) throws IOException {
        if (value == null) return;
        var stringValue = value.valueAsString();
        writeStringValueField(writer, tag, stringValue);
    }

    private static void writeStringValueField(Writer writer, java.lang.String tag, java.lang.String value) throws IOException {
        if (value == null) {
            writer.write("<");
            writer.write(tag);
            writer.write("/>");
        } else {
            writer.write("<");
            writer.write(tag);
            writer.write(" value=\"");
            XmlUtil.writeEscaped(writer, value);
            writer.write("\"/>");
        }
    }

    private static void writeStringField(Writer writer, java.lang.String tag, Uri value) throws IOException {
        if (value == null) return;
        writeStringValueField(writer, tag, value.value());
    }

    private static void writeStringField(Writer writer, java.lang.String tag, Id value) throws IOException {
        if (value == null) return;
        writeStringValueField(writer, tag, value.value());
    }

    private static void writeStringField(Writer writer, java.lang.String tag, Canonical value) throws IOException {
        if (value == null) return;
        writeStringValueField(writer, tag, value.value());
    }

    private static void writeStringField(Writer writer, java.lang.String tag, String value) throws IOException {
        if (value == null) return;
        writeStringValueField(writer, tag, value.value());
    }

    private static void writeStringField(Writer writer, java.lang.String tag, Code value) throws IOException {
        if (value == null) return;
        writeStringValueField(writer, tag, value.value());
    }

    private static void writeBooleanField(Writer writer, java.lang.String tag, Boolean value) throws IOException {
        if (value == null) return;
        var booleanValue = value.value();
        if (booleanValue == null) {
            writer.write("<");
            writer.write(tag);
            writer.write("/>");
        } else {
            writer.write("<");
            writer.write(tag);
            writer.write(booleanValue ? " value=\"true\"/>" : " value=\"false\"/>");
        }
    }

    private static void write2(Writer writer, int value) throws IOException {
        writer.write('0' + value / 10);
        writer.write('0' + value % 10);
    }

    private static void write4(Writer writer, int value) throws IOException {
        writer.write('0' + value / 1000);
        writer.write('0' + value / 100 % 10);
        writer.write('0' + value / 10 % 10);
        writer.write('0' + value % 10);
    }

    private static boolean canWriteYear(int year) {
        return 0 <= year && year <= 9999;
    }

    private static void writeLocalDateTime(Writer writer, LocalDateTime value) throws IOException {
        write4(writer, value.getYear());
        writer.write('-');
        write2(writer, value.getMonthValue());
        writer.write('-');
        write2(writer, value.getDayOfMonth());
        writer.write('T');
        write2(writer, value.getHour());
        writer.write(':');
        write2(writer, value.getMinute());
        writer.write(':');
        write2(writer, value.getSecond());
        writeFraction(writer, value.getNano());
    }

    private static void writeFraction(Writer writer, int nano) throws IOException {
        if (nano == 0) return;
        writer.write('.');
        int divisor = 100000000;
        while (nano % 10 == 0) {
            nano /= 10;
            divisor /= 10;
        }
        while (divisor > 0) {
            writer.write('0' + nano / divisor);
            nano %= divisor;
            divisor /= 10;
        }
    }

    private static void writeOffset(Writer writer, OffsetDateTime value) throws IOException {
        int totalSeconds = value.getOffset().getTotalSeconds();
        if (totalSeconds == 0) {
            writer.write('Z');
            return;
        }
        if (totalSeconds < 0) {
            writer.write('-');
            totalSeconds = -totalSeconds;
        } else {
            writer.write('+');
        }
        write2(writer, totalSeconds / 3600);
        writer.write(':');
        write2(writer, totalSeconds / 60 % 60);
        int seconds = totalSeconds % 60;
        if (seconds != 0) {
            writer.write(':');
            write2(writer, seconds);
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
                case LocalDateTime localDateTime -> {
                    if (canWriteYear(localDateTime.getYear())) {
                        writeLocalDateTime(writer, localDateTime);
                    } else {
                        blaze.fhir.spec.type.system.DateTime.LOCAL_DATE_TIME.formatTo(localDateTime, writer);
                    }
                }
                case OffsetDateTime offsetDateTime -> {
                    if (canWriteYear(offsetDateTime.getYear())) {
                        writeLocalDateTime(writer, offsetDateTime.toLocalDateTime());
                        writeOffset(writer, offsetDateTime);
                    } else {
                        blaze.fhir.spec.type.system.DateTime.DATE_TIME.formatTo(offsetDateTime, writer);
                    }
                }
                default -> writer.write(dateTime.toString());
            }
            writer.write("\"/>");
        }
    }

    private static void writeInstantField(Writer writer, java.lang.String tag, Instant value) throws IOException {
        if (value == null) return;
        var instant = value.value();
        if (instant == null) {
            writer.write("<");
            writer.write(tag);
            writer.write("/>");
        } else {
            writer.write("<");
            writer.write(tag);
            writer.write(" value=\"");
            if (canWriteYear(instant.getYear())) {
                writeLocalDateTime(writer, instant.toLocalDateTime());
                writeOffset(writer, instant);
            } else {
                blaze.fhir.spec.type.system.DateTime.DATE_TIME.formatTo(instant, writer);
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
            writeStringField(writer, "system", value.system());
            writeStringField(writer, "version", value.version());
            writeStringField(writer, "code", value.code());
            writeStringField(writer, "display", value.display());
            writeBooleanField(writer, "userSelected", value.userSelected());
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
            writeStringField(writer, "text", value.text());
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

    public static boolean writeIdentifier(Writer writer, java.lang.String tag, Identifier value) throws IOException {
        if (!canWriteIdentifier(value)) {
            return false;
        }
        writer.write("<");
        writer.write(tag);
        if (value.use() == null && value.type() == null && value.system() == null &&
                value.value() == null && value.period() == null) {
            writer.write("/>");
        } else {
            writer.write(">");
            writeStringField(writer, "use", value.use());
            if (value.type() != null) {
                writeCodeableConcept(writer, "type", value.type());
            }
            writeStringField(writer, "system", value.system());
            writeStringField(writer, "value", value.value());
            if (value.period() != null) {
                writePeriod(writer, "period", value.period());
            }
            writer.write("</");
            writer.write(tag);
            writer.write(">");
        }
        return true;
    }

    public static boolean writeMeta(Writer writer, java.lang.String tag, Meta value) throws IOException {
        if (!canWriteMeta(value)) {
            return false;
        }
        writer.write("<");
        writer.write(tag);
        if (value.versionId() == null && value.lastUpdated() == null && value.source() == null &&
                value.profile().isEmpty() && value.security().isEmpty() && value.tag().isEmpty()) {
            writer.write("/>");
        } else {
            writer.write(">");
            writeStringField(writer, "versionId", value.versionId());
            writeInstantField(writer, "lastUpdated", value.lastUpdated());
            writeStringField(writer, "source", value.source());
            for (Canonical profile : value.profile()) {
                writeStringField(writer, "profile", profile);
            }
            for (Coding security : value.security()) {
                writeCoding(writer, "security", security);
            }
            for (Coding tagCoding : value.tag()) {
                writeCoding(writer, "tag", tagCoding);
            }
            writer.write("</");
            writer.write(tag);
            writer.write(">");
        }
        return true;
    }
}
