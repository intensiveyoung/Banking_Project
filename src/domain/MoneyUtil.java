package domain;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtil {

    private MoneyUtil() {}

    public static String format(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US)
                .format(amount);
    }
}
