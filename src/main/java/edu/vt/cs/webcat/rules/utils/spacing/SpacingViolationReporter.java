package edu.vt.cs.webcat.rules.utils.spacing;

import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.HashSet;
import java.util.Set;

import static edu.vt.cs.webcat.rules.utils.RuleMessageUtils.escapeLiteral;

/** De-duplicates spacing violations produced by one rule for one source file. */
public final class SpacingViolationReporter {

    private final ASTCompilationUnit root;
    private final RuleContext context;
    private final Set<Integer> reportedPositions = new HashSet<>();

    public SpacingViolationReporter(ASTCompilationUnit root, RuleContext context) {
        this.root = root;
        this.context = context;
    }

    public void report(JavaccToken token, String message) {
        if (!reportedPositions.add(SpacingUtils.startOf(token))) {
            return;
        }
        context.addViolationWithPosition(root, token, escapeLiteral(message));
    }
}
