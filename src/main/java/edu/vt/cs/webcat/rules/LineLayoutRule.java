package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.*;

public class LineLayoutRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE =
            PropertyFactory.stringProperty("message")
                    .desc("Message when multiple statements appear on one line.")
                    .defaultValue("Only one statement is allowed per line.")
                    .build();
    private final Set<Integer> reportedLines = new HashSet<>();

    public LineLayoutRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(MESSAGE);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        reportedLines.clear();

        checkOneStatementPerLine(node, ctx);

        return data;
    }


    private void checkOneStatementPerLine(ASTCompilationUnit node, RuleContext ctx) {
        String message = getProperty(MESSAGE);
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

        if (isTryWithResourcesChild(stmt)) {
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

    private boolean isTryWithResourcesChild(ASTStatement stmt) {
        for (Node parent = stmt.getParent();
             parent != null; parent = parent.getParent()) {
            if (parent instanceof ASTResourceList) {
                return true;
            }
            if (parent instanceof ASTTryStatement) {
                return false;
            }
        }
        return false;
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