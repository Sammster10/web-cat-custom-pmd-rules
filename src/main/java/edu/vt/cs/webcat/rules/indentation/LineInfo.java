package edu.vt.cs.webcat.rules.indentation;

public class LineInfo {

    private final int lineNumber;
    private final String text;
    private final int leadingSpaces;
    private final boolean hasLeadingTab;
    private final boolean blank;
    private final boolean commentOnly;
    private final boolean insideBlockComment;
    private final boolean insideJavadoc;
    private final boolean insideTextBlock;

    public LineInfo(int lineNumber, String text, int leadingSpaces,
                    boolean hasLeadingTab, boolean blank, boolean commentOnly,
                    boolean insideBlockComment, boolean insideJavadoc,
                    boolean insideTextBlock) {
        this.lineNumber = lineNumber;
        this.text = text;
        this.leadingSpaces = leadingSpaces;
        this.hasLeadingTab = hasLeadingTab;
        this.blank = blank;
        this.commentOnly = commentOnly;
        this.insideBlockComment = insideBlockComment;
        this.insideJavadoc = insideJavadoc;
        this.insideTextBlock = insideTextBlock;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getText() {
        return text;
    }

    public int getLeadingSpaces() {
        return leadingSpaces;
    }

    public boolean hasLeadingTab() {
        return hasLeadingTab;
    }

    public boolean isBlank() {
        return blank;
    }

    public boolean isCommentOnly() {
        return commentOnly;
    }

    public boolean isInsideBlockComment() {
        return insideBlockComment;
    }

    public boolean isInsideJavadoc() {
        return insideJavadoc;
    }

    public boolean isInsideTextBlock() {
        return insideTextBlock;
    }

    @Override
    public String toString() {
        return String.format("LineInfo{line=%d, spaces=%d, tab=%s, blank=%s, comment=%s}",
                lineNumber, leadingSpaces, hasLeadingTab, blank, commentOnly);
    }
}

