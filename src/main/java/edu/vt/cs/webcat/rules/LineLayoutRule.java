package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static edu.vt.cs.webcat.rules.utils.RuleMessageUtils.escapeLiteral;

public class LineLayoutRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE =
            PropertyFactory.stringProperty("message")
                    .desc("Message when multiple statements appear on one line.")
                    .defaultValue("Only one statement is allowed per line.")
                    .build();
    public LineLayoutRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(MESSAGE);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext context = asCtx(data);
        String message = getProperty(MESSAGE);
        Map<Integer, List<JavaNode>> lineToStatements = new LinkedHashMap<>();
        collectStatementLines(node, lineToStatements);

        for (Map.Entry<Integer, List<JavaNode>> entry : lineToStatements.entrySet()) {
            if (entry.getValue().size() > 1) {
                JavaNode first = entry.getValue().get(0);
                context.addViolationWithPosition(
                        node, first.getFirstToken(), escapeLiteral(message));
            }
        }
        return data;
    }

    private void collectStatementLines(ASTCompilationUnit root,
                                       Map<Integer, List<JavaNode>> lineToStatements) {
        for (ASTStatement stmt : root.descendants(ASTStatement.class)) {
            if (isSkippableNode(stmt)) {
                continue;
            }

            int startLine = stmt.getFirstToken().getReportLocation().getStartLine();
            lineToStatements.computeIfAbsent(startLine, k -> new ArrayList<>()).add(stmt);
        }
    }

    private boolean isSkippableNode(ASTStatement stmt) {
        if (stmt instanceof ASTBlock) {
            return true;
        }
        if (stmt instanceof ASTSwitchStatement) {
            return false;
        }

        if (isForHeaderChild(stmt)) {
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

    private boolean isForHeaderChild(ASTStatement stmt) {
        if (stmt.getParent() instanceof ASTForInit
                || stmt.getParent() instanceof ASTForUpdate) {
            return true;
        }

        if (stmt.getParent() instanceof ASTForStatement) {
            ASTForStatement forStmt = (ASTForStatement) stmt.getParent();
            ASTStatement body = forStmt.getBody();
            return body != null && stmt != body;
        }
        return false;
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
}
