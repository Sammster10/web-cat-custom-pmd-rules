package edu.vt.cs.webcat.rules.indentation;

import java.util.ArrayList;
import java.util.List;

public final class LineScanner {

    private LineScanner() {
    }

    public static List<LineInfo> scan(String source) {
        String[] rawLines = source.split("\n", -1);
        List<LineInfo> result = new ArrayList<>(rawLines.length);

        boolean inBlockComment = false;
        boolean inJavadoc = false;
        boolean inTextBlock = false;

        for (int i = 0; i < rawLines.length; i++) {
            String text = rawLines[i];
            int lineNumber = i + 1;

            boolean blank = text.trim().isEmpty();
            int leadingSpaces = countLeadingSpaces(text);
            boolean hasLeadingTab = hasTabInIndentation(text);

            boolean wasInBlockComment = inBlockComment;
            boolean wasInJavadoc = inJavadoc;

            boolean commentOnly;

            if (inTextBlock) {
                if (countsTextBlockEnd(text, false)) {
                    inTextBlock = false;
                }
                result.add(new LineInfo(lineNumber, text, leadingSpaces,
                        hasLeadingTab, blank, false, false, false, true));
                continue;
            }

            if (inBlockComment || inJavadoc) {
                commentOnly = true;
                if (text.trim().contains("*/")) {
                    inBlockComment = false;
                    inJavadoc = false;
                    String afterClose = text.substring(text.indexOf("*/") + 2).trim();
                    if (!afterClose.isEmpty()) {
                        commentOnly = false;
                    }
                }
                result.add(new LineInfo(lineNumber, text, leadingSpaces,
                        hasLeadingTab, blank, commentOnly,
                        wasInBlockComment && !wasInJavadoc,
                        wasInJavadoc, false));
                continue;
            }

            ScanState state = scanLineContent(text);

            if (state.startsBlockComment || state.startsJavadoc) {
                if (!state.blockCommentClosed) {
                    inBlockComment = state.startsBlockComment && !state.startsJavadoc;
                    inJavadoc = state.startsJavadoc;
                }
            }

            if (state.startsTextBlock && !state.textBlockClosed) {
                inTextBlock = true;
            }

            commentOnly = state.commentOnly;

            result.add(new LineInfo(lineNumber, text, leadingSpaces,
                    hasLeadingTab, blank, commentOnly,
                    false, false, false));
        }

        return result;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    static boolean hasTabInIndentation(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                return true;
            }
            if (c != ' ') {
                break;
            }
        }
        return false;
    }

    private static boolean countsTextBlockEnd(String line, boolean skipOpener) {
        String trimmed = line.trim();
        int searchFrom = 0;
        if (skipOpener) {
            int openerIdx = trimmed.indexOf("\"\"\"");
            if (openerIdx >= 0) {
                searchFrom = openerIdx + 3;
            }
        }
        int idx = trimmed.indexOf("\"\"\"", searchFrom);
        return idx >= 0;
    }

    private static ScanState scanLineContent(String text) {
        String trimmed = text.trim();

        boolean startsBlockComment = false;
        boolean startsJavadoc = false;
        boolean blockCommentClosed = false;
        boolean startsTextBlock = false;
        boolean textBlockClosed = false;
        boolean commentOnly = false;

        if (trimmed.startsWith("//")) {
            commentOnly = true;
        } else if (trimmed.startsWith("/**")) {
            startsJavadoc = true;
            if (trimmed.contains("*/") && trimmed.indexOf("*/") > 2) {
                blockCommentClosed = true;
                String afterClose = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
                commentOnly = afterClose.isEmpty();
            } else {
                commentOnly = true;
            }
        } else if (trimmed.startsWith("/*")) {
            startsBlockComment = true;
            if (trimmed.contains("*/") && trimmed.indexOf("*/") > 1) {
                blockCommentClosed = true;
                String afterClose = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
                commentOnly = afterClose.isEmpty();
            } else {
                commentOnly = true;
            }
        } else if (trimmed.startsWith("*")) {
            commentOnly = true;
        }

        if (!commentOnly) {
            startsTextBlock = containsTextBlockOpener(text);
            if (startsTextBlock) {
                textBlockClosed = countsTextBlockEnd(text, true);
            }
        }

        return new ScanState(startsBlockComment, startsJavadoc,
                blockCommentClosed, startsTextBlock, textBlockClosed, commentOnly);
    }

    private static boolean containsTextBlockOpener(String text) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < text.length() - 2; i++) {
            char c = text.charAt(i);
            if (c == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (c == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }
            if (c == '"' && !inChar) {
                if (!inString && i + 2 < text.length()
                        && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
                    String after = text.substring(i + 3).trim();
                    return after.isEmpty() || after.startsWith("//") || after.startsWith("\\");
                }
                inString = !inString;
            }
        }
        return false;
    }

    private static class ScanState {
        final boolean startsBlockComment;
        final boolean startsJavadoc;
        final boolean blockCommentClosed;
        final boolean startsTextBlock;
        final boolean textBlockClosed;
        final boolean commentOnly;

        ScanState(boolean startsBlockComment, boolean startsJavadoc,
                  boolean blockCommentClosed, boolean startsTextBlock,
                  boolean textBlockClosed, boolean commentOnly) {
            this.startsBlockComment = startsBlockComment;
            this.startsJavadoc = startsJavadoc;
            this.blockCommentClosed = blockCommentClosed;
            this.startsTextBlock = startsTextBlock;
            this.textBlockClosed = textBlockClosed;
            this.commentOnly = commentOnly;
        }
    }
}

