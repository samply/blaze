package blaze.fhir.spec.type.system;

import com.google.common.hash.PrimitiveSink;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public interface DateTimes {

    @SuppressWarnings("UnstableApiUsage")
    static void hashInto(Temporal temporal, PrimitiveSink sink) {
        if (temporal instanceof JavaSystemType systemType) {
            systemType.hashInto(sink);
        }
        if (temporal instanceof LocalDateTime dateTime) {
            sink.putByte((byte) 6);
            sink.putInt(dateTime.getYear());
            sink.putInt(dateTime.getMonthValue());
            sink.putInt(dateTime.getDayOfMonth());
            sink.putInt(dateTime.getHour());
            sink.putInt(dateTime.getMinute());
            sink.putInt(dateTime.getSecond());
            sink.putInt(dateTime.getNano());
        }
        if (temporal instanceof OffsetDateTime dateTime) {
            hashInto(dateTime.toLocalDateTime(), sink);
            sink.putInt(dateTime.getOffset().getTotalSeconds());
        }
    }

    static String toString(Temporal temporal) {
        if (temporal instanceof LocalDateTime dateTime) {
            return ISO_LOCAL_DATE_TIME.format(dateTime);
        }
        if (temporal instanceof OffsetDateTime dateTime) {
            return ISO_DATE_TIME.format(dateTime);
        }
        return temporal.toString();
    }
}
