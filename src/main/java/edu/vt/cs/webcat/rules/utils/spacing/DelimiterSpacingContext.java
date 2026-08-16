package edu.vt.cs.webcat.rules.utils.spacing;

import edu.vt.cs.webcat.rules.DelimiterSpacingRule;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.*;

import java.util.HashSet;
import java.util.Set;

/** AST-derived token roles needed by {@link DelimiterSpacingRule}. */
public final class DelimiterSpacingContext {

    private final Set<Integer> genericAngleBracketOffsets;
    private final Set<Integer> methodParenOffsets = new HashSet<>();
    private final Set<Integer> dotOffsets = new HashSet<>();
    private final Set<Integer> forHeaderSemicolonOffsets = new HashSet<>();

    private DelimiterSpacingContext(ASTCompilationUnit root) {
        genericAngleBracketOffsets =
                SpacingUtils.genericAngleBracketOffsets(root);
        collectMethodParens(root);
        collectDots(root);
        collectForHeaderSemicolons(root);
    }

    public static DelimiterSpacingContext from(ASTCompilationUnit root) {
        return new DelimiterSpacingContext(root);
    }

    public boolean isGenericAngleBracket(JavaccToken token) {
        return genericAngleBracketOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isMethodParen(JavaccToken token) {
        return methodParenOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isMemberAccessDot(JavaccToken token) {
        return dotOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isForHeaderSemicolon(JavaccToken token) {
        return forHeaderSemicolonOffsets.contains(SpacingUtils.startOf(token));
    }

    private void collectMethodParens(ASTCompilationUnit root) {
        for (ASTMethodDeclaration method
                : root.descendants(ASTMethodDeclaration.class)) {
            addMethodParen(method.getFormalParameters());
        }
        for (ASTConstructorDeclaration constructor
                : root.descendants(ASTConstructorDeclaration.class)) {
            addMethodParen(constructor.getFormalParameters());
        }
        for (ASTMethodCall call : root.descendants(ASTMethodCall.class)) {
            addMethodParen(call.getArguments());
        }
        for (ASTConstructorCall call
                : root.descendants(ASTConstructorCall.class)) {
            addMethodParen(call.getArguments());
        }
        for (ASTExplicitConstructorInvocation invocation
                : root.descendants(ASTExplicitConstructorInvocation.class)) {
            addMethodParen(invocation.getArguments());
        }
    }

    private void addMethodParen(JavaNode parametersOrArguments) {
        methodParenOffsets.add(
                SpacingUtils.startOf(parametersOrArguments.getFirstToken()));
    }

    private void collectDots(ASTCompilationUnit root) {
        for (ASTFieldAccess access : root.descendants(ASTFieldAccess.class)) {
            collectDotTokens(access);
        }
        for (ASTMethodCall call : root.descendants(ASTMethodCall.class)) {
            collectDotTokens(call);
        }
        for (ASTClassType type : root.descendants(ASTClassType.class)) {
            collectDotTokens(type);
        }
    }

    private void collectDotTokens(JavaNode node) {
        for (JavaccToken token : node.tokens()) {
            if (token.kind == JavaTokenKinds.DOT) {
                dotOffsets.add(SpacingUtils.startOf(token));
            }
        }
    }

    private void collectForHeaderSemicolons(ASTCompilationUnit root) {
        for (ASTForStatement statement
                : root.descendants(ASTForStatement.class)) {
            for (JavaccToken token : statement.tokens()) {
                if (token.kind == JavaTokenKinds.SEMICOLON) {
                    forHeaderSemicolonOffsets.add(SpacingUtils.startOf(token));
                }
            }
        }
    }
}
