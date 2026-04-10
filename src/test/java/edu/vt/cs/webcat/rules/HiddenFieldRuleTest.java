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

class HiddenFieldRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new HiddenFieldRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("HiddenField violation");
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

    @Nested
    class NoViolations {
        @Test
        void parameterDoesNotMatchField() {
            assertNoViolations(
                    "class T {\n"
                            + "    private int count;\n"
                            + "    void m(int other) { }\n"
                            + "}");
        }

        @Test
        void localVariableDoesNotMatchField() {
            assertNoViolations(
                    "class T {\n"
                            + "    private String name;\n"
                            + "    void m() { int x = 0; }\n"
                            + "}");
        }

        @Test
        void noFieldsInClass() {
            assertNoViolations(
                    "class T {\n"
                            + "    void m(int x) { int y = 0; }\n"
                            + "}");
        }

        @Test
        void emptyMethodBody() {
            assertNoViolations(
                    "class T {\n"
                            + "    private int x;\n"
                            + "    void m() { }\n"
                            + "}");
        }
    }

    @Nested
    class ConstructorExclusion {
        @Test
        void constructorExcludedByDefault() {
            assertNoViolations(
                    "class T {\n"
                            + "    private int x;\n"
                            + "    T(int x) { this.x = x; }\n"
                            + "}");
        }

        @Test
        void constructorCheckedWhenEnabled() {
            setRuleProperty(rule, "checkConstructors", true);
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    T(int x) { this.x = x; }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void constructorLocalVariableCheckedWhenEnabled() {
            setRuleProperty(rule, "checkConstructors", true);
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    T() { int x = 5; this.x = x; }\n"
                            + "}",
                    "Local variable");
        }
    }

    @Nested
    class SetterExclusion {
        @Test
        void setterExcludedByDefault() {
            assertNoViolations(
                    "class T {\n"
                            + "    private int count;\n"
                            + "    void setCount(int count) { this.count = count; }\n"
                            + "}");
        }

        @Test
        void setterCheckedWhenEnabled() {
            setRuleProperty(rule, "checkSetters", true);
            assertHasViolation("class T {\n"
                            + "    private int count;\n"
                            + "    void setCount(int count) { this.count = count; }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void methodNamedSetButNotASetter() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    int setup(int x) { return x; }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void setterWithTwoParamsNotExcluded() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void setX(int x, int y) { this.x = x; }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void setterWithReturnTypeNotExcluded() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    int setX(int x) { this.x = x; return x; }\n"
                            + "}",
                    "Parameter");
        }
    }

    @Nested
    class AbstractMethodExclusion {
        @Test
        void abstractMethodExcludedByDefault() {
            assertNoViolations(
                    "abstract class T {\n"
                            + "    private int x;\n"
                            + "    abstract void m(int x);\n"
                            + "}");
        }

        @Test
        void abstractMethodCheckedWhenEnabled() {
            setRuleProperty(rule, "checkAbstractMethods", true);
            assertHasViolation("abstract class T {\n"
                            + "    private int x;\n"
                            + "    abstract void m(int x);\n"
                            + "}",
                    "Parameter");
        }
    }

    @Nested
    class ParameterShadowing {
        @Test
        void singleParameterMatchesField() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void m(int x) { }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void multipleParametersOneMatches() {
            assertViolationCount("class T {\n"
                    + "    private int x;\n"
                    + "    private int y;\n"
                    + "    void m(int x, int z) { }\n"
                    + "}", 1);
        }

        @Test
        void multipleParametersAllMatch() {
            assertViolationCount("class T {\n"
                    + "    private int x;\n"
                    + "    private int y;\n"
                    + "    void m(int x, int y) { }\n"
                    + "}", 2);
        }
    }

    @Nested
    class LocalVariableShadowing {
        @Test
        void localVariableMatchesField() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void m() { int x = 0; }\n"
                            + "}",
                    "Local variable");
        }

        @Test
        void nestedBlockLocalVariable() {
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void m() {\n"
                            + "        if (true) { int x = 1; }\n"
                            + "    }\n"
                            + "}",
                    "Local variable");
        }

        @Test
        void forLoopVariable() {
            assertHasViolation("class T {\n"
                            + "    private int i;\n"
                            + "    void m() {\n"
                            + "        for (int i = 0; i < 10; i++) { }\n"
                            + "    }\n"
                            + "}",
                    "Local variable");
        }
    }

    @Nested
    class MixedViolations {
        @Test
        void parameterAndLocalVariableBothMatch() {
            assertViolationCount("class T {\n"
                    + "    private int x;\n"
                    + "    private int y;\n"
                    + "    void m(int x) { int y = 0; }\n"
                    + "}", 2);
        }

        @Test
        void multipleMethods() {
            assertViolationCount("class T {\n"
                    + "    private int x;\n"
                    + "    void a(int x) { }\n"
                    + "    void b(int x) { }\n"
                    + "}", 2);
        }

        @Test
        void publicFieldShadowed() {
            assertHasViolation("class T {\n"
                            + "    public int x;\n"
                            + "    void m(int x) { }\n"
                            + "}",
                    "Parameter");
        }

        @Test
        void staticFieldShadowed() {
            assertHasViolation("class T {\n"
                            + "    static int x;\n"
                            + "    void m(int x) { }\n"
                            + "}",
                    "Parameter");
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customParameterMessage() {
            setRuleProperty(rule, "parameterMessage", "Param {0} shadows field");
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void m(int x) { }\n"
                            + "}",
                    "Param x shadows field");
        }

        @Test
        void customLocalVariableMessage() {
            setRuleProperty(rule, "localVariableMessage", "Local {0} shadows field");
            assertHasViolation("class T {\n"
                            + "    private int x;\n"
                            + "    void m() { int x = 0; }\n"
                            + "}",
                    "Local x shadows field");
        }
    }
}

