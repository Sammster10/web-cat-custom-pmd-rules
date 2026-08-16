package edu.vt.cs.webcat.rules.utils.spacing;

import edu.vt.cs.webcat.rules.OperatorSpacingRule;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.*;

import java.util.HashSet;
import java.util.Set;

/** AST-derived token roles needed by {@link OperatorSpacingRule}. */
public final class OperatorSpacingContext {

    private final Set<Integer> genericAngleBracketOffsets;
    private final Set<Integer> binaryPlusMinusOffsets = new HashSet<>();
    private final Set<Integer> binaryGtLtOffsets = new HashSet<>();
    private final Set<Integer> binaryStarOffsets = new HashSet<>();
    private final Set<Integer> binaryAmpOffsets = new HashSet<>();
    private final Set<Integer> ternaryQuestionOffsets = new HashSet<>();
    private final Set<Integer> wildcardQuestionOffsets = new HashSet<>();
    private final Set<Integer> binaryColonOffsets = new HashSet<>();
    private final Set<Integer> prefixUnaryOffsets = new HashSet<>();
    private final Set<Integer> postfixUnaryOffsets = new HashSet<>();

    private OperatorSpacingContext(ASTCompilationUnit root) {
        genericAngleBracketOffsets =
                SpacingUtils.genericAngleBracketOffsets(root);
        collectBinaryOperators(root);
        collectQuestionMarks(root);
        collectColons(root);
        collectUnaryOperators(root);
    }

    public static OperatorSpacingContext from(ASTCompilationUnit root) {
        return new OperatorSpacingContext(root);
    }

    public boolean isGenericAngleBracket(JavaccToken token) {
        return genericAngleBracketOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isBinaryPlusOrMinus(JavaccToken token) {
        return binaryPlusMinusOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isBinaryGreaterOrLess(JavaccToken token) {
        return binaryGtLtOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isBinaryStar(JavaccToken token) {
        return binaryStarOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isBinaryAmpersand(JavaccToken token) {
        return binaryAmpOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isTernaryQuestion(JavaccToken token) {
        return ternaryQuestionOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isWildcardQuestion(JavaccToken token) {
        return wildcardQuestionOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isBinaryColon(JavaccToken token) {
        return binaryColonOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isPrefixUnary(JavaccToken token) {
        return prefixUnaryOffsets.contains(SpacingUtils.startOf(token));
    }

    public boolean isPostfixUnary(JavaccToken token) {
        return postfixUnaryOffsets.contains(SpacingUtils.startOf(token));
    }

    private void collectBinaryOperators(ASTCompilationUnit root) {
        for (ASTInfixExpression expression
                : root.descendants(ASTInfixExpression.class)) {
            BinaryOp operator = expression.getOperator();
            JavaccToken token = findOperatorToken(expression);
            if (token == null) {
                continue;
            }
            int offset = SpacingUtils.startOf(token);
            if (operator == BinaryOp.ADD || operator == BinaryOp.SUB) {
                binaryPlusMinusOffsets.add(offset);
            } else if (operator == BinaryOp.GT || operator == BinaryOp.LT) {
                binaryGtLtOffsets.add(offset);
            } else if (operator == BinaryOp.MUL) {
                binaryStarOffsets.add(offset);
            } else if (operator == BinaryOp.AND) {
                binaryAmpOffsets.add(offset);
            }
        }

        for (ASTAssignmentExpression expression
                : root.descendants(ASTAssignmentExpression.class)) {
            AssignmentOp operator = expression.getOperator();
            if (operator != AssignmentOp.ADD_ASSIGN
                    && operator != AssignmentOp.SUB_ASSIGN) {
                continue;
            }
            JavaccToken token = findAssignmentOperatorToken(expression);
            if (token != null) {
                binaryPlusMinusOffsets.add(SpacingUtils.startOf(token));
            }
        }
    }

    private void collectQuestionMarks(ASTCompilationUnit root) {
        for (ASTConditionalExpression expression
                : root.descendants(ASTConditionalExpression.class)) {
            JavaccToken token = findTernaryQuestionToken(expression);
            if (token != null) {
                ternaryQuestionOffsets.add(SpacingUtils.startOf(token));
            }
        }

        for (ASTWildcardType wildcard : root.descendants(ASTWildcardType.class)) {
            for (JavaccToken token : wildcard.tokens()) {
                if (token.kind == JavaTokenKinds.HOOK) {
                    wildcardQuestionOffsets.add(SpacingUtils.startOf(token));
                    break;
                }
            }
        }
    }

    private void collectColons(ASTCompilationUnit root) {
        for (ASTConditionalExpression expression
                : root.descendants(ASTConditionalExpression.class)) {
            for (JavaccToken token : expression.tokens()) {
                if (token.kind == JavaTokenKinds.COLON) {
                    binaryColonOffsets.add(SpacingUtils.startOf(token));
                }
            }
        }
        for (ASTForeachStatement statement
                : root.descendants(ASTForeachStatement.class)) {
            for (JavaccToken token : statement.tokens()) {
                if (token.kind == JavaTokenKinds.COLON) {
                    binaryColonOffsets.add(SpacingUtils.startOf(token));
                    break;
                }
            }
        }
    }

    private void collectUnaryOperators(ASTCompilationUnit root) {
        for (ASTUnaryExpression expression
                : root.descendants(ASTUnaryExpression.class)) {
            if (expression.getOperator().isPrefix()) {
                prefixUnaryOffsets.add(
                        SpacingUtils.startOf(expression.getFirstToken()));
            } else {
                postfixUnaryOffsets.add(
                        SpacingUtils.startOf(expression.getLastToken()));
            }
        }
    }

    private JavaccToken findTernaryQuestionToken(
            ASTConditionalExpression expression) {
        JavaccToken token = expression.getCondition().getLastToken().getNext();
        JavaccToken thenFirst = expression.getThenBranch().getFirstToken();
        while (token != null && token != thenFirst) {
            if (token.kind == JavaTokenKinds.HOOK) {
                return token;
            }
            token = token.getNext();
        }
        return null;
    }

    private JavaccToken findOperatorToken(ASTInfixExpression expression) {
        BinaryOp operator = expression.getOperator();
        JavaccToken token = expression.getLeftOperand().getLastToken().getNext();
        JavaccToken rightFirst = expression.getRightOperand().getFirstToken();
        while (token != null && token != rightFirst) {
            if (tokenImageMatches(token, operator)) {
                return token;
            }
            token = token.getNext();
        }
        return null;
    }

    private boolean tokenImageMatches(JavaccToken token, BinaryOp operator) {
        if (token.getImage().equals(operator.getToken())) {
            return true;
        }
        return (operator == BinaryOp.RIGHT_SHIFT
                && token.kind == JavaTokenKinds.RSIGNEDSHIFT)
                || (operator == BinaryOp.UNSIGNED_RIGHT_SHIFT
                && token.kind == JavaTokenKinds.RUNSIGNEDSHIFT);
    }

    private JavaccToken findAssignmentOperatorToken(
            ASTAssignmentExpression expression) {
        ASTExpression left = (ASTExpression) expression.getChild(0);
        ASTExpression right = (ASTExpression) expression.getChild(1);
        String operatorImage = expression.getOperator().getToken();
        JavaccToken token = left.getLastToken().getNext();
        JavaccToken rightFirst = right.getFirstToken();
        while (token != null && token != rightFirst) {
            if (token.getImage().equals(operatorImage)) {
                return token;
            }
            token = token.getNext();
        }
        return null;
    }
}
