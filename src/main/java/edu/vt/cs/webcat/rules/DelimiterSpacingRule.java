package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.spacing.DelimiterSpacingContext;
import edu.vt.cs.webcat.rules.utils.spacing.SpacingViolationReporter;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;

import java.util.List;

import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.hasHorizontalWhitespaceBetween;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.hasWhitespaceBetween;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.isModifierOrKeyword;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.sameLine;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.tokens;

/** Enforces separator, call-parenthesis, generic, and member-access spacing. */
public class DelimiterSpacingRule extends AbstractJavaRulechainRule {

    public DelimiterSpacingRule() {
        super(ASTCompilationUnit.class);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        DelimiterSpacingContext context = DelimiterSpacingContext.from(node);
        SpacingViolationReporter reporter =
                new SpacingViolationReporter(node, asCtx(data));
        List<JavaccToken> sourceTokens = tokens(node);

        for (int index = 0; index < sourceTokens.size(); index++) {
            JavaccToken token = sourceTokens.get(index);
            JavaccToken previous = index > 0 ? sourceTokens.get(index - 1) : null;
            JavaccToken next = index + 1 < sourceTokens.size()
                    ? sourceTokens.get(index + 1) : null;

            checkMemberAccessSpacing(context, reporter, token, previous, next);
            checkSeparatorSpacing(context, reporter, token, next);
            checkMethodParenthesisSpacing(context, reporter, token, previous);
            checkGenericTypeSpacing(context, reporter, token, previous, next);
        }
        return data;
    }

    private static void checkMemberAccessSpacing(
            DelimiterSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous == null || token.kind != JavaTokenKinds.DOT
                || !context.isMemberAccessDot(token)) {
            return;
        }
        if (sameLine(previous, token)
                && hasHorizontalWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    "No horizontal whitespace allowed before '.' on the same line.");
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && hasHorizontalWhitespaceBetween(token, next)) {
            reporter.report(token,
                    "No horizontal whitespace allowed after '.' on the same line.");
        }
    }

    private static void checkSeparatorSpacing(
            DelimiterSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken next) {
        if (token.kind != JavaTokenKinds.COMMA
                && token.kind != JavaTokenKinds.SEMICOLON) {
            return;
        }
        if (next == null || next.isEof()) {
            return;
        }
        if (token.kind == JavaTokenKinds.SEMICOLON
                && context.isForHeaderSemicolon(token)
                && (next.kind == JavaTokenKinds.SEMICOLON
                || next.kind == JavaTokenKinds.RPAREN)) {
            return;
        }
        if (!hasWhitespaceBetween(token, next)) {
            reporter.report(token,
                    String.format(
                            "'%s' must be followed by whitespace or a line break.",
                            token.getImage()));
        }
    }

    private static void checkMethodParenthesisSpacing(
            DelimiterSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous) {
        if (previous == null || token.kind != JavaTokenKinds.LPAREN
                || !context.isMethodParen(token)) {
            return;
        }
        if (previous.kind != JavaTokenKinds.IDENTIFIER
                && previous.kind != JavaTokenKinds.THIS
                && previous.kind != JavaTokenKinds.SUPER
                && previous.kind != JavaTokenKinds.GT) {
            return;
        }
        if (!sameLine(previous, token)) {
            reporter.report(token,
                    "'(' must be on the same line as the method/constructor name.");
        } else if (hasWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    "No space allowed before '(' in method/constructor declaration or call.");
        }
    }

    private static void checkGenericTypeSpacing(
            DelimiterSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (!context.isGenericAngleBracket(token)) {
            return;
        }

        if (token.kind == JavaTokenKinds.LT) {
            checkOpeningGenericBracket(reporter, token, previous, next);
        } else if (token.kind == JavaTokenKinds.GT) {
            checkClosingGenericBracket(context, reporter, token, previous, next);
        } else if (token.kind == JavaTokenKinds.RSIGNEDSHIFT
                || token.kind == JavaTokenKinds.RUNSIGNEDSHIFT) {
            if (previous != null && sameLine(previous, token)
                    && hasWhitespaceBetween(previous, token)) {
                reporter.report(token,
                        String.format(
                                "No space allowed before '%s' in nested generic type.",
                                token.getImage()));
            }
        }
    }

    private static void checkOpeningGenericBracket(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous != null && sameLine(previous, token)
                && hasWhitespaceBetween(previous, token)
                && previous.kind != JavaTokenKinds.COMMA
                && !isModifierOrKeyword(previous)) {
            reporter.report(token,
                    "No space allowed before '<' in generic type.");
        }
        if (next != null && sameLine(token, next)
                && hasWhitespaceBetween(token, next)
                && next.kind != JavaTokenKinds.GT) {
            reporter.report(token,
                    "No space allowed after '<' in generic type.");
        }
    }

    private static void checkClosingGenericBracket(
            DelimiterSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous != null && sameLine(previous, token)
                && hasWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    "No space allowed before '>' in generic type.");
        }
        if (next == null || next.isEof() || !sameLine(token, next)) {
            return;
        }

        if (next.kind == JavaTokenKinds.GT
                && context.isGenericAngleBracket(next)) {
            if (hasWhitespaceBetween(token, next)) {
                reporter.report(token,
                        "No space allowed between consecutive '>>' in nested generic type.");
            }
            return;
        }

        if (next.kind != JavaTokenKinds.LPAREN
                && next.kind != JavaTokenKinds.COMMA
                && next.kind != JavaTokenKinds.SEMICOLON
                && next.kind != JavaTokenKinds.DOT
                && next.kind != JavaTokenKinds.RPAREN
                && next.kind != JavaTokenKinds.LBRACKET
                && next.kind != JavaTokenKinds.GT
                && next.kind != JavaTokenKinds.METHOD_REF
                && !hasWhitespaceBetween(token, next)) {
            reporter.report(token,
                    "Space required after '>' in generic type.");
        }
    }
}
