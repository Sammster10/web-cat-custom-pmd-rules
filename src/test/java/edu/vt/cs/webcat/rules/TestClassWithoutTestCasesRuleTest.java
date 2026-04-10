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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestClassWithoutTestCasesRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new TestClassWithoutTestCasesRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("TestClassWithoutTestCases: {0}");
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

    private void assertViolationCount(String code, int expected) {
        List<RuleViolation> violations = runRule(code);
        assertEquals(expected, violations.size(),
                String.format("Expected %d violations but found %d: %s",
                        expected, violations.size(), violations));
    }

    private void assertHasViolation(String code, String messageFragment) {
        List<RuleViolation> violations = runRule(code);
        boolean found = violations.stream()
                .anyMatch(v -> v.getDescription().contains(messageFragment));
        assertTrue(found,
                String.format("Expected violation containing '%s' but found: %s",
                        messageFragment, violations));
    }

    @Nested
    class NoViolations {
        @Test
        void junit3ClassWithTestMethod() {
            assertNoViolations("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void testSomething() { }\n"
                    + "}");
        }

        @Test
        void junit5ClassWithTestMethod() {
            assertNoViolations("import org.junit.jupiter.api.Test;\n"
                    + "class TestFoo {\n"
                    + "    @Test\n"
                    + "    void testSomething() { }\n"
                    + "}");
        }

        @Test
        void nonTestClass() {
            assertNoViolations("class Foo {\n"
                    + "    void doStuff() { }\n"
                    + "}");
        }

        @Test
        void abstractTestClass() {
            assertNoViolations("abstract class TestBase {\n"
                    + "    void doStuff() { }\n"
                    + "}");
        }

        @Test
        void interfaceWithTestName() {
            assertNoViolations("interface TestInterface {\n"
                    + "    void doStuff();\n"
                    + "}");
        }
    }

    @Nested
    class Violations {
        @Test
        void junit3ClassWithNoTests() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void helperMethod() { }\n"
                    + "}", 1);
        }

        @Test
        void testNamePatternClassWithNoTests() {
            assertViolationCount("class TestFoo {\n"
                    + "    void helperMethod() { }\n"
                    + "}", 1);
        }

        @Test
        void testSuffixPatternWithNoTests() {
            assertViolationCount("class FooTest {\n"
                    + "    void helperMethod() { }\n"
                    + "}", 1);
        }

        @Test
        void testCaseSuffixPatternWithNoTests() {
            assertViolationCount("class FooTestCase {\n"
                    + "    void helperMethod() { }\n"
                    + "}", 1);
        }

        @Test
        void emptyTestClass() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "}", 1);
        }
    }

    @Nested
    class PatternConfiguration {
        @Test
        void emptyPatternDisablesDetectionByName() {
            setRuleProperty(rule, "testClassPattern", Pattern.compile(""));
            assertNoViolations("class TestFoo {\n"
                    + "    void helperMethod() { }\n"
                    + "}");
        }

        @Test
        void customPatternMatchesClass() {
            setRuleProperty(rule, "testClassPattern", Pattern.compile(".*Spec$"));
            assertViolationCount("class FooSpec {\n"
                    + "    void helperMethod() { }\n"
                    + "}", 1);
        }

        @Test
        void customPatternDoesNotMatchDefaultNames() {
            setRuleProperty(rule, "testClassPattern", Pattern.compile(".*Spec$"));
            assertNoViolations("class TestFoo {\n"
                    + "    void helperMethod() { }\n"
                    + "}");
        }
    }
}

