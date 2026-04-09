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

class JUnitStaticSuiteRuleTest {

    private Rule rule;

    @BeforeEach
    void setUp() {
        rule = new JUnitStaticSuiteRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("JUnitStaticSuite violation");
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
        void publicStaticSuiteMethod() {
            assertNoViolations("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public static junit.framework.Test suite() { return null; }
                    }""");
        }

        @Test
        void noSuiteMethod() {
            assertNoViolations("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public void testSomething() { }
                    }""");
        }

        @Test
        void nonTestClass() {
            assertNoViolations("""
                    class NotATest {
                        private void suite() { }
                    }""");
        }

        @Test
        void suiteMethodWithParameters() {
            assertNoViolations("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        private void suite(int x) { }
                    }""");
        }
    }

    @Nested
    class Violations {
        @Test
        void nonPublicSuiteMethod() {
            assertViolationCount("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        static junit.framework.Test suite() { return null; }
                    }""", 1);
        }

        @Test
        void nonStaticSuiteMethod() {
            assertViolationCount("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public junit.framework.Test suite() { return null; }
                    }""", 1);
        }

        @Test
        void privateSuiteMethod() {
            assertViolationCount("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        private static junit.framework.Test suite() { return null; }
                    }""", 1);
        }

        @Test
        void neitherPublicNorStatic() {
            assertViolationCount("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        junit.framework.Test suite() { return null; }
                    }""", 1);
        }
    }
}

