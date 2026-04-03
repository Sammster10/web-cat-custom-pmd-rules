package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.rule.AbstractRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A PMD rule that enforces consistent depth-aware indentation using spaces
 * and optionally bans tab characters entirely.
 *
 * <h3>Indentation enforcement</h3>
 * The rule tracks nesting depth by counting block-opening ({@code &#123;},
 * {@code [}, {@code (}) and block-closing ({@code &#125;}, {@code ]},
 * {@code )}) characters outside of string literals, character literals, and
 * comments. Each line's leading whitespace must equal exactly
 * {@code depth * indentSize} spaces. When a line begins with one or more
 * closing delimiters the expected depth is reduced accordingly before the
 * check. Blank lines and lines containing tab characters are skipped for the
 * indentation check.
 *
 * <h3>Tab ban</h3>
 * When {@code banTabs} is enabled (default {@code true}), any line containing
 * a tab character outside of a string or character literal is reported as a
 * violation.
 *
 * <h3>Configurable properties</h3>
 * <ul>
 *   <li>{@code indentSize} &mdash; number of spaces per indent level (default 4)</li>
 *   <li>{@code banTabs} &mdash; whether tab characters are forbidden (default true)</li>
 *   <li>{@code tabViolationMessage} &mdash; message reported when a tab is found</li>
 *   <li>{@code indentViolationMessage} &mdash; message reported for bad indentation</li>
 * </ul>
 *
 * @author Web-CAT
 * @version 1.0
 */
public class IndentationRule extends AbstractRule {

    private static final PropertyDescriptor<Integer> INDENT_SIZE =
            PropertyFactory.intProperty("indentSize")
                    .desc("Number of spaces per indentation level. Must be between 1 and 16.")
                    .defaultValue(4)
                    .build();

    private static final PropertyDescriptor<Boolean> BAN_TABS =
            PropertyFactory.booleanProperty("banTabs")
                    .desc("When true, any line containing a tab character is reported as a violation.")
                    .defaultValue(true)
                    .build();

    private static final PropertyDescriptor<String> TAB_VIOLATION_MESSAGE =
            PropertyFactory.stringProperty("tabViolationMessage")
                    .desc("Message when a tab character is found. Placeholders: {0}=line number")
                    .defaultValue("Line {0} contains a tab character. Use spaces for indentation.")
                    .build();

    private static final PropertyDescriptor<String> INDENT_VIOLATION_MESSAGE =
            PropertyFactory.stringProperty("indentViolationMessage")
                    .desc("Message for incorrect indentation. Placeholders: {0}=line number, {1}=expected spaces, {2}=actual spaces")
                    .defaultValue("Line {0} is indented incorrectly. Expected {1} spaces but found {2}.")
                    .build();

    public IndentationRule() {
        definePropertyDescriptor(INDENT_SIZE);
        definePropertyDescriptor(BAN_TABS);
        definePropertyDescriptor(TAB_VIOLATION_MESSAGE);
        definePropertyDescriptor(INDENT_VIOLATION_MESSAGE);
    }

    @Override
    public void apply(Node target, RuleContext context) {
        TextDocument document = target.getTextDocument();
        Chars fileContent = document.getText();

        int indentSize = getProperty(INDENT_SIZE);
        boolean tabsBanned = getProperty(BAN_TABS);
        String tabMessage = getProperty(TAB_VIOLATION_MESSAGE);
        String indentMessage = getProperty(INDENT_VIOLATION_MESSAGE);

        String[] lines = fileContent.toString().split("\n", -1);
        int offset = 0;
        int currentDepth = 0;
        boolean insideBlockComment = false;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;

            if (tabsBanned) {
                String lineOutsideLiterals = stripLiteralContents(line, insideBlockComment);
                if (lineOutsideLiterals.indexOf('\t') >= 0) {
                    int reportLine = document.lineColumnAtOffset(offset).getLine();
                    context.addViolationWithPosition(
                            target,
                            reportLine,
                            reportLine,
                            tabMessage,
                            lineNumber
                    );
                }
            }

            String strippedLine = stripLiteralContents(line, insideBlockComment);
            boolean lineIsComment = isCommentLine(line, insideBlockComment);

            int leadingClosers = countLeadingClosers(strippedLine);
            int expectedDepth = Math.max(0, currentDepth - leadingClosers);

            if (!line.isBlank() && !containsTabs(line) && !lineIsComment) {
                int leadingSpaceCount = countLeadingSpaces(line);
                int expectedSpaces = expectedDepth * indentSize;

                if (leadingSpaceCount != expectedSpaces) {
                    int reportLine = document.lineColumnAtOffset(offset).getLine();
                    context.addViolationWithPosition(
                            target,
                            reportLine,
                            reportLine,
                            indentMessage,
                            lineNumber,
                            expectedSpaces,
                            leadingSpaceCount
                    );
                }
            }

            DepthDelta delta = computeDepthDelta(strippedLine);
            currentDepth = Math.max(0, currentDepth + delta.netChange());
            insideBlockComment = delta.endsInsideBlockComment();

            offset += line.length() + 1;
        }
    }

    private boolean isCommentLine(String line, boolean insideBlockComment) {
        if (insideBlockComment) {
            return true;
        }
        String trimmed = line.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    private int countLeadingClosers(String strippedLine) {
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

    private DepthDelta computeDepthDelta(String strippedLine) {
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

    private boolean containsTabs(String line) {
        return line.indexOf('\t') >= 0;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String stripLiteralContents(String line, boolean startInsideBlockComment) {
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

    private record DepthDelta(int netChange, boolean endsInsideBlockComment) {
    }
}

