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

class UnitTestShouldIncludeAssertRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new UnitTestShouldIncludeAssertRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("UnitTestShouldIncludeAssert violation");
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
        void junit3TestWithAssert() {
            assertNoViolations("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public void testFoo() {
                            assertEquals(1, 1);
                        }
                    }""");
        }

        @Test
        void junit4TestWithAssert() {
            assertNoViolations("""
                    import org.junit.Test;
                    import static org.junit.Assert.assertEquals;
                    class MyTest {
                        @Test
                        public void testFoo() {
                            assertEquals(1, 1);
                        }
                    }""");
        }

        @Test
        void junit5TestWithAssert() {
            assertNoViolations("""
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertTrue(true);
                        }
                    }""");
        }

        @Test
        void testWithFailCall() {
            assertNoViolations("""
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.fail;
                    class MyTest {
                        @Test
                        void testFoo() {
                            fail("not implemented");
                        }
                    }""");
        }

        @Test
        void nonTestMethod() {
            assertNoViolations("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public void helperMethod() {
                            int x = 1;
                        }
                    }""");
        }

        @Test
        void junit4ExpectedAnnotation() {
            assertNoViolations("""
                    import org.junit.Test;
                    class MyTest {
                        @Test(expected = RuntimeException.class)
                        public void testThrows() {
                            throw new RuntimeException();
                        }
                    }""");
        }
    }

    @Nested
    class Violations {
        @Test
        void junit3TestWithoutAssert() {
            assertViolationCount("""
                    import student.TestCase;
                    class MyTest extends TestCase {
                        public void testFoo() {
                            int x = 1;
                        }
                    }""", 1);
        }

        @Test
        void junit5TestWithoutAssert() {
            assertViolationCount("""
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            int x = 1;
                        }
                    }""", 1);
        }

        @Test
        void junit4TestWithoutAssert() {
            assertViolationCount("""
                    import org.junit.Test;
                    class MyTest {
                        @Test
                        public void testFoo() {
                            int x = 1;
                        }
                    }""", 1);
        }

        @Test
        void multipleTestsWithoutAsserts() {
            assertViolationCount("""
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            int x = 1;
                        }
                        @Test
                        void testBar() {
                            int y = 2;
                        }
                    }""", 2);
        }
    }

    @Nested
    class ExtraAssertMethods {
        @Test
        void customAssertMethodSuppressesViolation() {
            setRuleProperty(rule, "extraAssertMethodNames", List.of("myCustomAssert"));
            assertNoViolations("""
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            myCustomAssert(1);
                        }
                        void myCustomAssert(int x) { }
                    }""");
        }
    }
}

