package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.TestFrameworksUtil;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Based on the official net.sourceforge.pmd.lang.java.rule.bestpractices.UnitTestShouldIncludeAssert rule.
 * This custom version makes use of our own Util class, since we use a custom TestCase class.
 */
public class UnitTestShouldIncludeAssertRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<Set<String>> EXTRA_ASSERT_METHOD_NAMES =
            PropertyFactory.stringProperty("extraAssertMethodNames")
                    .desc("Extra valid assertion methods names")
                    .map(Collectors.toSet())
                    .emptyDefaultValue()
                    .build();

    public UnitTestShouldIncludeAssertRule() {
        super(ASTMethodDeclaration.class);
        definePropertyDescriptor(EXTRA_ASSERT_METHOD_NAMES);
    }

    @Override
    public Object visit(ASTMethodDeclaration method, Object data) {
        boolean usesSoftAssertExtension = usesSoftAssertExtension(method.getEnclosingType());
        Set<String> extraAsserts = getProperty(EXTRA_ASSERT_METHOD_NAMES);
        Predicate<ASTMethodCall> isAssertCall = TestFrameworksUtil::isProbableAssertCall;
        if (usesSoftAssertExtension) {
            isAssertCall = isAssertCall.or(TestFrameworksUtil::isSoftAssert);
        }

        ASTBlock body = method.getBody();
        if (body != null
                && TestFrameworksUtil.isTestMethod(method)
                && !TestFrameworksUtil.isExpectAnnotated(method)
                && body.descendants(ASTMethodCall.class)
                .none(isAssertCall
                        .or(call -> extraAsserts.contains(call.getMethodName())))) {
            asCtx(data).addViolation(method);
        }
        return data;
    }

    private boolean usesSoftAssertExtension(ASTTypeDeclaration typeDeclaration) {
        return hasSoftAssertExtensionOn(typeDeclaration)
                || (TestFrameworksUtil.isJUnit5NestedClass(typeDeclaration)
                && usesSoftAssertExtension(typeDeclaration.getEnclosingType()));
    }

    private boolean hasSoftAssertExtensionOn(ASTTypeDeclaration typeDeclaration) {
        ASTAnnotation extendWith = typeDeclaration.getAnnotation("org.junit.jupiter.api.extension.ExtendWith");
        return extendWith != null && extendWith.getFlatValue("value")
                .filterIs(ASTClassLiteral.class)
                .map(ASTClassLiteral::getTypeNode)
                .any(c -> TypeTestUtil.isA("org.assertj.core.api.junit.jupiter.SoftAssertionsExtension", c));
    }
}