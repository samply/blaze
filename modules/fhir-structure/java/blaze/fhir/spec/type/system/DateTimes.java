package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

public interface DateTimes {

    DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(Times.LOCAL_TIME)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .append(LOCAL_DATE_TIME)
            .appendOffsetId()
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT)
            .withChronology(IsoChronology.INSTANCE);

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(Temporal value, PrimitiveSink sink) {
        if (value instanceof JavaSystemType systemType) {
            systemType.hashInto(sink);
        }
        if (value instanceof LocalDateTime dateTime) {
            sink.putByte((byte) 6);
            sink.putInt(dateTime.getYear());
            sink.putInt(dateTime.getMonthValue());
            sink.putInt(dateTime.getDayOfMonth());
            sink.putInt(dateTime.getHour());
            sink.putInt(dateTime.getMinute());
            sink.putInt(dateTime.getSecond());
            sink.putInt(dateTime.getNano());
        }
        if (value instanceof OffsetDateTime dateTime) {
            hashInto(dateTime.toLocalDateTime(), sink);
            sink.putInt(dateTime.getOffset().getTotalSeconds());
        }
    }

    static int memSize(Temporal value) {
        if (value == null) return 0;
        return switch (value) {
            case JavaSystemType systemType -> systemType.memSize();
            case LocalDateTime x -> 48;
            case OffsetDateTime x -> 64;
            default -> 0;
        };
    }

    static String toString(Temporal value) {
        return switch (value) {
            case LocalDateTime dateTime -> LOCAL_DATE_TIME.format(dateTime);
            case OffsetDateTime dateTime -> DATE_TIME.format(dateTime);
            default -> value.toString();
        };
    }
}
