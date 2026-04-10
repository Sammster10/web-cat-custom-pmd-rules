package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictJavadocRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new StrictJavadocRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("StrictJavadoc violation");
    }

    private List<RuleViolation> runRule(String code) {
        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                JavaLanguageModule.getInstance().getVersion("17"));
        RuleSet ruleSet = RuleSet.forSingleRule(rule);
        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
            analysis.addRuleSet(ruleSet);
            analysis.files().addFile(
                    TextFile.forCharSeq(code,
                            FileId.fromPathLikeString("Test.java"),
                            JavaLanguageModule.getInstance().getVersion("17")));
            Report report = analysis.performAnalysisAndCollectReport();
            return report.getViolations();
        }
    }

    private void assertNoViolations(String code) {
        List<RuleViolation> violations = runRule(code);
        assertTrue(violations.isEmpty(),
                String.format("Expected no violations but found %d: %s",
                        violations.size(), violations));
    }

    private void assertHasViolation(String code, String messageFragment) {
        List<RuleViolation> violations = runRule(code);
        boolean found = violations.stream()
                .anyMatch(v -> v.getDescription().contains(messageFragment));
        assertTrue(found,
                String.format("Expected violation containing '%s' but found: %s",
                        messageFragment, violations));
    }

    private void assertViolationCount(String code, int expected) {
        List<RuleViolation> violations = runRule(code);
        assertEquals(expected, violations.size(),
                String.format("Expected %d violations but found %d: %s",
                        expected, violations.size(), violations));
    }

    private static final String VALID_CLASS_JAVADOC = "/**\n"
            + " * A class.\n"
            + " * @author John\n"
            + " * @version 1.0\n"
            + " */\n"
            + "";

    private String wrapInClass(String body) {
        return VALID_CLASS_JAVADOC + "class T {\n" + body + "\n}";
    }

    @Nested
    class TypeDeclarations {
        @Test
        void validClassJavadoc() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void missingClassJavadoc() {
            assertHasViolation("class T { }", "must have Javadoc");
        }

        @Test
        void missingAuthorTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "must declare @author");
        }

        @Test
        void missingVersionTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "must declare @version");
        }

        @Test
        void duplicateAuthorTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @author Jane\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "duplicate @author");
        }

        @Test
        void duplicateVersionTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " * @version 2.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "duplicate @version");
        }

        @Test
        void emptyAuthorTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "empty @author");
        }

        @Test
        void emptyVersionTag() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "empty @version");
        }

        @Test
        void returnTagOnClassForbidden() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " * @return nothing\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "unused @return");
        }

        @Test
        void throwsTagOnClassForbidden() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " * @throws Exception\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "unused @throws");
        }

        @Test
        void validEnumJavadoc() {
            String code = "/**\n"
                    + " * An enum.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "enum Color { RED }";
            assertNoViolations(code);
        }

        @Test
        void validInterfaceJavadoc() {
            String code = "/**\n"
                    + " * An interface.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "interface I { }";
            assertNoViolations(code);
        }

        @Test
        void validAnnotationJavadoc() {
            String code = "/**\n"
                    + " * An annotation.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "@interface A { }";
            assertNoViolations(code);
        }

        @Test
        void validRecordJavadoc() {
            String code = "/**\n"
                    + " * A record.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "record R() { }";
            assertNoViolations(code);
        }

        @Test
        void privateClassNotChecked() {
            String code = VALID_CLASS_JAVADOC
                    + "class Outer {\n"
                    + "    private class Inner { }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class TypeParameters {
        @Test
        void validGenericClassParam() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @param <T> the type\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class Box<T> { }";
            assertNoViolations(code);
        }

        @Test
        void missingGenericClassParam() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class Box<T> { }";
            assertHasViolation(code, "missing @param for type parameter");
        }

        @Test
        void duplicateTypeParam() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @param <T> first\n"
                    + " * @param <T> second\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class Box<T> { }";
            assertHasViolation(code, "duplicate @param for type parameter");
        }

        @Test
        void unknownTypeParam() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @param <X> unknown\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "unknown type parameter");
        }

        @Test
        void invalidParamTargetOnType() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @param notAType desc\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "invalid @param target");
        }
    }

    @Nested
    class MethodJavadoc {
        @Test
        void validVoidMethod() {
            String code = wrapInClass(
                    "    /**\n     * Does something.\n     */\n    void doIt() { }");
            assertNoViolations(code);
        }

        @Test
        void missingMethodJavadoc() {
            String code = wrapInClass("    void doIt() { }");
            assertHasViolation(code, "must have Javadoc");
        }

        @Test
        void validNonVoidMethodWithReturn() {
            String code = wrapInClass(
                    "    /**\n     * Gets value.\n     * @return the value\n     */\n    int getValue() { return 0; }");
            assertNoViolations(code);
        }

        @Test
        void missingReturnOnNonVoidMethod() {
            String code = wrapInClass(
                    "    /**\n     * Gets value.\n     */\n    int getValue() { return 0; }");
            assertHasViolation(code, "must declare @return");
        }

        @Test
        void forbiddenReturnOnVoidMethod() {
            String code = wrapInClass(
                    "    /**\n     * Does something.\n     * @return nothing\n     */\n    void doIt() { }");
            assertHasViolation(code, "must not declare @return");
        }

        @Test
        void duplicateReturnTag() {
            String code = wrapInClass(
                    "    /**\n     * Gets value.\n     * @return first\n     * @return second\n     */\n    int getValue() { return 0; }");
            assertHasViolation(code, "duplicate @return");
        }

        @Test
        void validMethodWithParam() {
            String code = wrapInClass(
                    "    /**\n     * Sets value.\n     * @param x the value\n     */\n    void set(int x) { }");
            assertNoViolations(code);
        }

        @Test
        void missingParamTag() {
            String code = wrapInClass(
                    "    /**\n     * Sets value.\n     */\n    void set(int x) { }");
            assertHasViolation(code, "missing @param");
        }

        @Test
        void duplicateParamTag() {
            String code = wrapInClass(
                    "    /**\n     * Sets value.\n     * @param x first\n     * @param x second\n     */\n    void set(int x) { }");
            assertHasViolation(code, "duplicate @param");
        }

        @Test
        void unknownParamTag() {
            String code = wrapInClass(
                    "    /**\n     * Does something.\n     * @param y unknown\n     */\n    void doIt() { }");
            assertHasViolation(code, "unknown parameter");
        }

        @Test
        void malformedParamTag() {
            String code = wrapInClass(
                    "    /**\n     * Does something.\n     * @param\n     */\n    void doIt() { }");
            assertHasViolation(code, "malformed @param");
        }

        @Test
        void multipleParams() {
            String code = wrapInClass(
                    "    /**\n     * Adds.\n     * @param a first\n     * @param b second\n     * @return result\n     */\n    int add(int a, int b) { return a + b; }");
            assertNoViolations(code);
        }

        @Test
        void methodWithGenericTypeParam() {
            String code = wrapInClass(
                    "    /**\n     * Identity.\n     * @param <E> element type\n     * @param x the element\n     * @return the element\n     */\n    <E> E id(E x) { return x; }");
            assertNoViolations(code);
        }

        @Test
        void missingMethodTypeParam() {
            String code = wrapInClass(
                    "    /**\n     * Identity.\n     * @param x the element\n     * @return the element\n     */\n    <E> E id(E x) { return x; }");
            assertHasViolation(code, "missing @param for type parameter");
        }

        @Test
        void privateMethodNotChecked() {
            String code = wrapInClass("    private void helper() { }");
            assertNoViolations(code);
        }
    }

    @Nested
    class ConstructorJavadoc {
        @Test
        void validConstructor() {
            String code = wrapInClass(
                    "    /**\n     * Creates T.\n     */\n    T() { }");
            assertNoViolations(code);
        }

        @Test
        void missingConstructorJavadoc() {
            String code = wrapInClass("    T() { }");
            assertHasViolation(code, "must have Javadoc");
        }

        @Test
        void constructorWithParam() {
            String code = wrapInClass(
                    "    /**\n     * Creates T.\n     * @param x the value\n     */\n    T(int x) { }");
            assertNoViolations(code);
        }

        @Test
        void constructorMissingParam() {
            String code = wrapInClass(
                    "    /**\n     * Creates T.\n     */\n    T(int x) { }");
            assertHasViolation(code, "missing @param");
        }

        @Test
        void constructorForbiddenReturn() {
            String code = wrapInClass(
                    "    /**\n     * Creates T.\n     * @return something\n     */\n    T() { }");
            assertHasViolation(code, "must not declare @return");
        }
    }

    @Nested
    class FieldJavadoc {
        @Test
        void validPublicFieldJavadoc() {
            String code = wrapInClass(
                    "    /** The value. */\n    public int value;");
            assertNoViolations(code);
        }

        @Test
        void missingPublicFieldJavadoc() {
            String code = wrapInClass("    public int value;");
            assertHasViolation(code, "must have Javadoc");
        }

        @Test
        void missingPackagePrivateFieldJavadoc() {
            String code = wrapInClass("    int value;");
            assertHasViolation(code, "must have Javadoc");
        }

        @Test
        void privateFieldNotChecked() {
            String code = wrapInClass("    private int value;");
            assertNoViolations(code);
        }

        @Test
        void protectedFieldMissingJavadoc() {
            String code = wrapInClass("    protected int value;");
            assertHasViolation(code, "must have Javadoc");
        }

        @Test
        void validProtectedFieldJavadoc() {
            String code = wrapInClass(
                    "    /** The value. */\n    protected int value;");
            assertNoViolations(code);
        }
    }

    @Nested
    class CompactRecordConstructor {
        @Test
        void validCompactConstructor() {
            String code = "/**\n"
                    + " * A record.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "record R(int x) {\n"
                    + "    /**\n"
                    + "     * Creates R.\n"
                    + "     * @param x the value\n"
                    + "     */\n"
                    + "    R {\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void missingCompactConstructorParam() {
            String code = "/**\n"
                    + " * A record.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "record R(int x) {\n"
                    + "    /**\n"
                    + "     * Creates R.\n"
                    + "     */\n"
                    + "    R {\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "missing @param");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void multipleViolationsOnOneClass() {
            String code = "class T { }";
            List<RuleViolation> violations = runRule(code);
            assertTrue(violations.size() >= 1,
                    String.format("Expected at least 1 violation but found %d", violations.size()));
        }

        @Test
        void emptyJavadocStillNeedsTags() {
            String code = "/**\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "must declare @author");
        }
    }

    @Nested
    class AllowedDuplicateTags {
        @Test
        void duplicateAuthorAllowedWhenConfigured() {
            setRuleProperty(rule, "allowedDuplicateTags", List.of("author"));
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @author Jane\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void duplicateAuthorForbiddenByDefault() {
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @author Jane\n"
                    + " * @version 1.0\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "duplicate @author");
        }

        @Test
        void duplicateVersionAllowedWhenConfigured() {
            setRuleProperty(rule, "allowedDuplicateTags", List.of("version"));
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @version 1.0\n"
                    + " * @version 2.0\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void duplicateReturnAllowedWhenConfigured() {
            setRuleProperty(rule, "allowedDuplicateTags", List.of("return"));
            String code = wrapInClass(
                    "    /**\n     * Gets value.\n     * @return first\n     * @return second\n     */\n    int getValue() { return 0; }");
            assertNoViolations(code);
        }

        @Test
        void duplicateParamAllowedWhenConfigured() {
            setRuleProperty(rule, "allowedDuplicateTags", List.of("param"));
            String code = wrapInClass(
                    "    /**\n     * Sets value.\n     * @param x first\n     * @param x second\n     */\n    void set(int x) { }");
            assertNoViolations(code);
        }

        @Test
        void multipleTagsAllowed() {
            setRuleProperty(rule, "allowedDuplicateTags", List.of("author", "version"));
            String code = "/**\n"
                    + " * A class.\n"
                    + " * @author John\n"
                    + " * @author Jane\n"
                    + " * @version 1.0\n"
                    + " * @version 2.0\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }
    }
}
