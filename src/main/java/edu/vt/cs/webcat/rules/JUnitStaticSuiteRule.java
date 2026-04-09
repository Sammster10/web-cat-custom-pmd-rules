package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.TestFrameworksUtil;
import net.sourceforge.pmd.lang.java.ast.ASTClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;


/**
 * Based on the official net.sourceforge.pmd.lang.java.rule.errorprone.JUnitStaticSuite rule.
 * This custom version makes use of our own Util class, since we use a custom TestCase class.
 */
public class JUnitStaticSuiteRule extends AbstractJavaRulechainRule {

    public JUnitStaticSuiteRule() {
        super(ASTClassDeclaration.class);
    }

    @Override
    public Object visit(ASTClassDeclaration node, Object data) {
        if (TestFrameworksUtil.isJUnit3Class(node)) {
            ASTMethodDeclaration suiteMethod = node.getDeclarations(ASTMethodDeclaration.class)
                    .filter(it -> "suite".equals(it.getName()) && it.getArity() == 0)
                    .first();
            if (suiteMethod != null
                    && (suiteMethod.getVisibility() != Visibility.V_PUBLIC || !suiteMethod.isStatic())) {
                asCtx(data).addViolation(suiteMethod);
            }
        }
        return null;
    }
}