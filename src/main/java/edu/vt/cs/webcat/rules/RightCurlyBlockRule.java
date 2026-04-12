package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.document.TextRegion;
import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.HashSet;
import java.util.Set;

public class RightCurlyBlockRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE =
            PropertyFactory.stringProperty("message")
                    .desc("Message when a right curly brace is not alone on its line.")
                    .defaultValue("Right curly brace must be alone on its line.")
                    .build();

    private final Set<Integer> reportedLines = new HashSet<>();

    public RightCurlyBlockRule() {
        super(ASTBlock.class);
        definePropertyDescriptor(MESSAGE);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        reportedLines.clear();
        return data;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {

        var brace = node.getLastToken();

        if (brace.kind != JavaTokenKinds.RBRACE) {
            return data;
        }

        int line = brace.getReportLocation().getStartLine();

        TextDocument doc = node.getTextDocument();
        TextRegion lineRegion = doc.createLineRange(line, line);

        Chars lineChars = doc.sliceOriginalText(lineRegion);
        String lineText = lineChars.toString().trim();

        if (!lineText.equals("}")) {

            if (reportedLines.add(line)) {

                RuleContext ctx = asCtx(data);

                ctx.addViolationWithPosition(
                        node.getRoot(),
                        brace,
                        getProperty(MESSAGE)
                );
            }
        }

        return data;
    }
}