package edu.vt.cs.webcat.rules;


import edu.vt.cs.webcat.rules.utils.TestFrameworksUtil;
import net.sourceforge.pmd.lang.java.ast.ASTClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

import java.util.regex.Pattern;

/**
 * Based on the official net.sourceforge.pmd.lang.java.rule.errorprone.TestClassWithoutTestCases rule.
 * This custom version makes use of our own Util class, since we use a custom TestCase class.
 */
public class TestClassWithoutTestCasesRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<Pattern> TEST_CLASS_PATTERN = PropertyFactory.regexProperty("testClassPattern")
            .defaultValue("^(?:.*\\.)?Test[^\\.]*$|^(?:.*\\.)?.*Tests?$|^(?:.*\\.)?.*TestCase$")
            .desc("Test class name pattern to identify test classes by their fully qualified name. "
                    + "An empty pattern disables test class detection by name. Since PMD 6.51.0.")
            .build();

    public TestClassWithoutTestCasesRule() {
        super(ASTClassDeclaration.class);
        definePropertyDescriptor(TEST_CLASS_PATTERN);
    }

    @Override
    public Object visit(ASTClassDeclaration node, Object data) {
        if (TestFrameworksUtil.isJUnit3Class(node) || TestFrameworksUtil.isJUnit5NestedClass(node) || isTestClassByPattern(node)) {
            boolean hasTests =
                    node.getDeclarations(ASTMethodDeclaration.class)
                            .any(TestFrameworksUtil::isTestMethod);
            boolean hasNestedTestClasses = node.getDeclarations(ASTTypeDeclaration.class)
                    .any(TestFrameworksUtil::isJUnit5NestedClass);

            if (!hasTests && !hasNestedTestClasses) {
                asCtx(data).addViolation(node, node.getSimpleName());
            }
        }
        return null;
    }

    private boolean isTestClassByPattern(ASTClassDeclaration node) {
        Pattern testClassPattern = getProperty(TEST_CLASS_PATTERN);
        if (testClassPattern.pattern().isEmpty()) {
            // detection by pattern is disabled
            return false;
        }

        if (node.isAbstract() || node.isInterface()) {
            return false;
        }

        String fullName = node.getCanonicalName();
        return fullName != null && testClassPattern.matcher(fullName).find();
    }
}