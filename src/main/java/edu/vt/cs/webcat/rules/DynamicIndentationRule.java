package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.IndentationUtils;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.rule.AbstractRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A PMD rule that dynamically detects the indentation convention used in a
 * source file and enforces consistency based on the detected style.
 *
 * <p>Instead of prescribing a fixed indent size (2, 3, 4, etc.), this rule
 * scans all indented lines, computes the most common number of spaces per
 * nesting level, and then validates every line against that detected
 * convention. This accommodates individual developer preferences while
 * still requiring readable, consistent formatting.</p>
 *
 * <h3>Detection algorithm</h3>
 * <ol>
 *   <li>Walk every line, tracking nesting depth via brace/paren/bracket
 *       counting (delegating to {@link IndentationUtils}).</li>
 *   <li>For each non-blank, non-comment, non-tab line at depth &gt; 0,
 *       compute {@code leadingSpaces / depth}. If the result is a positive
 *       integer and divides evenly, record it as a candidate indent size.</li>
 *   <li>The candidate with the highest frequency wins. If the winning
 *       candidate accounts for less than 50% of all observations the file
 *       is considered inconsistently indented and a single file-level
 *       violation is reported instead of per-line violations.</li>
 * </ol>
 *
 * <h3>Configurable properties</h3>
 * <ul>
 *   <li>{@code banTabs} &mdash; whether tab characters are forbidden
 *       (default {@code true})</li>
 *   <li>{@code tabViolationMessage} &mdash; message reported when a tab
 *       is found</li>
 *   <li>{@code indentViolationMessage} &mdash; message reported for a
 *       single line whose indentation does not match the detected
 *       convention</li>
 *   <li>{@code inconsistentFileMessage} &mdash; message reported when no
 *       single indentation convention reaches the 50% threshold</li>
 * </ul>
 *
 * @author Web-CAT
 * @version 1.0
 */
public class DynamicIndentationRule extends AbstractRule {

    private static final Pattern PACKAGE_OR_IMPORT =
            Pattern.compile("^\\s*(package|import)\\s");

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

    private static final PropertyDescriptor<String> INCONSISTENT_FILE_MESSAGE =
            PropertyFactory.stringProperty("inconsistentFileMessage")
                    .desc("Message when no consistent indentation convention can be detected.")
                    .defaultValue("This file does not follow a consistent indentation style. "
                            + "No single indent size accounts for the majority of indented lines.")
                    .build();

    public DynamicIndentationRule() {
        definePropertyDescriptor(BAN_TABS);
        definePropertyDescriptor(TAB_VIOLATION_MESSAGE);
        definePropertyDescriptor(INDENT_VIOLATION_MESSAGE);
        definePropertyDescriptor(INCONSISTENT_FILE_MESSAGE);
    }

    @Override
    public void apply(Node target, RuleContext context) {
        TextDocument document = target.getTextDocument();
        Chars fileContent = document.getText();
        String[] lines = fileContent.toString().split("\n", -1);

        boolean tabsBanned = getProperty(BAN_TABS);
        String tabMessage = getProperty(TAB_VIOLATION_MESSAGE);

        reportTabViolations(target, context, document, lines, tabsBanned, tabMessage);

        DetectionResult detection = detectIndentSize(lines);

        if (detection.totalObservations == 0) {
            return;
        }

        if (detection.detectedSize == 0) {
            reportInconsistentFile(target, context, document, lines);
            return;
        }

        enforceDetectedIndent(target, context, document, lines, detection.detectedSize);
    }

