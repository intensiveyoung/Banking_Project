package domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilTest {

    @Test
    @DisplayName("Should parse single-digit day and month with a four-digit year")
    void testSingleDigitDateWithFourDigitYear() {
        assertEquals(LocalDate.of(2026, 6, 6), DateUtil.parseFlexibleDate("6/6/2026"));
    }

    @Test
    @DisplayName("Should parse padded day and month with a four-digit year")
    void testPaddedDateWithFourDigitYear() {
        assertEquals(LocalDate.of(2026, 6, 6), DateUtil.parseFlexibleDate("06/06/2026"));
    }

    @Test
    @DisplayName("Should parse single-digit day and month with a two-digit year")
    void testSingleDigitDateWithTwoDigitYear() {
        assertEquals(LocalDate.of(2026, 6, 6), DateUtil.parseFlexibleDate("6/6/26"));
    }

    @Test
    @DisplayName("Should parse padded day and month with a two-digit year")
    void testPaddedDateWithTwoDigitYear() {
        assertEquals(LocalDate.of(2026, 6, 6), DateUtil.parseFlexibleDate("06/06/26"));
    }
}
