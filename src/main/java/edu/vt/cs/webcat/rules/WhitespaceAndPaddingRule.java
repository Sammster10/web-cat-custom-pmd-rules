package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.GenericToken;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.document.FileLocation;
import net.sourceforge.pmd.lang.document.TextRegion;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhitespaceAndPaddingRule extends AbstractJavaRulechainRule {

    private static final Set<Integer> KEYWORD_WHITESPACE_TOKEN_KINDS = Set.of(
            JavaTokenKinds.IF,
            JavaTokenKinds.ELSE,
            JavaTokenKinds.FOR,
            JavaTokenKinds.WHILE,
            JavaTokenKinds.DO,
            JavaTokenKinds.TRY,
            JavaTokenKinds.CATCH,
            JavaTokenKinds.FINALLY,
            JavaTokenKinds.SYNCHRONIZED,
            JavaTokenKinds.RETURN
    );

    private final Set<Integer> reportedPositions = new HashSet<>();
    private Set<Integer> genericAngleBracketOffsets;
    private Set<Integer> binaryPlusMinusOffsets;
    private Set<Integer> binaryGtLtOffsets;
    private Set<Integer> binaryStarOffsets;
    private Set<Integer> binaryAmpOffsets;
    private Set<Integer> colonOffsets;
    private Set<Integer> methodParenOffsets;
    private Set<Integer> prefixUnaryOffsets;
    private Set<Integer> postfixUnaryOffsets;
    private Set<Integer> dotOffsets;
    private Set<Integer> arrayInitBraceOffsets;

    public WhitespaceAndPaddingRule() {
        super(ASTCompilationUnit.class);
    }

    private static int startOf(JavaccToken t) {
        return t.getRegion().getStartOffset();
    }

    private static int endOf(JavaccToken t) {
        return t.getRegion().getEndOffset();
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        reportedPositions.clear();

        genericAngleBracketOffsets = new HashSet<>();
        binaryPlusMinusOffsets = new HashSet<>();
        binaryGtLtOffsets = new HashSet<>();
        binaryStarOffsets = new HashSet<>();
        binaryAmpOffsets = new HashSet<>();
        colonOffsets = new HashSet<>();
        methodParenOffsets = new HashSet<>();
        prefixUnaryOffsets = new HashSet<>();
        postfixUnaryOffsets = new HashSet<>();
        dotOffsets = new HashSet<>();
        arrayInitBraceOffsets = new HashSet<>();

        collectAstContext(node);

        JavaccToken first = node.getFirstToken();
        JavaccToken last = node.getLastToken();

        List<JavaccToken> tokens = new ArrayList<>();
        for (JavaccToken t : GenericToken.range(first, last)) {
            tokens.add(t);
        }

        for (int i = 0; i < tokens.size(); i++) {
            JavaccToken token = tokens.get(i);
            JavaccToken prev = i > 0 ? tokens.get(i - 1) : null;
            JavaccToken next = i < tokens.size() - 1 ? tokens.get(i + 1) : null;

            checkPrefixUnarySpacing(node, ctx, token, next);
            checkPostfixIncDecSpacing(node, ctx, token, prev);
            checkDotSpacing(node, ctx, token, prev);
            checkDelimiterSpacing(node, ctx, token, next);
            checkWhitespaceAroundOperatorsAndKeywords(node, ctx, token, prev, next);
            checkMethodConstructorParenPadding(node, ctx, token, prev);
            checkGenericTypeSpacing(node, ctx, token, prev, next);
        }

        return data;
    }

    private void collectAstContext(ASTCompilationUnit root) {
        collectGenericBrackets(root);
        collectBinaryOperators(root);
        collectColons(root);
        collectMethodParens(root);
        collectUnaryOperators(root);
        collectDots(root);
        collectArrayInitBraces(root);
    }

    private void collectGenericBrackets(ASTCompilationUnit root) {
        for (ASTTypeArguments ta : root.descendants(ASTTypeArguments.class)) {
            genericAngleBracketOffsets.add(startOf(ta.getFirstToken()));
            genericAngleBracketOffsets.add(startOf(ta.getLastToken()));
        }
        for (ASTTypeParameters tp : root.descendants(ASTTypeParameters.class)) {
            genericAngleBracketOffsets.add(startOf(tp.getFirstToken()));
            genericAngleBracketOffsets.add(startOf(tp.getLastToken()));
        }
    }

    private void collectBinaryOperators(ASTCompilationUnit root) {
        for (ASTInfixExpression infix : root.descendants(ASTInfixExpression.class)) {
            BinaryOp op = infix.getOperator();
            JavaccToken opToken = findOperatorToken(infix);
            if (opToken == null) {
                continue;
            }
            int offset = startOf(opToken);
            if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
                binaryPlusMinusOffsets.add(offset);
            }
            if (op == BinaryOp.GT || op == BinaryOp.LT) {
                binaryGtLtOffsets.add(offset);
            }
            if (op == BinaryOp.MUL) {
                binaryStarOffsets.add(offset);
            }
            if (op == BinaryOp.AND) {
                binaryAmpOffsets.add(offset);
            }
        }

        for (ASTAssignmentExpression assign : root.descendants(ASTAssignmentExpression.class)) {
            JavaccToken opToken = findAssignmentOperatorToken(assign);
            if (opToken != null) {
                int offset = startOf(opToken);
                if (assign.getOperator() == AssignmentOp.ADD_ASSIGN
                        || assign.getOperator() == AssignmentOp.SUB_ASSIGN) {
                    binaryPlusMinusOffsets.add(offset);
                }
            }
        }
    }

    private void collectColons(ASTCompilationUnit root) {
        for (ASTConditionalExpression ternary : root.descendants(ASTConditionalExpression.class)) {
            for (JavaccToken t : ternary.tokens()) {
                if (t.kind == JavaTokenKinds.COLON) {
                    colonOffsets.add(startOf(t));
                }
            }
        }
        for (ASTForeachStatement forEach : root.descendants(ASTForeachStatement.class)) {
            for (JavaccToken t : forEach.tokens()) {
                if (t.kind == JavaTokenKinds.COLON) {
                    colonOffsets.add(startOf(t));
                    break;
                }
            }
        }
        for (ASTSwitchLabel label : root.descendants(ASTSwitchLabel.class)) {
            for (JavaccToken t : label.tokens()) {
                if (t.kind == JavaTokenKinds.COLON) {
                    colonOffsets.add(startOf(t));
                }
            }
        }
        for (ASTLabeledStatement labeled : root.descendants(ASTLabeledStatement.class)) {
            for (JavaccToken t : labeled.tokens()) {
                if (t.kind == JavaTokenKinds.COLON) {
                    colonOffsets.add(startOf(t));
                    break;
                }
            }
        }
    }

    private void collectMethodParens(ASTCompilationUnit root) {
        for (ASTMethodDeclaration md : root.descendants(ASTMethodDeclaration.class)) {
            ASTFormalParameters params = md.getFormalParameters();
            methodParenOffsets.add(startOf(params.getFirstToken()));
        }
        for (ASTConstructorDeclaration cd : root.descendants(ASTConstructorDeclaration.class)) {
            ASTFormalParameters params = cd.getFormalParameters();
            methodParenOffsets.add(startOf(params.getFirstToken()));
        }
        for (ASTMethodCall mc : root.descendants(ASTMethodCall.class)) {
            ASTArgumentList args = mc.getArguments();
            methodParenOffsets.add(startOf(args.getFirstToken()));
        }
        for (ASTConstructorCall cc : root.descendants(ASTConstructorCall.class)) {
            ASTArgumentList args = cc.getArguments();
            methodParenOffsets.add(startOf(args.getFirstToken()));
        }
        for (ASTExplicitConstructorInvocation eci : root.descendants(ASTExplicitConstructorInvocation.class)) {
            ASTArgumentList args = eci.getArguments();
            methodParenOffsets.add(startOf(args.getFirstToken()));
        }
    }

    private void collectUnaryOperators(ASTCompilationUnit root) {
        for (ASTUnaryExpression unary : root.descendants(ASTUnaryExpression.class)) {
            UnaryOp op = unary.getOperator();
            if (op.isPrefix()) {
                prefixUnaryOffsets.add(startOf(unary.getFirstToken()));
            } else {
                postfixUnaryOffsets.add(startOf(unary.getLastToken()));
            }
        }
    }

    private void collectDots(ASTCompilationUnit root) {
        for (ASTFieldAccess fa : root.descendants(ASTFieldAccess.class)) {
            collectDotTokens(fa);
        }
        for (ASTMethodCall mc : root.descendants(ASTMethodCall.class)) {
            collectDotTokens(mc);
        }
        for (ASTClassType ct : root.descendants(ASTClassType.class)) {
            collectDotTokens(ct);
        }
    }

    private void collectDotTokens(JavaNode node) {
        for (JavaccToken t : node.tokens()) {
            if (t.kind == JavaTokenKinds.DOT) {
                dotOffsets.add(startOf(t));
            }
        }
    }

    private void collectArrayInitBraces(ASTCompilationUnit root) {
        for (ASTArrayInitializer ai : root.descendants(ASTArrayInitializer.class)) {
            arrayInitBraceOffsets.add(startOf(ai.getFirstToken()));
            arrayInitBraceOffsets.add(startOf(ai.getLastToken()));
        }
    }

    private void checkPrefixUnarySpacing(ASTCompilationUnit root, RuleContext ctx,
                                         JavaccToken token, JavaccToken next) {
        if (next == null) {
            return;
        }
        if (!prefixUnaryOffsets.contains(startOf(token))) {
            return;
        }
        int kind = token.kind;
        if (kind != JavaTokenKinds.BANG && kind != JavaTokenKinds.INCR
                && kind != JavaTokenKinds.DECR && kind != JavaTokenKinds.PLUS
                && kind != JavaTokenKinds.MINUS) {
            return;
        }
        if (hasWhitespaceBetween(token, next)) {
            reportViolation(ctx, root, token,
                    String.format("No whitespace allowed after prefix unary operator '%s'.",
                            token.getImage()));
        }
    }

    private void checkPostfixIncDecSpacing(ASTCompilationUnit root, RuleContext ctx,
                                           JavaccToken token, JavaccToken prev) {
        if (prev == null) {
            return;
        }
        if (!postfixUnaryOffsets.contains(startOf(token))) {
            return;
        }
        if (token.kind != JavaTokenKinds.INCR && token.kind != JavaTokenKinds.DECR) {
            return;
        }
        if (hasWhitespaceBetween(prev, token)) {
            reportViolation(ctx, root, token,
                    String.format("No whitespace allowed before postfix operator '%s'.",
                            token.getImage()));
        }
    }

    private void checkDotSpacing(ASTCompilationUnit root, RuleContext ctx,
                                 JavaccToken token, JavaccToken prev) {
        if (prev == null || token.kind != JavaTokenKinds.DOT) {
            return;
        }
        if (!dotOffsets.contains(startOf(token))) {
            return;
        }
        if (sameLine(prev, token) && hasHorizontalWhitespaceBetween(prev, token)) {
            reportViolation(ctx, root, token,
                    "No horizontal whitespace allowed before '.' on the same line.");
        }
    }

    private void checkDelimiterSpacing(ASTCompilationUnit root, RuleContext ctx,
                                       JavaccToken token, JavaccToken next) {
        if (token.kind != JavaTokenKinds.COMMA && token.kind != JavaTokenKinds.SEMICOLON) {
            return;
        }
        if (next == null || next.isEof()) {
            return;
        }
        if (!hasWhitespaceBetween(token, next)) {
            reportViolation(ctx, root, token,
                    String.format("'%s' must be followed by whitespace or a line break.",
                            token.getImage()));
        }
    }

    private void checkWhitespaceAroundOperatorsAndKeywords(ASTCompilationUnit root, RuleContext ctx,
                                                           JavaccToken token, JavaccToken prev,
                                                           JavaccToken next) {
        int kind = token.kind;

        if (KEYWORD_WHITESPACE_TOKEN_KINDS.contains(kind)) {
            checkKeywordSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.IDENTIFIER && "assert".equals(token.getImage())) {
            checkKeywordSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.PLUS || kind == JavaTokenKinds.MINUS) {
            if (!binaryPlusMinusOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.GT || kind == JavaTokenKinds.LT) {
            if (genericAngleBracketOffsets.contains(startOf(token))) {
                return;
            }
            if (!binaryGtLtOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.RSIGNEDSHIFT || kind == JavaTokenKinds.RUNSIGNEDSHIFT) {
            if (genericAngleBracketOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.STAR) {
            if (!binaryStarOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.BIT_AND) {
            if (!binaryAmpOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.COLON) {
            if (!colonOffsets.contains(startOf(token))) {
                return;
            }
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
            return;
        }

        if (kind == JavaTokenKinds.LBRACE || kind == JavaTokenKinds.RBRACE) {
            checkBraceSpacing(root, ctx, token, prev, next);
            return;
        }

        if (isAlwaysBinaryOperator(kind)) {
            checkBinaryOperatorSpacing(root, ctx, token, prev, next);
        }
    }

    private boolean isAlwaysBinaryOperator(int kind) {
        return kind == JavaTokenKinds.ASSIGN
                || kind == JavaTokenKinds.EQ || kind == JavaTokenKinds.NE
                || kind == JavaTokenKinds.LE || kind == JavaTokenKinds.GE
                || kind == JavaTokenKinds.SC_AND || kind == JavaTokenKinds.SC_OR
                || kind == JavaTokenKinds.BIT_OR || kind == JavaTokenKinds.XOR
                || kind == JavaTokenKinds.LSHIFT || kind == JavaTokenKinds.LSHIFTASSIGN
                || kind == JavaTokenKinds.RSIGNEDSHIFTASSIGN || kind == JavaTokenKinds.RUNSIGNEDSHIFTASSIGN
                || kind == JavaTokenKinds.SLASH || kind == JavaTokenKinds.SLASHASSIGN
                || kind == JavaTokenKinds.REM || kind == JavaTokenKinds.REMASSIGN
                || kind == JavaTokenKinds.PLUSASSIGN || kind == JavaTokenKinds.MINUSASSIGN
                || kind == JavaTokenKinds.STARASSIGN
                || kind == JavaTokenKinds.ANDASSIGN || kind == JavaTokenKinds.ORASSIGN
                || kind == JavaTokenKinds.XORASSIGN
                || kind == JavaTokenKinds.HOOK;
    }

    private void checkKeywordSpacing(ASTCompilationUnit root, RuleContext ctx,
                                     JavaccToken token, JavaccToken prev, JavaccToken next) {
        if (prev != null && sameLine(prev, token) && !hasWhitespaceBetween(prev, token)
                && prev.kind != JavaTokenKinds.SEMICOLON && prev.kind != JavaTokenKinds.LBRACE
                && prev.kind != JavaTokenKinds.RBRACE && prev.kind != JavaTokenKinds.LPAREN
                && prev.kind != JavaTokenKinds.AT) {
            reportViolation(ctx, root, token,
                    String.format("Whitespace required before keyword '%s'.", token.getImage()));
        }
        if (next != null && !next.isEof() && sameLine(token, next) && !hasWhitespaceBetween(token, next)) {
            if (next.kind != JavaTokenKinds.SEMICOLON && next.kind != JavaTokenKinds.RPAREN) {
                reportViolation(ctx, root, token,
                        String.format("Whitespace required after keyword '%s'.", token.getImage()));
            }
        }
    }

    private void checkBinaryOperatorSpacing(ASTCompilationUnit root, RuleContext ctx,
                                            JavaccToken token, JavaccToken prev, JavaccToken next) {
        if (prev != null && sameLine(prev, token) && !hasWhitespaceBetween(prev, token)) {
            reportViolation(ctx, root, token,
                    String.format("Whitespace required before '%s'.", token.getImage()));
        }
        if (next != null && !next.isEof() && sameLine(token, next) && !hasWhitespaceBetween(token, next)) {
            reportViolation(ctx, root, token,
                    String.format("Whitespace required after '%s'.", token.getImage()));
        }
    }

    private void checkBraceSpacing(ASTCompilationUnit root, RuleContext ctx,
                                   JavaccToken token, JavaccToken prev, JavaccToken next) {
        boolean isArrayInit = arrayInitBraceOffsets.contains(startOf(token));

        if (token.kind == JavaTokenKinds.LBRACE) {
            if (prev != null && sameLine(prev, token) && !hasWhitespaceBetween(prev, token)
                    && !isArrayInit) {
                reportViolation(ctx, root, token, "Whitespace required before '{'.");
            }
            if (next != null && !next.isEof() && sameLine(token, next) && !hasWhitespaceBetween(token, next)
                    && next.kind != JavaTokenKinds.RBRACE && !isArrayInit) {
                reportViolation(ctx, root, token, "Whitespace required after '{'.");
            }
        }
        if (token.kind == JavaTokenKinds.RBRACE) {
            if (prev != null && sameLine(prev, token) && !hasWhitespaceBetween(prev, token)
                    && prev.kind != JavaTokenKinds.LBRACE && !isArrayInit) {
                reportViolation(ctx, root, token, "Whitespace required before '}'.");
            }
            if (next != null && !next.isEof() && sameLine(token, next) && !hasWhitespaceBetween(token, next)
                    && next.kind != JavaTokenKinds.SEMICOLON && next.kind != JavaTokenKinds.COMMA
                    && next.kind != JavaTokenKinds.RPAREN && next.kind != JavaTokenKinds.RBRACE) {
                reportViolation(ctx, root, token, "Whitespace required after '}'.");
            }
        }
    }

    private void checkMethodConstructorParenPadding(ASTCompilationUnit root, RuleContext ctx,
                                                    JavaccToken token, JavaccToken prev) {
        if (token.kind != JavaTokenKinds.LPAREN || prev == null) {
            return;
        }
        if (!methodParenOffsets.contains(startOf(token))) {
            return;
        }
        if (prev.kind != JavaTokenKinds.IDENTIFIER && prev.kind != JavaTokenKinds.THIS
                && prev.kind != JavaTokenKinds.SUPER && prev.kind != JavaTokenKinds.GT) {
            return;
        }
        if (!sameLine(prev, token)) {
            reportViolation(ctx, root, token,
                    "'(' must be on the same line as the method/constructor name.");
        } else if (hasWhitespaceBetween(prev, token)) {
            reportViolation(ctx, root, token,
                    "No space allowed before '(' in method/constructor declaration or call.");
        }
    }

    private void checkGenericTypeSpacing(ASTCompilationUnit root, RuleContext ctx,
                                         JavaccToken token, JavaccToken prev, JavaccToken next) {
        if (!genericAngleBracketOffsets.contains(startOf(token))) {
            return;
        }

        if (token.kind == JavaTokenKinds.LT) {
            if (prev != null && sameLine(prev, token) && hasWhitespaceBetween(prev, token)
                    && prev.kind != JavaTokenKinds.COMMA && !isModifierOrKeyword(prev)) {
                reportViolation(ctx, root, token,
                        "No space allowed before '<' in generic type.");
            }
            if (next != null && sameLine(token, next) && hasWhitespaceBetween(token, next)
                    && next.kind != JavaTokenKinds.GT) {
                reportViolation(ctx, root, token,
                        "No space allowed after '<' in generic type.");
            }
        }

        if (token.kind == JavaTokenKinds.GT) {
            if (prev != null && sameLine(prev, token) && hasWhitespaceBetween(prev, token)) {
                reportViolation(ctx, root, token,
                        "No space allowed before '>' in generic type.");
            }
            if (next != null && !next.isEof() && sameLine(token, next)) {
                if (next.kind == JavaTokenKinds.GT
                        && genericAngleBracketOffsets.contains(startOf(next))) {
                    if (hasWhitespaceBetween(token, next)) {
                        reportViolation(ctx, root, token,
                                "No space allowed between consecutive '>>' in nested generic type.");
                    }
                } else if (next.kind != JavaTokenKinds.LPAREN
                        && next.kind != JavaTokenKinds.COMMA
                        && next.kind != JavaTokenKinds.SEMICOLON
                        && next.kind != JavaTokenKinds.DOT
                        && next.kind != JavaTokenKinds.RPAREN
                        && next.kind != JavaTokenKinds.LBRACKET
                        && next.kind != JavaTokenKinds.GT
                        && next.kind != JavaTokenKinds.METHOD_REF) {
                    if (!hasWhitespaceBetween(token, next)) {
                        reportViolation(ctx, root, token,
                                "Space required after '>' in generic type.");
                    }
                }
            }
        }

        if (token.kind == JavaTokenKinds.RSIGNEDSHIFT || token.kind == JavaTokenKinds.RUNSIGNEDSHIFT) {
            if (prev != null && sameLine(prev, token) && hasWhitespaceBetween(prev, token)) {
                reportViolation(ctx, root, token,
                        String.format("No space allowed before '%s' in nested generic type.",
                                token.getImage()));
            }
        }
    }

    private JavaccToken findOperatorToken(ASTInfixExpression infix) {
        ASTExpression left = infix.getLeftOperand();
        ASTExpression right = infix.getRightOperand();
        BinaryOp op = infix.getOperator();
        String opImage = op.getToken();

        JavaccToken leftLast = left.getLastToken();
        JavaccToken rightFirst = right.getFirstToken();

        JavaccToken t = leftLast.getNext();
        while (t != null && t != rightFirst) {
            if (tokenImageMatches(t, opImage, op)) {
                return t;
            }
            t = t.getNext();
        }
        return null;
    }

    private boolean tokenImageMatches(JavaccToken t, String opImage, BinaryOp op) {
        if (t.getImage().equals(opImage)) {
            return true;
        }
        if (op == BinaryOp.RIGHT_SHIFT && t.kind == JavaTokenKinds.RSIGNEDSHIFT) {
            return true;
        }
        if (op == BinaryOp.UNSIGNED_RIGHT_SHIFT && t.kind == JavaTokenKinds.RUNSIGNEDSHIFT) {
            return true;
        }
        return false;
    }

    private JavaccToken findAssignmentOperatorToken(ASTAssignmentExpression assign) {
        ASTExpression left = (ASTExpression) assign.getChild(0);
        ASTExpression right = (ASTExpression) assign.getChild(1);
        String opImage = assign.getOperator().getToken();

        JavaccToken leftLast = left.getLastToken();
        JavaccToken rightFirst = right.getFirstToken();

        JavaccToken t = leftLast.getNext();
        while (t != null && t != rightFirst) {
            if (t.getImage().equals(opImage)) {
                return t;
            }
            t = t.getNext();
        }
        return null;
    }

    private boolean hasWhitespaceBetween(JavaccToken a, JavaccToken b) {
        return startOf(b) > endOf(a);
    }

    private boolean hasHorizontalWhitespaceBetween(JavaccToken a, JavaccToken b) {
        if (startOf(b) <= endOf(a)) {
            return false;
        }
        String text = a.getDocument().getTextDocument()
                .sliceOriginalText(TextRegion.fromBothOffsets(endOf(a), startOf(b)))
                .toString();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                return true;
            }
        }
        return false;
    }

    private boolean sameLine(JavaccToken a, JavaccToken b) {
        FileLocation locA = a.getReportLocation();
        FileLocation locB = b.getReportLocation();
        return locA.getEndLine() == locB.getStartLine();
    }

    private boolean isModifierOrKeyword(JavaccToken t) {
        int k = t.kind;
        return k == JavaTokenKinds.PUBLIC || k == JavaTokenKinds.PRIVATE
                || k == JavaTokenKinds.PROTECTED || k == JavaTokenKinds.STATIC
                || k == JavaTokenKinds.FINAL || k == JavaTokenKinds.ABSTRACT
                || k == JavaTokenKinds.NATIVE || k == JavaTokenKinds.TRANSIENT
                || k == JavaTokenKinds.VOLATILE || k == JavaTokenKinds.STRICTFP
                || k == JavaTokenKinds.SYNCHRONIZED
                || k == JavaTokenKinds.VOID || k == JavaTokenKinds.CLASS
                || k == JavaTokenKinds.INTERFACE || k == JavaTokenKinds.RETURN;
    }

    private void reportViolation(RuleContext ctx, ASTCompilationUnit root,
                                 JavaccToken token, String message) {
        int offset = startOf(token);
        if (reportedPositions.contains(offset)) {
            return;
        }
        reportedPositions.add(offset);
        String escaped = message.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
        ctx.addViolationWithPosition(root, token, escaped);
    }
}
