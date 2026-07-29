package domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

public final class DateUtil {
    private static final DateTimeFormatter FLEXIBLE_DATE_FORMATTER =
            new DateTimeFormatterBuilder()
                    .parseStrict()
                    .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('/')
                    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('/')
                    .optionalStart()
                    .appendValue(ChronoField.YEAR, 4)
                    .optionalEnd()
                    .optionalStart()
                    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                    .optionalEnd()
                    .toFormatter()
                    .withResolverStyle(ResolverStyle.STRICT);

    private DateUtil() {}

    public static LocalDate parseFlexibleDate(String input) {
        return LocalDate.parse(input.trim(), FLEXIBLE_DATE_FORMATTER);
    }
}