    private void reportTabViolations(Node target, RuleContext context,
                                     TextDocument document, String[] lines,
                                     boolean tabsBanned, String tabMessage) {
        if (!tabsBanned) {
            return;
        }
        int offset = 0;
        boolean insideBlockComment = false;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String stripped = IndentationUtils.stripLiteralContents(line, insideBlockComment);
            if (stripped.indexOf('\t') >= 0) {
                int reportLine = document.lineColumnAtOffset(offset).getLine();
                context.addViolationWithPosition(
                        target, reportLine, reportLine, tabMessage, lineIndex + 1);
            }
            IndentationUtils.DepthDelta delta = IndentationUtils.computeDepthDelta(stripped);
            insideBlockComment = delta.getEndsInsideBlockComment();
            offset += line.length() + 1;
        }
    }

    private DetectionResult detectIndentSize(String[] lines) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        int totalObservations = 0;
        int currentDepth = 0;
        boolean insideBlockComment = false;

        Deque<IndentationUtils.SwitchContext> switchStack = new ArrayDeque<>();
        int frozenBonus = 0;
        int activeCaseBonus = 0;

        for (String line : lines) {
            String strippedLine = IndentationUtils.stripLiteralContents(line, insideBlockComment);
            boolean lineIsComment = IndentationUtils.isCommentLine(line, insideBlockComment);
            boolean isCaseLabel = !lineIsComment && IndentationUtils.isCaseLabelLine(strippedLine);

            int leadingClosers = IndentationUtils.countLeadingClosers(strippedLine);
            int depthBeforeClosers = Math.max(0, currentDepth - leadingClosers);

            if (leadingClosers > 0) {
                while (!switchStack.isEmpty()
                        && depthBeforeClosers < switchStack.peek().getBraceDepth()) {
                    IndentationUtils.SwitchContext restored = switchStack.pop();
                    frozenBonus = restored.getPreviousFrozenBonus();
                    activeCaseBonus = restored.getPreviousActiveCaseBonus();
                }
            }

            int effectiveDepth;
            if (isCaseLabel) {
                effectiveDepth = depthBeforeClosers + frozenBonus;
            } else {
                effectiveDepth = depthBeforeClosers + frozenBonus + activeCaseBonus;
            }

            if (!line.isBlank() && !IndentationUtils.containsTabs(line) && !lineIsComment) {
                int leadingSpaces = IndentationUtils.countLeadingSpaces(line);
                if (effectiveDepth > 0 && leadingSpaces > 0 && leadingSpaces % effectiveDepth == 0) {
                    int candidate = leadingSpaces / effectiveDepth;
                    if (candidate > 0) {
                        frequencyMap.merge(candidate, 1, Integer::sum);
                        totalObservations++;
                    }
                }
            }

            if (isCaseLabel && !IndentationUtils.isArrowCase(strippedLine)) {
                IndentationUtils.DepthDelta caseDelta = IndentationUtils.computeDepthDelta(strippedLine);
                activeCaseBonus = caseDelta.getNetChange() <= 0 ? 1 : 0;
            }

            if (!lineIsComment && IndentationUtils.SWITCH_OPEN_PATTERN.matcher(strippedLine).find()) {
                IndentationUtils.DepthDelta preSwitchDelta = IndentationUtils.computeDepthDelta(strippedLine);
                int switchBraceDepth = currentDepth + preSwitchDelta.getNetChange();
                switchStack.push(new IndentationUtils.SwitchContext(switchBraceDepth, frozenBonus, activeCaseBonus));
                frozenBonus = frozenBonus + activeCaseBonus;
                activeCaseBonus = 0;
            }

            IndentationUtils.DepthDelta delta = IndentationUtils.computeDepthDelta(strippedLine);
            int newDepth = Math.max(0, currentDepth + delta.getNetChange());

            while (!switchStack.isEmpty() && newDepth <= switchStack.peek().getBraceDepth() - 1) {
                IndentationUtils.SwitchContext restored = switchStack.pop();
                frozenBonus = restored.getPreviousFrozenBonus();
                activeCaseBonus = restored.getPreviousActiveCaseBonus();
            }

            currentDepth = newDepth;
            insideBlockComment = delta.getEndsInsideBlockComment();
        }

        if (totalObservations == 0) {
            return new DetectionResult(0, 0);
        }

        int bestSize = 0;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestSize = entry.getKey();
            }
        }

        if (bestCount * 2 < totalObservations) {
            return new DetectionResult(0, totalObservations);
        }

        return new DetectionResult(bestSize, totalObservations);
    }

    private void reportInconsistentFile(Node target, RuleContext context,
                                        TextDocument document, String[] lines) {
        String message = getProperty(INCONSISTENT_FILE_MESSAGE);

        int firstCodeLine = -1;
        int lastCodeLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            if (PACKAGE_OR_IMPORT.matcher(line).find()) {
                continue;
            }
            if (firstCodeLine == -1) {
                firstCodeLine = i;
            }
            lastCodeLine = i;
        }

        if (firstCodeLine == -1) {
            return;
        }

        int startReportLine = document.lineColumnAtOffset(computeOffset(lines, firstCodeLine)).getLine();
        int endReportLine = document.lineColumnAtOffset(computeOffset(lines, lastCodeLine)).getLine();
        context.addViolationWithPosition(target, startReportLine, endReportLine, message);
    }

    private void enforceDetectedIndent(Node target, RuleContext context,
                                       TextDocument document, String[] lines,
                                       int indentSize) {
        String indentMessage = getProperty(INDENT_VIOLATION_MESSAGE);

        int offset = 0;
        int currentDepth = 0;
        boolean insideBlockComment = false;

        Deque<IndentationUtils.SwitchContext> switchStack = new ArrayDeque<>();
        int frozenBonus = 0;
        int activeCaseBonus = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;

            String strippedLine = IndentationUtils.stripLiteralContents(line, insideBlockComment);
            boolean lineIsComment = IndentationUtils.isCommentLine(line, insideBlockComment);
            boolean isCaseLabel = !lineIsComment && IndentationUtils.isCaseLabelLine(strippedLine);

            int leadingClosers = IndentationUtils.countLeadingClosers(strippedLine);
            int depthBeforeClosers = Math.max(0, currentDepth - leadingClosers);

            if (leadingClosers > 0) {
                while (!switchStack.isEmpty()
                        && depthBeforeClosers < switchStack.peek().getBraceDepth()) {
                    IndentationUtils.SwitchContext restored = switchStack.pop();
                    frozenBonus = restored.getPreviousFrozenBonus();
                    activeCaseBonus = restored.getPreviousActiveCaseBonus();
                }
            }

            int effectiveExpected;
            if (isCaseLabel) {
                effectiveExpected = depthBeforeClosers + frozenBonus;
            } else {
                effectiveExpected = depthBeforeClosers + frozenBonus + activeCaseBonus;
            }

            if (!line.isBlank() && !IndentationUtils.containsTabs(line) && !lineIsComment) {
                int leadingSpaceCount = IndentationUtils.countLeadingSpaces(line);
                int expectedSpaces = effectiveExpected * indentSize;

                if (leadingSpaceCount != expectedSpaces) {
                    int reportLine = document.lineColumnAtOffset(offset).getLine();
                    context.addViolationWithPosition(
                            target, reportLine, reportLine, indentMessage,
                            lineNumber, expectedSpaces, leadingSpaceCount);
                }
            }

            if (isCaseLabel && !IndentationUtils.isArrowCase(strippedLine)) {
                IndentationUtils.DepthDelta caseDelta = IndentationUtils.computeDepthDelta(strippedLine);
                activeCaseBonus = caseDelta.getNetChange() <= 0 ? 1 : 0;
            }

            if (!lineIsComment && IndentationUtils.SWITCH_OPEN_PATTERN.matcher(strippedLine).find()) {
                IndentationUtils.DepthDelta preSwitchDelta = IndentationUtils.computeDepthDelta(strippedLine);
                int switchBraceDepth = currentDepth + preSwitchDelta.getNetChange();
                switchStack.push(new IndentationUtils.SwitchContext(switchBraceDepth, frozenBonus, activeCaseBonus));
                frozenBonus = frozenBonus + activeCaseBonus;
                activeCaseBonus = 0;
            }

            IndentationUtils.DepthDelta delta = IndentationUtils.computeDepthDelta(strippedLine);
            int newDepth = Math.max(0, currentDepth + delta.getNetChange());

            while (!switchStack.isEmpty() && newDepth <= switchStack.peek().getBraceDepth() - 1) {
                IndentationUtils.SwitchContext restored = switchStack.pop();
                frozenBonus = restored.getPreviousFrozenBonus();
                activeCaseBonus = restored.getPreviousActiveCaseBonus();
            }

            currentDepth = newDepth;
            insideBlockComment = delta.getEndsInsideBlockComment();
            offset += line.length() + 1;
        }
    }

    private static int computeOffset(String[] lines, int targetLineIndex) {
        int offset = 0;
        for (int i = 0; i < targetLineIndex; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    private static class DetectionResult {
        private final int detectedSize;
        private final int totalObservations;

        public DetectionResult(int detectedSize, int totalObservations) {
            this.detectedSize = detectedSize;
            this.totalObservations = totalObservations;
        }

        public int getDetectedSize() {
            return detectedSize;
        }

        public int getTotalObservations() {
            return totalObservations;
        }
    }
}

