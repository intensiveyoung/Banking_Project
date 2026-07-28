package domain;

import java.util.Arrays;

public enum SecurityQuestion {
    FIRST_PET("What is your first pet's name?"),
    BIRTH_CITY("What city were you born in?"),
    FIRST_SCHOOL("What was your first school?"),
    FAVORITE_BOOK("What is your favorite book?"),
    CHILDHOOD_NICKNAME("What was your childhood nickname?");

    private final String text;

    SecurityQuestion(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static SecurityQuestion fromSelection(int selection) {
        SecurityQuestion[] questions = values();
        if (selection < 1 || selection > questions.length) {
            throw new IllegalArgumentException("Security question selection must be between 1 and 5.");
        }
        return questions[selection - 1];
    }

    public static boolean isSupported(String questionText) {
        return questionText != null && Arrays.stream(values())
                .anyMatch(question -> question.text.equals(questionText.trim()));
    }
}
