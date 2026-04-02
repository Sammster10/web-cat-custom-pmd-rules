package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.rule.AbstractRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexRule extends AbstractRule {

    private static final PropertyDescriptor<Pattern> REGEX_PROPERTY =
            PropertyFactory.regexProperty("pattern")
                    .desc("The regular expression to check against the source file")
                    .defaultValue(Pattern.compile(".*"))
                    .build();

    private static final PropertyDescriptor<Boolean> SHOULD_MATCH_PROPERTY =
            PropertyFactory.booleanProperty("shouldMatch")
                    .desc("Set to true to enforce the regex is present, false to forbid it")
                    .defaultValue(true)
                    .build();

    private static final PropertyDescriptor<String> MESSAGE_PROPERTY =
            PropertyFactory.stringProperty("message")
                    .desc("Custom message to display when the rule is violated")
                    .defaultValue("")
                    .build();

    private static final PropertyDescriptor<Boolean> MULTILINE_PROPERTY =
            PropertyFactory.booleanProperty("multiline")
                    .desc("Set to true to enable multiline mode (^ and $ match line boundaries)")
                    .defaultValue(false)
                    .build();

    private static final PropertyDescriptor<Boolean> DOTALL_PROPERTY =
            PropertyFactory.booleanProperty("dotall")
                    .desc("Set to true to enable dotall mode (. matches newlines)")
                    .defaultValue(false)
                    .build();

    public RegexRule() {
        definePropertyDescriptor(REGEX_PROPERTY);
        definePropertyDescriptor(SHOULD_MATCH_PROPERTY);
        definePropertyDescriptor(MESSAGE_PROPERTY);
        definePropertyDescriptor(MULTILINE_PROPERTY);
        definePropertyDescriptor(DOTALL_PROPERTY);
    }

    @Override
    public void apply(Node target, RuleContext ctx) {
        TextDocument textDocument = target.getTextDocument();
        Chars fileContent = textDocument.getText();

        Pattern basePattern = getProperty(REGEX_PROPERTY);
        boolean shouldMatch = getProperty(SHOULD_MATCH_PROPERTY);
        String customMessage = getProperty(MESSAGE_PROPERTY);
        boolean multiline = getProperty(MULTILINE_PROPERTY);
        boolean dotall = getProperty(DOTALL_PROPERTY);

        int flags = basePattern.flags();
        if (multiline) {
            flags |= Pattern.MULTILINE;
        }
        if (dotall) {
            flags |= Pattern.DOTALL;
        }
        Pattern pattern = Pattern.compile(basePattern.pattern(), flags);

        Matcher matcher = pattern.matcher(fileContent);

        if (shouldMatch) {
            if (!matcher.find()) {
                String msg = customMessage.isEmpty()
                        ? "Required pattern not found: " + pattern.pattern()
                        : customMessage;

                ctx.addViolationWithMessage(target, msg);
            }
        } else {
            while (matcher.find()) {
                int beginLine = textDocument.lineColumnAtOffset(matcher.start()).getLine();
                int endLine = textDocument.lineColumnAtOffset(matcher.end()).getLine();

                String msg = customMessage.isEmpty()
                        ? "Forbidden pattern found: " + matcher.group()
                        : customMessage;

                ctx.addViolationWithPosition(target, beginLine, endLine, msg);
            }
        }
    }
}