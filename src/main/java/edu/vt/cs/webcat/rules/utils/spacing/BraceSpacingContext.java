package edu.vt.cs.webcat.rules.utils.spacing;

import edu.vt.cs.webcat.rules.BraceSpacingRule;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.java.ast.ASTArrayInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;

import java.util.HashSet;
import java.util.Set;

/** AST-derived token roles needed by {@link BraceSpacingRule}. */
public final class BraceSpacingContext {

    private final Set<Integer> arrayInitializerBraceOffsets = new HashSet<>();

    private BraceSpacingContext(ASTCompilationUnit root) {
        for (ASTArrayInitializer initializer
                : root.descendants(ASTArrayInitializer.class)) {
            arrayInitializerBraceOffsets.add(
                    SpacingUtils.startOf(initializer.getFirstToken()));
            arrayInitializerBraceOffsets.add(
                    SpacingUtils.startOf(initializer.getLastToken()));
        }
    }

    public static BraceSpacingContext from(ASTCompilationUnit root) {
        return new BraceSpacingContext(root);
    }

    public boolean isArrayInitializerBrace(JavaccToken token) {
        return arrayInitializerBraceOffsets.contains(SpacingUtils.startOf(token));
    }
}
