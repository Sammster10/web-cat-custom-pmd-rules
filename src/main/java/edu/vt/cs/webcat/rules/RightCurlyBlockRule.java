package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.document.TextRegion;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTCatchClause;
import net.sourceforge.pmd.lang.java.ast.ASTDoStatement;
import net.sourceforge.pmd.lang.java.ast.ASTFinallyClause;
import net.sourceforge.pmd.lang.java.ast.ASTIfStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTryStatement;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

public class RightCurlyBlockRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE =
            PropertyFactory.stringProperty("message")
                    .desc("Message when a right curly brace is not alone on its line.")
                    .defaultValue("Right curly brace must be alone on its line.")
                    .build();

    private static final PropertyDescriptor<Boolean> ALLOW_SAME_LINE_BRACE_TRANSITIONS =
            PropertyFactory.booleanProperty("allowSameLineBraceTransitions")
                    .desc("Allow a closing brace to share a line with else, catch, finally, or do-while.")
                    .defaultValue(false)
                    .build();

    public RightCurlyBlockRule() {
        super(ASTBlock.class);
        definePropertyDescriptor(MESSAGE);
        definePropertyDescriptor(ALLOW_SAME_LINE_BRACE_TRANSITIONS);
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
        String lineText = stripComments(lineChars.toString()).trim();

        if (!lineText.equals("}") && !isAllowedSameLineBraceTransition(node, brace)) {
            RuleContext context = asCtx(data);
            context.addViolationWithPosition(
                    node.getRoot(),
                    brace,
                    getProperty(MESSAGE)
            );
        }
        return data;
    }

    private boolean isAllowedSameLineBraceTransition(ASTBlock block, JavaccToken brace) {
        if (!getProperty(ALLOW_SAME_LINE_BRACE_TRANSITIONS)) {
            return false;
        }

        JavaccToken next = brace.getNext();
        if (next != null && sameLine(brace, next)
                && isAllowedContinuationKeyword(block, next)) {
            return true;
        }

        return isCompactContinuationBlock(block, brace);
    }

    private static boolean isAllowedContinuationKeyword(ASTBlock block, JavaccToken keyword) {
        if (keyword.kind == JavaTokenKinds.ELSE) {
            return block.getParent() instanceof ASTIfStatement
                    && ((ASTIfStatement) block.getParent()).getThenBranch() == block;
        }

        if (keyword.kind == JavaTokenKinds.CATCH || keyword.kind == JavaTokenKinds.FINALLY) {
            return isTryOrCatchBody(block);
        }

        return keyword.kind == JavaTokenKinds.WHILE
                && block.getParent() instanceof ASTDoStatement
                && ((ASTDoStatement) block.getParent()).getBody() == block;
    }

    private static boolean isTryOrCatchBody(ASTBlock block) {
        if (block.getParent() instanceof ASTTryStatement) {
            return ((ASTTryStatement) block.getParent()).getBody() == block;
        }
        return block.getParent() instanceof ASTCatchClause
                && ((ASTCatchClause) block.getParent()).getBody() == block;
    }

    private static boolean isCompactContinuationBlock(ASTBlock block, JavaccToken brace) {
        JavaccToken previousBrace;
        JavaccToken keyword;

        if (block.getParent() instanceof ASTIfStatement) {
            ASTIfStatement ifStatement = (ASTIfStatement) block.getParent();
            if (ifStatement.getElseBranch() != block) {
                return false;
            }
            previousBrace = ifStatement.getThenBranch().getLastToken();
            keyword = previousBrace.getNext();
        } else if (block.getParent() instanceof ASTCatchClause) {
            ASTCatchClause catchClause = (ASTCatchClause) block.getParent();
            keyword = catchClause.getFirstToken();
            previousBrace = catchClause.getParent() instanceof ASTTryStatement
                    ? previousToken((ASTTryStatement) catchClause.getParent(), keyword)
                    : null;
        } else if (block.getParent() instanceof ASTFinallyClause) {
            ASTFinallyClause finallyClause = (ASTFinallyClause) block.getParent();
            keyword = finallyClause.getFirstToken();
            previousBrace = finallyClause.getParent() instanceof ASTTryStatement
                    ? previousToken((ASTTryStatement) finallyClause.getParent(), keyword)
                    : null;
        } else {
            return false;
        }

        return previousBrace != null && previousBrace.kind == JavaTokenKinds.RBRACE
                && isBlockContinuationKeyword(keyword)
                && sameLine(previousBrace, keyword) && sameLine(keyword, brace);
    }

    private static JavaccToken previousToken(ASTTryStatement tryStatement, JavaccToken target) {
        JavaccToken previous = null;
        for (JavaccToken token : tryStatement.tokens()) {
            if (token == target) {
                return previous;
            }
            previous = token;
        }
        return null;
    }

    private static boolean isBlockContinuationKeyword(JavaccToken token) {
        return token != null && (token.kind == JavaTokenKinds.ELSE
                || token.kind == JavaTokenKinds.CATCH
                || token.kind == JavaTokenKinds.FINALLY);
    }

    private static boolean sameLine(JavaccToken first, JavaccToken second) {
        return first.getReportLocation().getStartLine()
                == second.getReportLocation().getStartLine();
    }

    private static String stripComments(String line) {
        StringBuilder result = new StringBuilder(line.length());

        boolean inBlockComment = false;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);

            if (inBlockComment) {
                if (current == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (current == '/' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);

                if (next == '/') {
                    break;
                }

                if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            result.append(current);
        }

        return result.toString();
    }

}
