package edu.vt.cs.webcat.rules.indentation;

public class IndentViolation {

    private final int lineNumber;
    private final String message;

    public IndentViolation(int lineNumber, String message) {
        this.lineNumber = lineNumber;
        this.message = message;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("IndentViolation{line=%d, msg='%s'}", lineNumber, message);
    }
}

