package util;

public final class ConsoleColor {
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";

    private ConsoleColor() {
    }

    public static String red(String msg) {
        return RED + msg + RESET;
    }

    public static String green(String msg) {
        return GREEN + msg + RESET;
    }

    public static String cyan(String msg) {
        return CYAN + msg + RESET;
    }

    public static String yellow(String msg) {
        return YELLOW + msg + RESET;
    }
}
