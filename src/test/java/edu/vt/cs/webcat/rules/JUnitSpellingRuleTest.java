package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitSpellingRuleTest {

    private Rule rule;

    @BeforeEach
    void setUp() {
        rule = new JUnitSpellingRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("JUnitSpelling violation");
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

    @Nested
    class NoViolations {
        @Test
        void correctSetUpSpelling() {
            assertNoViolations(
                    "import student.TestCase;\n"
                            + "class MyTest extends TestCase {\n"
                            + "    public void setUp() { }\n"
                            + "}");
        }

        @Test
        void correctTearDownSpelling() {
            assertNoViolations(
                    "import student.TestCase;\n"
                            + "class MyTest extends TestCase {\n"
                            + "    public void tearDown() { }\n"
                            + "}");
        }

        @Test
        void unrelatedMethodName() {
            assertNoViolations(
                    "import student.TestCase;\n"
                            + "class MyTest extends TestCase {\n"
                            + "    public void testSomething() { }\n"
                            + "}");
        }

        @Test
        void nonTestClass() {
            assertNoViolations(
                    "class NotATest {\n"
                            + "    public void setup() { }\n"
                            + "}");
        }

        @Test
        void methodWithParameters() {
            assertNoViolations(
                    "import student.TestCase;\n"
                            + "class MyTest extends TestCase {\n"
                            + "    public void setup(int x) { }\n"
                            + "}");
        }
    }

    @Nested
    class Violations {
        @Test
        void lowercaseSetup() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void setup() { }\n"
                    + "}", 1);
        }

        @Test
        void uppercaseSetup() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void SETUP() { }\n"
                    + "}", 1);
        }

        @Test
        void lowercaseTeardown() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void teardown() { }\n"
                    + "}", 1);
        }

        @Test
        void uppercaseTeardown() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void TEARDOWN() { }\n"
                    + "}", 1);
        }

        @Test
        void bothMisspelled() {
            assertViolationCount("import student.TestCase;\n"
                    + "class MyTest extends TestCase {\n"
                    + "    public void setup() { }\n"
                    + "    public void teardown() { }\n"
                    + "}", 2);
        }
    }
}

