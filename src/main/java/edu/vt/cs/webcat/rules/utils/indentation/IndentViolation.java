package edu.vt.cs.webcat.rules.utils.indentation;

public final class IndentViolation {

    private final int lineNumber;
    private final String message;
    private final String expectedSpaces;
    private final Integer actualSpaces;

    public IndentViolation(int lineNumber, String message) {
        this(lineNumber, message, null, null);
    }

    public IndentViolation(int lineNumber, String message,
                           String expectedSpaces, int actualSpaces) {
        this(lineNumber, message, expectedSpaces, Integer.valueOf(actualSpaces));
    }

    private IndentViolation(int lineNumber, String message,
                            String expectedSpaces, Integer actualSpaces) {
        this.lineNumber = lineNumber;
        this.message = message;
        this.expectedSpaces = expectedSpaces;
        this.actualSpaces = actualSpaces;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMessage() {
        return message;
    }

    public String getExpectedSpaces() {
        return expectedSpaces;
    }

    public Integer getActualSpaces() {
        return actualSpaces;
    }

    @Override
    public String toString() {
        return String.format("IndentViolation{line=%d, msg='%s'}", lineNumber, message);
    }
}

