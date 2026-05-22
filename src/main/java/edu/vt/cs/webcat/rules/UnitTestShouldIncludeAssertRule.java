package edu.vt.cs.webcat.rules;

import edu.vt.cs.webcat.rules.utils.TestFrameworksUtil;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Based on the official net.sourceforge.pmd.lang.java.rule.bestpractices.UnitTestShouldIncludeAssert rule.
 * This custom version makes use of our own Util class, since we use a custom TestCase class.
 * <p>
 * This version also recognizes assertions that are made through helper methods in the same enclosing type.
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
        ASTBlock body = method.getBody();

        if (body == null
                || !TestFrameworksUtil.isTestMethod(method)
                || TestFrameworksUtil.isExpectAnnotated(method)) {
            return data;
        }

        ASTTypeDeclaration enclosingType = method.getEnclosingType();

        boolean usesSoftAssertExtension = usesSoftAssertExtension(enclosingType);
        Set<String> extraAsserts = getProperty(EXTRA_ASSERT_METHOD_NAMES);

        Predicate<ASTMethodCall> isAssertCall = TestFrameworksUtil::isProbableAssertCall;
        if (usesSoftAssertExtension) {
            isAssertCall = isAssertCall.or(TestFrameworksUtil::isSoftAssert);
        }

        Map<String, List<ASTMethodDeclaration>> sameTypeMethodsByName =
                collectSameTypeMethodsByName(enclosingType);

        boolean hasAssert = hasAssertDirectlyOrViaHelper(
                method,
                sameTypeMethodsByName,
                isAssertCall,
                extraAsserts,
                new HashSet<>(),
                new HashMap<>()
        );

        if (!hasAssert) {
            asCtx(data).addViolation(method);
        }

        return data;
    }

    private boolean hasAssertDirectlyOrViaHelper(
            ASTMethodDeclaration method,
            Map<String, List<ASTMethodDeclaration>> sameTypeMethodsByName,
            Predicate<ASTMethodCall> isAssertCall,
            Set<String> extraAsserts,
            Set<ASTMethodDeclaration> visiting,
            Map<ASTMethodDeclaration, Boolean> memo
    ) {
        Boolean cached = memo.get(method);
        if (cached != null) {
            return cached;
        }

        if (!visiting.add(method)) {
            return false;
        }

        ASTBlock body = method.getBody();
        if (body == null) {
            visiting.remove(method);
            memo.put(method, false);
            return false;
        }

        boolean result = body.descendants(ASTMethodCall.class).any(call ->
                isDirectAssertCall(call, isAssertCall, extraAsserts)
                        || callsHelperWithAssert(
                        call,
                        sameTypeMethodsByName,
                        isAssertCall,
                        extraAsserts,
                        visiting,
                        memo
                )
        );

        visiting.remove(method);
        memo.put(method, result);
        return result;
    }

    private boolean isDirectAssertCall(
            ASTMethodCall call,
            Predicate<ASTMethodCall> isAssertCall,
            Set<String> extraAsserts
    ) {
        return isAssertCall.test(call)
                || extraAsserts.contains(call.getMethodName());
    }

    private boolean callsHelperWithAssert(
            ASTMethodCall call,
            Map<String, List<ASTMethodDeclaration>> sameTypeMethodsByName,
            Predicate<ASTMethodCall> isAssertCall,
            Set<String> extraAsserts,
            Set<ASTMethodDeclaration> visiting,
            Map<ASTMethodDeclaration, Boolean> memo
    ) {
        List<ASTMethodDeclaration> candidateHelpers = sameTypeMethodsByName.get(call.getMethodName());
        if (candidateHelpers == null || candidateHelpers.isEmpty()) {
            return false;
        }

        int argumentCount = getArgumentCount(call);

        return candidateHelpers.stream()
                .filter(helper -> helper.getArity() == argumentCount)
                .anyMatch(helper -> hasAssertDirectlyOrViaHelper(
                        helper,
                        sameTypeMethodsByName,
                        isAssertCall,
                        extraAsserts,
                        visiting,
                        memo
                ));
    }

    private int getArgumentCount(ASTMethodCall call) {
        ASTArgumentList arguments = call.getArguments();
        return arguments == null ? 0 : arguments.getNumChildren();
    }

    private Map<String, List<ASTMethodDeclaration>> collectSameTypeMethodsByName(
            ASTTypeDeclaration typeDeclaration
    ) {
        Map<String, List<ASTMethodDeclaration>> methodsByName = new HashMap<>();

        if (typeDeclaration == null) {
            return methodsByName;
        }

        typeDeclaration.descendants(ASTMethodDeclaration.class)
                .filter(candidate -> candidate.getEnclosingType() == typeDeclaration)
                .forEach(candidate ->
                        methodsByName
                                .computeIfAbsent(candidate.getName(), ignored -> new ArrayList<>())
                                .add(candidate)
                );

        return methodsByName;
    }

    private boolean usesSoftAssertExtension(ASTTypeDeclaration typeDeclaration) {
        if (typeDeclaration == null) {
            return false;
        }

        ASTTypeDeclaration enclosingType = typeDeclaration.getEnclosingType();

        return hasSoftAssertExtensionOn(typeDeclaration)
                || (TestFrameworksUtil.isJUnit5NestedClass(typeDeclaration)
                && usesSoftAssertExtension(enclosingType));
    }

    private boolean hasSoftAssertExtensionOn(ASTTypeDeclaration typeDeclaration) {
        if (typeDeclaration == null) {
            return false;
        }

        ASTAnnotation extendWith = typeDeclaration.getAnnotation("org.junit.jupiter.api.extension.ExtendWith");

        return extendWith != null && extendWith.getFlatValue("value")
                .filterIs(ASTClassLiteral.class)
                .map(ASTClassLiteral::getTypeNode)
                .any(c -> TypeTestUtil.isA("org.assertj.core.api.junit.jupiter.SoftAssertionsExtension", c));
    }
}