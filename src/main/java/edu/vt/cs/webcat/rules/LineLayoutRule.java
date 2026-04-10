package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.GenericToken;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.TextDocument;
import net.sourceforge.pmd.lang.document.TextRegion;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.*;

public class LineLayoutRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> RCURLY_MESSAGE =
            PropertyFactory.stringProperty("rcurlyMessage")
                    .desc("Message reported when a right curly brace is not alone on its line.")
                    .defaultValue("Right curly brace must be alone on its line.")
                    .build();

    private static final PropertyDescriptor<String> ONE_STMT_MESSAGE =
            PropertyFactory.stringProperty("oneStatementMessage")
                    .desc("Message reported when multiple statements occupy the same line.")
                    .defaultValue("Only one statement is allowed per line.")
                    .build();

    private static final PropertyDescriptor<Boolean> CHECK_RCURLY =
            PropertyFactory.booleanProperty("checkRCurly")
                    .desc("Enable the right-curly-brace-alone-on-line check.")
                    .defaultValue(true)
                    .build();

    private static final PropertyDescriptor<Boolean> CHECK_ONE_STMT =
            PropertyFactory.booleanProperty("checkOneStatement")
                    .desc("Enable the one-statement-per-line check.")
                    .defaultValue(true)
                    .build();

    private final Set<Integer> reportedLines = new HashSet<>();

    public LineLayoutRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(RCURLY_MESSAGE);
        definePropertyDescriptor(ONE_STMT_MESSAGE);
        definePropertyDescriptor(CHECK_RCURLY);
        definePropertyDescriptor(CHECK_ONE_STMT);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        reportedLines.clear();

        if (getProperty(CHECK_RCURLY)) {
            checkRightCurlyAlone(node, ctx);
        }
        if (getProperty(CHECK_ONE_STMT)) {
            checkOneStatementPerLine(node, ctx);
        }

        return data;
    }

    private void checkRightCurlyAlone(ASTCompilationUnit node, RuleContext ctx) {
        String message = getProperty(RCURLY_MESSAGE);
        List<JavaccToken> tokens = collectTokens(node);
        TextDocument textDoc = node.getTextDocument();

        for (int i = 0; i < tokens.size(); i++) {
            JavaccToken token = tokens.get(i);
            if (token.kind != JavaTokenKinds.RBRACE) {
                continue;
            }

            int braceLine = token.getReportLocation().getStartLine();
            boolean hasOtherContent = false;

            for (int j = i - 1; j >= 0; j--) {
                JavaccToken prev = tokens.get(j);
                if (lineOf(prev) != braceLine) {
                    break;
                }
                hasOtherContent = true;
                break;
            }

            if (!hasOtherContent) {
                for (int j = i + 1; j < tokens.size(); j++) {
                    JavaccToken next = tokens.get(j);
                    if (next.isEof() || lineOf(next) != braceLine) {
                        break;
                    }
                    hasOtherContent = true;
                    break;
                }
            }

            if (!hasOtherContent) {
                hasOtherContent = hasCommentOnSameLine(token, tokens, i, textDoc);
            }

            if (hasOtherContent) {
                reportOnce(ctx, node, token, braceLine, message);
            }
        }
    }

    private boolean hasCommentOnSameLine(JavaccToken braceToken, List<JavaccToken> tokens,
                                         int braceIndex, TextDocument textDoc) {
        int braceEnd = braceToken.getRegion().getEndOffset();
        int searchEnd;

        if (braceIndex + 1 < tokens.size()) {
            JavaccToken next = tokens.get(braceIndex + 1);
            searchEnd = next.getRegion().getStartOffset();
        } else {
            searchEnd = textDoc.getLength();
        }

        if (searchEnd <= braceEnd) {
            return false;
        }

        Chars gap = textDoc.sliceOriginalText(TextRegion.fromBothOffsets(braceEnd, searchEnd));
        String gapStr = gap.toString();

        int newlinePos = gapStr.indexOf('\n');
        String sameLineGap = newlinePos >= 0 ? gapStr.substring(0, newlinePos) : gapStr;

        return sameLineGap.contains("//") || sameLineGap.contains("/*");
    }

    private void checkOneStatementPerLine(ASTCompilationUnit node, RuleContext ctx) {
        String message = getProperty(ONE_STMT_MESSAGE);
        Set<Integer> forHeaderLines = collectForHeaderLines(node);

        Map<Integer, List<JavaNode>> lineToStatements = new LinkedHashMap<>();

        collectStatementLines(node, lineToStatements, forHeaderLines);

        for (Map.Entry<Integer, List<JavaNode>> entry : lineToStatements.entrySet()) {
            if (entry.getValue().size() > 1) {
                JavaNode first = entry.getValue().get(0);
                int line = entry.getKey();
                reportOnce(ctx, node, first.getFirstToken(), line, message);
            }
        }
    }

    private void collectStatementLines(ASTCompilationUnit root,
                                       Map<Integer, List<JavaNode>> lineToStatements,
                                       Set<Integer> forHeaderLines) {
        for (ASTStatement stmt : root.descendants(ASTStatement.class)) {
            if (isSkippableNode(stmt, forHeaderLines)) {
                continue;
            }

            int startLine = stmt.getFirstToken().getReportLocation().getStartLine();
            lineToStatements.computeIfAbsent(startLine, k -> new ArrayList<>()).add(stmt);

            if (isControlFlowWithSameLineBody(stmt)) {
                JavaNode body = getControlFlowBody(stmt);
                if (body != null && !(body instanceof ASTBlock)) {
                    int bodyLine = body.getFirstToken().getReportLocation().getStartLine();
                    if (bodyLine == startLine) {
                        lineToStatements.computeIfAbsent(bodyLine, k -> new ArrayList<>()).add(body);
                    }
                }
            }
        }
    }

    private boolean isSkippableNode(ASTStatement stmt, Set<Integer> forHeaderLines) {
        if (stmt instanceof ASTBlock) {
            return true;
        }
        if (stmt instanceof ASTSwitchStatement) {
            return false;
        }

        if (isForHeaderChild(stmt, forHeaderLines)) {
            return true;
        }

        if (isForeachHeaderChild(stmt)) {
            return true;
        }

        return false;
    }

    private boolean isForHeaderChild(ASTStatement stmt, Set<Integer> forHeaderLines) {
        if (stmt.getParent() instanceof ASTForInit
                || stmt.getParent() instanceof ASTForUpdate) {
            return true;
        }

        if (stmt.getParent() instanceof ASTForStatement) {
            ASTForStatement forStmt = (ASTForStatement) stmt.getParent();
            int forLine = forStmt.getFirstToken().getReportLocation().getStartLine();
            if (forHeaderLines.contains(forLine)) {
                ASTStatement body = getForBody(forStmt);
                return body != null && stmt != body;
            }
        }
        return false;
    }

    private ASTStatement getForBody(ASTForStatement forStmt) {
        return forStmt.getBody();
    }

    private boolean isForeachHeaderChild(ASTStatement stmt) {
        if (!(stmt.getParent() instanceof ASTForeachStatement)) {
            return false;
        }
        ASTForeachStatement forEach = (ASTForeachStatement) stmt.getParent();
        return stmt != forEach.getBody();
    }

    private Set<Integer> collectForHeaderLines(ASTCompilationUnit root) {
        Set<Integer> lines = new HashSet<>();
        for (ASTForStatement forStmt : root.descendants(ASTForStatement.class)) {
            lines.add(forStmt.getFirstToken().getReportLocation().getStartLine());
        }
        return lines;
    }

    private boolean isControlFlowWithSameLineBody(ASTStatement stmt) {
        return stmt instanceof ASTIfStatement
                || stmt instanceof ASTForStatement
                || stmt instanceof ASTForeachStatement
                || stmt instanceof ASTWhileStatement
                || stmt instanceof ASTDoStatement;
    }

    private JavaNode getControlFlowBody(ASTStatement stmt) {
        if (stmt instanceof ASTIfStatement) {
            return ((ASTIfStatement) stmt).getThenBranch();
        }
        if (stmt instanceof ASTForStatement) {
            return ((ASTForStatement) stmt).getBody();
        }
        if (stmt instanceof ASTForeachStatement) {
            return ((ASTForeachStatement) stmt).getBody();
        }
        if (stmt instanceof ASTWhileStatement) {
            return ((ASTWhileStatement) stmt).getBody();
        }
        if (stmt instanceof ASTDoStatement) {
            return ((ASTDoStatement) stmt).getBody();
        }
        return null;
    }

    private List<JavaccToken> collectTokens(ASTCompilationUnit node) {
        List<JavaccToken> tokens = new ArrayList<>();
        for (JavaccToken t : GenericToken.range(node.getFirstToken(), node.getLastToken())) {
            tokens.add(t);
        }
        return tokens;
    }

    private int lineOf(JavaccToken token) {
        return token.getReportLocation().getStartLine();
    }

    private void reportOnce(RuleContext ctx, ASTCompilationUnit root,
                            JavaccToken token, int line, String message) {
        int key = Objects.hash(line, message);
        if (reportedLines.contains(key)) {
            return;
        }
        reportedLines.add(key);
        String escaped = message.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
        ctx.addViolationWithPosition(root, token, escaped);
    }
}
