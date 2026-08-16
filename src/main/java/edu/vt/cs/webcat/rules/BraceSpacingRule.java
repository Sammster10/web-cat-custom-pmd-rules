package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.spacing.BraceSpacingContext;
import edu.vt.cs.webcat.rules.utils.spacing.SpacingViolationReporter;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;

import java.util.List;

import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.hasWhitespaceBetween;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.sameLine;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.tokens;

/** Enforces whitespace around braces without controlling brace line placement. */
public class BraceSpacingRule extends AbstractJavaRulechainRule {

    public BraceSpacingRule() {
        super(ASTCompilationUnit.class);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        BraceSpacingContext context = BraceSpacingContext.from(node);
        SpacingViolationReporter reporter =
                new SpacingViolationReporter(node, asCtx(data));
        List<JavaccToken> sourceTokens = tokens(node);

        for (int index = 0; index < sourceTokens.size(); index++) {
            JavaccToken token = sourceTokens.get(index);
            if (token.kind != JavaTokenKinds.LBRACE
                    && token.kind != JavaTokenKinds.RBRACE) {
                continue;
            }
            JavaccToken previous = index > 0 ? sourceTokens.get(index - 1) : null;
            JavaccToken next = index + 1 < sourceTokens.size()
                    ? sourceTokens.get(index + 1) : null;
            checkBraceSpacing(context, reporter, token, previous, next);
        }
        return data;
    }

    private static void checkBraceSpacing(
            BraceSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        boolean arrayInitializer = context.isArrayInitializerBrace(token);
        if (token.kind == JavaTokenKinds.LBRACE) {
            checkOpeningBrace(
                    reporter, token, previous, next, arrayInitializer);
        } else {
            checkClosingBrace(
                    reporter, token, previous, next, arrayInitializer);
        }
    }

    private static void checkOpeningBrace(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next,
            boolean arrayInitializer) {
        if (arrayInitializer) {
            return;
        }
        if (previous != null && sameLine(previous, token)
                && !hasWhitespaceBetween(previous, token)) {
            reporter.report(token, "Whitespace required before '{'.");
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && !hasWhitespaceBetween(token, next)
                && next.kind != JavaTokenKinds.RBRACE) {
            reporter.report(token, "Whitespace required after '{'.");
        }
    }

    private static void checkClosingBrace(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next,
            boolean arrayInitializer) {
        if (!arrayInitializer && previous != null && sameLine(previous, token)
                && !hasWhitespaceBetween(previous, token)
                && previous.kind != JavaTokenKinds.LBRACE) {
            reporter.report(token, "Whitespace required before '}'.");
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && !hasWhitespaceBetween(token, next)
                && next.kind != JavaTokenKinds.SEMICOLON
                && next.kind != JavaTokenKinds.COMMA
                && next.kind != JavaTokenKinds.RPAREN
                && next.kind != JavaTokenKinds.RBRACE
                && next.kind != JavaTokenKinds.DOT) {
            reporter.report(token, "Whitespace required after '}'.");
        }
    }
}
