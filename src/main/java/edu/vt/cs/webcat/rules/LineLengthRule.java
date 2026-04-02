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

public class LineLengthRule extends AbstractRule {

    private static final PropertyDescriptor<Pattern> FILE_PATTERN_PROPERTY =
            PropertyFactory.regexProperty("filePattern")
                    .desc("If the file path matches this regex, the filePatternMaxLength is used instead. Use this to exempt or adjust limits for certain files (e.g. test classes).")
                    .defaultValue("$.^")
                    .build();

    private static final PropertyDescriptor<Integer> FILE_PATTERN_MAX_LENGTH_PROPERTY =
            PropertyFactory.intProperty("filePatternMaxLength")
                    .desc("Max line length for files matching filePattern. A value of 0 or less means the file is exempt from line length checks.")
                    .defaultValue(0)
                    .build();

    private static final PropertyDescriptor<Pattern> IGNORE_PATTERN_PROPERTY =
            PropertyFactory.regexProperty("ignorePattern")
                    .desc("Lines matching this regular expression will be ignored when checking for line length violations")
                    .defaultValue("$.^")
                    .build();

    private static final PropertyDescriptor<Integer> MAX_LENGTH_PROPERTY =
            PropertyFactory.intProperty("maxLength")
                    .desc("Maximum allowed line length")
                    .defaultValue(80)
                    .build();

    private static final PropertyDescriptor<String> MESSAGE_PROPERTY =
            PropertyFactory.stringProperty("message")
                    .desc("Custom message to display when the rule is violated. Placeholders: {0}=max length, {1}=actual length")
                    .defaultValue("")
                    .build();


    public LineLengthRule() {
        definePropertyDescriptor(FILE_PATTERN_PROPERTY);
        definePropertyDescriptor(FILE_PATTERN_MAX_LENGTH_PROPERTY);
        definePropertyDescriptor(IGNORE_PATTERN_PROPERTY);
        definePropertyDescriptor(MAX_LENGTH_PROPERTY);
        definePropertyDescriptor(MESSAGE_PROPERTY);
    }

    @Override
    public void apply(Node target, RuleContext ctx) {
        TextDocument textDocument = target.getTextDocument();
        Chars fileContent = textDocument.getText();

        int maxLength = getProperty(MAX_LENGTH_PROPERTY);
        String customMessage = getProperty(MESSAGE_PROPERTY);
        Pattern ignorePattern = getProperty(IGNORE_PATTERN_PROPERTY);
        Pattern filePattern = getProperty(FILE_PATTERN_PROPERTY);
        int filePatternMaxLength = getProperty(FILE_PATTERN_MAX_LENGTH_PROPERTY);

        String filePath = textDocument.getFileId().getAbsolutePath();
        if (filePattern != null && filePattern.matcher(filePath).find()) {
            if (filePatternMaxLength <= 0) {
                return;
            }
            maxLength = filePatternMaxLength;
        }

        String[] lines = fileContent.toString().split("\n");
        int offset = 0;
        for (String line : lines) {
            if (line.length() > maxLength) {

                if (ignorePattern != null) {
                    Matcher matcher = ignorePattern.matcher(line);
                    if (matcher.find()) {
                        offset += line.length() + 1;
                        continue;
                    }
                }

                String message = customMessage.isEmpty()
                        ? "Line exceeds maximum length of {0}: {1} characters"
                        : customMessage;

                int startLine = textDocument.lineColumnAtOffset(offset).getLine();

                ctx.addViolationWithPosition(target, startLine, startLine, message, maxLength, line.length());
            }

            offset += line.length() + 1;
        }
    }
}