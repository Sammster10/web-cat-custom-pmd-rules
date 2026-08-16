package edu.vt.cs.webcat.rules.utils.indentation;

/** Structural depth change contributed by one source line. */
public final class DepthDelta {

    private final int netChange;
    private final boolean endsInsideBlockComment;

    public DepthDelta(int netChange, boolean endsInsideBlockComment) {
        this.netChange = netChange;
        this.endsInsideBlockComment = endsInsideBlockComment;
    }

    public int getNetChange() {
        return netChange;
    }

    public boolean getEndsInsideBlockComment() {
        return endsInsideBlockComment;
    }
}
