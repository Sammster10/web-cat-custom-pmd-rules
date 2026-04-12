package edu.vt.cs.webcat.rules.indentation;

public class IndentExpectation {

    private final int structuralDepth;
    private final LineKind kind;
    private final Integer exactSpaces;
    private final Integer minSpaces;
    private final String reason;

    public IndentExpectation(int structuralDepth, LineKind kind,
                             Integer exactSpaces, Integer minSpaces,
                             String reason) {
        this.structuralDepth = structuralDepth;
        this.kind = kind;
        this.exactSpaces = exactSpaces;
        this.minSpaces = minSpaces;
        this.reason = reason;
    }

    public int getStructuralDepth() {
        return structuralDepth;
    }

    public LineKind getKind() {
        return kind;
    }

    public Integer getExactSpaces() {
        return exactSpaces;
    }

    public Integer getMinSpaces() {
        return minSpaces;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("IndentExpectation{depth=%d, kind=%s, exact=%s, min=%s}",
                structuralDepth, kind, exactSpaces, minSpaces);
    }
}

