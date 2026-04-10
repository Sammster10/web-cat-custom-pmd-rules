package edu.vt.cs.webcat.rules.utils;

import java.util.regex.Pattern;

public final class IndentationUtils {

    public static final Pattern CASE_LABEL_PATTERN =
            Pattern.compile("^\\s*(?:case\\s+.+|default\\s*):(?!:)");

    public static final Pattern SWITCH_OPEN_PATTERN =
            Pattern.compile("\\bswitch\\b.*\\{");

    private IndentationUtils() {
    }

    public static boolean isCaseLabelLine(String strippedLine) {
        return CASE_LABEL_PATTERN.matcher(strippedLine).find();
    }

    public static boolean isArrowCase(String strippedLine) {
        String trimmed = strippedLine.stripLeading();
        return trimmed.matches("\\s*(?:case\\s+.+|default\\s*)\\s*->.*");
    }

    public static boolean isCommentLine(String line, boolean insideBlockComment) {
        if (insideBlockComment) {
            return true;
        }
        String trimmed = line.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    public static int countLeadingClosers(String strippedLine) {
        String trimmed = strippedLine.stripLeading();
        int count = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (character == '}' || character == ')' || character == ']') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public static DepthDelta computeDepthDelta(String strippedLine) {
        int netChange = 0;
        boolean insideLineComment = false;
        boolean insideBlockComment = false;

        for (int index = 0; index < strippedLine.length(); index++) {
            char character = strippedLine.charAt(index);
            char nextCharacter = (index + 1 < strippedLine.length()) ? strippedLine.charAt(index + 1) : 0;

            if (insideBlockComment) {
                if (character == '*' && nextCharacter == '/') {
                    insideBlockComment = false;
                    index++;
                }
                continue;
            }

            if (insideLineComment) {
                continue;
            }

            if (character == '/' && nextCharacter == '/') {
                insideLineComment = true;
                index++;
                continue;
            }

            if (character == '/' && nextCharacter == '*') {
                insideBlockComment = true;
                index++;
                continue;
            }

            if (character == '{' || character == '(' || character == '[') {
                netChange++;
            } else if (character == '}' || character == ')' || character == ']') {
                netChange--;
            }
        }

        return new DepthDelta(netChange, insideBlockComment);
    }

    public static boolean containsTabs(String line) {
        return line.indexOf('\t') >= 0;
    }

    public static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    public static String stripLiteralContents(String line, boolean startInsideBlockComment) {
        StringBuilder result = new StringBuilder(line.length());
        boolean insideString = false;
        boolean insideChar = false;
        boolean insideBlockComment = startInsideBlockComment;
        boolean insideLineComment = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            char nextCharacter = (index + 1 < line.length()) ? line.charAt(index + 1) : 0;

            if (insideBlockComment) {
                if (character == '*' && nextCharacter == '/') {
                    result.append("*/");
                    index++;
                    insideBlockComment = false;
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (insideLineComment) {
                result.append(' ');
                continue;
            }

            if (!insideString && !insideChar) {
                if (character == '/' && nextCharacter == '/') {
                    result.append("//");
                    index++;
                    insideLineComment = true;
                    continue;
                }
                if (character == '/' && nextCharacter == '*') {
                    result.append("/*");
                    index++;
                    insideBlockComment = true;
                    continue;
                }
            }

            if (character == '\\' && (insideString || insideChar) && index + 1 < line.length()) {
                result.append(' ');
                result.append(' ');
                index++;
                continue;
            }

            if (character == '"' && !insideChar) {
                insideString = !insideString;
                result.append(character);
                continue;
            }

            if (character == '\'' && !insideString) {
                insideChar = !insideChar;
                result.append(character);
                continue;
            }

            if (insideString || insideChar) {
                result.append(' ');
            } else {
                result.append(character);
            }
        }

        return result.toString();
    }

    public static class DepthDelta {
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

    public static class SwitchContext {
        private final int braceDepth;
        private final int previousFrozenBonus;
        private final int previousActiveCaseBonus;

        public SwitchContext(int braceDepth, int previousFrozenBonus, int previousActiveCaseBonus) {
            this.braceDepth = braceDepth;
            this.previousFrozenBonus = previousFrozenBonus;
            this.previousActiveCaseBonus = previousActiveCaseBonus;
        }

        public int getBraceDepth() {
            return braceDepth;
        }

        public int getPreviousFrozenBonus() {
            return previousFrozenBonus;
        }

        public int getPreviousActiveCaseBonus() {
            return previousActiveCaseBonus;
        }
    }
}

