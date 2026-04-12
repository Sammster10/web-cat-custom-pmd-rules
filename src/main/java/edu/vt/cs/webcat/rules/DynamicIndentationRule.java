package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.indentation.IndentViolation;
import edu.vt.cs.webcat.rules.indentation.IndentationAnalyzer;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.rule.AbstractRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A PMD rule that dynamically infers the indentation convention used in a
 * Java source file and enforces consistency based on the detected style.
 *
 * <h3>Design overview</h3>
 *
 * <h4>Structural depth</h4>
 * <p>Structural depth is computed from the PMD AST. Top-level declarations
 * (package, import, class) have depth 0. Type members (fields, methods,
 * constructors) have depth 1. Statements inside blocks increase depth by 1.
 * Switch cases use: switch body = parent+1, case label = switch body+1,
 * case body statements = case label+1. Closing braces match the depth of
 * the owning construct, not the inner content.</p>
 *
 * <h4>Line classification</h4>
 * <p>Each line is classified as BASE, CONTINUATION, or IGNORE. BASE lines
 * start a structural element (declaration, statement, case label, closing
 * brace, else/catch/finally, standalone annotation). CONTINUATION lines
 * continue a prior construct (wrapped arguments, chained calls, binary
 * expressions, etc.). IGNORE lines include blanks, comments, block comment
 * interiors, Javadoc interiors, and text block interiors.</p>
 *
 * <h4>Indent inference</h4>
 * <p>The base indent unit N is inferred from high-signal BASE lines where
 * depth &gt; 0 and spaces &gt; 0 and spaces % depth == 0. A frequency vote
 * determines the winner. Inference requires at least 1 usable sample and
 * 70% winner support. Small files with consistent indentation are fully
 * supported. Ambiguous files intentionally fail closed.</p>
 *
 * <h4>Continuation enforcement</h4>
 * <p>Continuation indentation is enforced strictly: continuation lines must
 * be indented at a whole number of indent levels above the structural depth,
 * i.e. the leading spaces must be at least (depth+1)*N and must be a
 * multiple of N. This ensures continuation lines align to an indent
 * boundary while allowing flexibility in how many levels deep they are.</p>
 *
 * <h3>Known limitations</h3>
 * <ul>
 *   <li>Only one base indent unit N is inferred per file.</li>
 *   <li>Continuation indentation must be a multiple of N but may be
 *       at any depth above the structural depth.</li>
 *   <li>Comment bodies and text block content are ignored.</li>
 *   <li>Inference is file-local.</li>
 *   <li>Ambiguous files intentionally fail closed.</li>
 * </ul>
 */
public class DynamicIndentationRule extends AbstractRule {

    private static final PropertyDescriptor<Boolean> BAN_TABS =
            PropertyFactory.booleanProperty("banTabs")
                    .desc("When true, any line with a tab in its leading indentation is reported.")
                    .defaultValue(true)
                    .build();

    private static final PropertyDescriptor<String> TAB_VIOLATION_MESSAGE =
            PropertyFactory.stringProperty("tabViolationMessage")
                    .desc("Message when a tab character is found. Placeholder: {0}=line number")
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
                    .defaultValue("Could not infer a consistent indentation unit for this file.")
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
        String source = fileContent.toString();

        IndentationAnalyzer.AnalysisResult result =
                IndentationAnalyzer.analyze(source, target);

        boolean tabsBanned = getProperty(BAN_TABS);
        if (tabsBanned) {
            String tabMsg = getProperty(TAB_VIOLATION_MESSAGE);
            for (IndentViolation tv : result.getTabViolations()) {
                int pmdLine = document.lineColumnAtOffset(
                        computeOffset(source, tv.getLineNumber())).getLine();
                context.addViolationWithPosition(
                        target, pmdLine, pmdLine, tabMsg, tv.getLineNumber());
            }
        }

        if (!result.getInference().isSuccess()) {
            boolean hasContentLines = result.getLines().stream()
                    .anyMatch(l -> !l.isBlank() && !l.isCommentOnly()
                            && !l.isInsideBlockComment() && !l.isInsideJavadoc());
            if (hasContentLines && result.getInference().getUsableSamples() > 0) {
                String msg = getProperty(INCONSISTENT_FILE_MESSAGE);
                int firstLine = findFirstCodeLine(source, document);
                int lastLine = findLastCodeLine(source, document);
                if (firstLine > 0 && lastLine > 0) {
                    context.addViolationWithPosition(target, firstLine, lastLine, msg);
                }
            }
            return;
        }

        String indentMsg = getProperty(INDENT_VIOLATION_MESSAGE);
        for (IndentViolation iv : result.getIndentViolations()) {
            int pmdLine = document.lineColumnAtOffset(
                    computeOffset(source, iv.getLineNumber())).getLine();
            context.addViolationWithPosition(
                    target, pmdLine, pmdLine, indentMsg,
                    iv.getLineNumber(),
                    extractExpected(iv.getMessage()),
                    extractActual(iv.getMessage()));
        }
    }

    private static int computeOffset(String source, int lineNumber) {
        int offset = 0;
        int currentLine = 1;
        for (int i = 0; i < source.length() && currentLine < lineNumber; i++) {
            if (source.charAt(i) == '\n') {
                currentLine++;
            }
            offset = i + 1;
        }
        return offset;
    }

    private int findFirstCodeLine(String source, TextDocument document) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.startsWith("package") && !line.startsWith("import")) {
                return document.lineColumnAtOffset(computeLineOffset(lines, i)).getLine();
            }
        }
        return 1;
    }

    private int findLastCodeLine(String source, TextDocument document) {
        String[] lines = source.split("\n", -1);
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) {
                return document.lineColumnAtOffset(computeLineOffset(lines, i)).getLine();
            }
        }
        return 1;
    }

    private static int computeLineOffset(String[] lines, int targetIndex) {
        int offset = 0;
        for (int i = 0; i < targetIndex; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    private static String extractExpected(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("expected (\\d+|at least \\d+) spaces").matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("multiple of (\\d+) spaces").matcher(message);
        if (m2.find()) {
            return "a multiple of " + m2.group(1);
        }
        return "?";
    }

    private static String extractActual(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("found (\\d+)").matcher(message);
        if (m.find()) {
            return m.group(1);
        }
        return "?";
    }
}

