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
 * <h3>Switch/case indentation</h3>
 * Traditional {@code case X:} and {@code default:} labels inside a switch
 * block are recognised and cause an additional indentation level for the
 * statements that follow the label. Arrow-style cases ({@code case X ->})
 * do not trigger the extra level because their bodies are already scoped
 * by braces or are single expressions. Nested switches are fully supported
 * via a depth-tracking stack.
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
 */
public class SimpleIndentationRule extends AbstractRule {

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

    public SimpleIndentationRule() {
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

        Deque<IndentationUtils.SwitchContext> switchStack = new ArrayDeque<>();
        int frozenBonus = 0;
        int activeCaseBonus = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;

            if (tabsBanned) {
                String lineOutsideLiterals = IndentationUtils.stripLiteralContents(line, insideBlockComment);
                if (lineOutsideLiterals.indexOf('\t') >= 0) {
                    int reportLine = document.lineColumnAtOffset(offset).getLine();
                    context.addViolationWithPosition(
                            target, reportLine, reportLine, tabMessage, lineNumber);
                }
            }

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
}

