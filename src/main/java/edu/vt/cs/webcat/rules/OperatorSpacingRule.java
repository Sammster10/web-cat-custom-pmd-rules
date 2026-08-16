package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.spacing.OperatorSpacingContext;
import edu.vt.cs.webcat.rules.utils.spacing.SpacingViolationReporter;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;

import java.util.List;
import java.util.Set;

import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.hasWhitespaceBetween;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.sameLine;
import static edu.vt.cs.webcat.rules.utils.spacing.SpacingUtils.tokens;

/** Enforces unary, binary, ternary, label, and control-flow keyword spacing. */
public class OperatorSpacingRule extends AbstractJavaRulechainRule {

    private static final Set<Integer> KEYWORD_TOKEN_KINDS = Set.of(
            JavaTokenKinds.IF,
            JavaTokenKinds.ELSE,
            JavaTokenKinds.FOR,
            JavaTokenKinds.SWITCH,
            JavaTokenKinds.WHILE,
            JavaTokenKinds.DO,
            JavaTokenKinds.TRY,
            JavaTokenKinds.CATCH,
            JavaTokenKinds.FINALLY,
            JavaTokenKinds.SYNCHRONIZED,
            JavaTokenKinds.RETURN
    );

    public OperatorSpacingRule() {
        super(ASTCompilationUnit.class);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        OperatorSpacingContext context = OperatorSpacingContext.from(node);
        SpacingViolationReporter reporter =
                new SpacingViolationReporter(node, asCtx(data));
        List<JavaccToken> sourceTokens = tokens(node);

        for (int index = 0; index < sourceTokens.size(); index++) {
            JavaccToken token = sourceTokens.get(index);
            JavaccToken previous = index > 0 ? sourceTokens.get(index - 1) : null;
            JavaccToken next = index + 1 < sourceTokens.size()
                    ? sourceTokens.get(index + 1) : null;

            checkPrefixUnarySpacing(context, reporter, token, next);
            checkPostfixUnarySpacing(context, reporter, token, previous);
            checkOperatorOrKeywordSpacing(
                    context, reporter, token, previous, next);
        }
        return data;
    }

    private static void checkPrefixUnarySpacing(
            OperatorSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken next) {
        if (next == null || !context.isPrefixUnary(token)) {
            return;
        }
        int kind = token.kind;
        if (kind != JavaTokenKinds.BANG && kind != JavaTokenKinds.INCR
                && kind != JavaTokenKinds.DECR && kind != JavaTokenKinds.PLUS
                && kind != JavaTokenKinds.MINUS) {
            return;
        }
        if (hasWhitespaceBetween(token, next)) {
            reporter.report(token,
                    String.format(
                            "No whitespace allowed after prefix unary operator '%s'.",
                            token.getImage()));
        }
    }

