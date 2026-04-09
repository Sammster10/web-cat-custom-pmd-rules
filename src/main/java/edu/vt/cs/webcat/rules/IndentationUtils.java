package edu.vt.cs.webcat.rules;

import java.util.regex.Pattern;

final class IndentationUtils {

    static final Pattern CASE_LABEL_PATTERN =
            Pattern.compile("^\\s*(?:case\\s+.+|default\\s*):(?!:)");

    static final Pattern SWITCH_OPEN_PATTERN =
            Pattern.compile("\\bswitch\\b.*\\{");

    private IndentationUtils() {
    }

    static boolean isCaseLabelLine(String strippedLine) {
        return CASE_LABEL_PATTERN.matcher(strippedLine).find();
    }

    static boolean isArrowCase(String strippedLine) {
        String trimmed = strippedLine.stripLeading();
        return trimmed.matches("\\s*(?:case\\s+.+|default\\s*)\\s*->.*");
    }

    static boolean isCommentLine(String line, boolean insideBlockComment) {
        if (insideBlockComment) {
            return true;
        }
        String trimmed = line.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    static int countLeadingClosers(String strippedLine) {
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

    static DepthDelta computeDepthDelta(String strippedLine) {
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

    static boolean containsTabs(String line) {
        return line.indexOf('\t') >= 0;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    static String stripLiteralContents(String line, boolean startInsideBlockComment) {
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

    record DepthDelta(int netChange, boolean endsInsideBlockComment) {
    }

    record SwitchContext(int braceDepth, int previousFrozenBonus, int previousActiveCaseBonus) {
    }
}