    private static void checkPostfixUnarySpacing(
            OperatorSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous) {
        if (previous == null || !context.isPostfixUnary(token)) {
            return;
        }
        if (token.kind != JavaTokenKinds.INCR
                && token.kind != JavaTokenKinds.DECR) {
            return;
        }
        if (hasWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    String.format(
                            "No whitespace allowed before postfix operator '%s'.",
                            token.getImage()));
        }
    }

    private static void checkOperatorOrKeywordSpacing(
            OperatorSpacingContext context,
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        int kind = token.kind;
        boolean keyword = KEYWORD_TOKEN_KINDS.contains(kind)
                || kind == JavaTokenKinds.IDENTIFIER
                && "assert".equals(token.getImage());
        if (keyword) {
            checkKeywordSpacing(reporter, token, previous, next);
            return;
        }

        if (kind == JavaTokenKinds.PLUS || kind == JavaTokenKinds.MINUS) {
            if (context.isBinaryPlusOrMinus(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.GT || kind == JavaTokenKinds.LT) {
            if (!context.isGenericAngleBracket(token)
                    && context.isBinaryGreaterOrLess(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.RSIGNEDSHIFT
                || kind == JavaTokenKinds.RUNSIGNEDSHIFT) {
            if (!context.isGenericAngleBracket(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.STAR) {
            if (context.isBinaryStar(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.BIT_AND) {
            if (context.isBinaryAmpersand(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.HOOK) {
            if (!context.isWildcardQuestion(token)
                    && context.isTernaryQuestion(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (kind == JavaTokenKinds.COLON) {
            if (context.isBinaryColon(token)) {
                checkBinaryOperatorSpacing(reporter, token, previous, next);
            } else {
                // Method references use the distinct METHOD_REF token. Other
                // single colons terminate switch labels or labeled statements.
                checkLabelColonSpacing(reporter, token, previous, next);
            }
            return;
        }

        if (isAlwaysBinaryOperator(kind)) {
            checkBinaryOperatorSpacing(reporter, token, previous, next);
        }
    }

    private static void checkKeywordSpacing(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous != null && sameLine(previous, token)
                && !hasWhitespaceBetween(previous, token)
                && previous.kind != JavaTokenKinds.SEMICOLON
                && previous.kind != JavaTokenKinds.LBRACE
                && previous.kind != JavaTokenKinds.RBRACE
                && previous.kind != JavaTokenKinds.LPAREN
                && previous.kind != JavaTokenKinds.AT) {
            reporter.report(token,
                    String.format("Whitespace required before keyword '%s'.",
                            token.getImage()));
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && !hasWhitespaceBetween(token, next)
                && next.kind != JavaTokenKinds.SEMICOLON
                && next.kind != JavaTokenKinds.RPAREN) {
            reporter.report(token,
                    String.format("Whitespace required after keyword '%s'.",
                            token.getImage()));
        }
    }

    private static void checkBinaryOperatorSpacing(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous != null && sameLine(previous, token)
                && !hasWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    String.format("Whitespace required before '%s'.",
                            token.getImage()));
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && !hasWhitespaceBetween(token, next)) {
            reporter.report(token,
                    String.format("Whitespace required after '%s'.",
                            token.getImage()));
        }
    }

    private static void checkLabelColonSpacing(
            SpacingViolationReporter reporter,
            JavaccToken token,
            JavaccToken previous,
            JavaccToken next) {
        if (previous != null && sameLine(previous, token)
                && hasWhitespaceBetween(previous, token)) {
            reporter.report(token,
                    "No whitespace allowed before ':' in a label.");
        }
        if (next != null && !next.isEof() && sameLine(token, next)
                && !hasWhitespaceBetween(token, next)) {
            reporter.report(token,
                    "Whitespace required after ':' in a label.");
        }
    }

    private static boolean isAlwaysBinaryOperator(int kind) {
        return kind == JavaTokenKinds.ASSIGN
                || kind == JavaTokenKinds.EQ || kind == JavaTokenKinds.NE
                || kind == JavaTokenKinds.LE || kind == JavaTokenKinds.GE
                || kind == JavaTokenKinds.SC_AND || kind == JavaTokenKinds.SC_OR
                || kind == JavaTokenKinds.BIT_OR || kind == JavaTokenKinds.XOR
                || kind == JavaTokenKinds.LSHIFT
                || kind == JavaTokenKinds.LSHIFTASSIGN
                || kind == JavaTokenKinds.RSIGNEDSHIFTASSIGN
                || kind == JavaTokenKinds.RUNSIGNEDSHIFTASSIGN
                || kind == JavaTokenKinds.SLASH
                || kind == JavaTokenKinds.SLASHASSIGN
                || kind == JavaTokenKinds.REM || kind == JavaTokenKinds.REMASSIGN
                || kind == JavaTokenKinds.PLUSASSIGN
                || kind == JavaTokenKinds.MINUSASSIGN
                || kind == JavaTokenKinds.STARASSIGN
                || kind == JavaTokenKinds.ANDASSIGN
                || kind == JavaTokenKinds.ORASSIGN
                || kind == JavaTokenKinds.XORASSIGN;
    }
}
