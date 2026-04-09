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

class SimplifiableTestAssertionRuleTest {

    private Rule rule;

    @BeforeEach
    void setUp() {
        rule = new SimplifiableTestAssertionRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Use {0} instead");
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
        void properAssertEquals() {
            assertNoViolations("""
                    import static org.junit.jupiter.api.Assertions.assertEquals;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertEquals(1, 1);
                        }
                    }""");
        }

        @Test
        void properAssertTrue() {
            assertNoViolations("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertTrue(someCondition());
                        }
                        boolean someCondition() { return true; }
                    }""");
        }

        @Test
        void nonTestCode() {
            assertNoViolations("""
                    class Foo {
                        void doStuff() {
                            boolean x = (1 == 1);
                        }
                    }""");
        }
    }

    @Nested
    class AssertTrueWithEquality {
        @Test
        void assertTrueWithEqualityOnPrimitives() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertTrue(1 == 1);
                        }
                    }""", "assertEquals");
        }

        @Test
        void assertTrueWithNullCheck() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            Object o = null;
                            assertTrue(o == null);
                        }
                    }""", "assertNull");
        }

        @Test
        void assertTrueWithInequalityOnPrimitives() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertTrue(1 != 2);
                        }
                    }""", "assertNotEquals");
        }
    }

    @Nested
    class AssertTrueWithNegation {
        @Test
        void assertTrueWithBooleanNegation() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertTrue(!true);
                        }
                    }""", "assertFalse");
        }

        @Test
        void assertFalseWithBooleanNegation() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertFalse;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertFalse(!true);
                        }
                    }""", "assertTrue");
        }
    }

    @Nested
    class AssertTrueWithObjectEquals {
        @Test
        void assertTrueWithEqualsCall() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            String a = "a";
                            String b = "a";
                            assertTrue(a.equals(b));
                        }
                    }""", "assertEquals");
        }

        @Test
        void assertTrueWithNegatedEqualsCall() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            String a = "a";
                            String b = "b";
                            assertTrue(!a.equals(b));
                        }
                    }""", "assertNotEquals");
        }
    }

    @Nested
    class AssertEqualsWithBoolean {
        @Test
        void assertEqualsWithTrueLiteral() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertEquals;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertEquals(true, 1 > 0);
                        }
                    }""", "assertTrue");
        }

        @Test
        void assertEqualsWithFalseLiteral() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertEquals;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertEquals(false, 1 > 0);
                        }
                    }""", "assertFalse");
        }

        @Test
        void assertNotEqualsWithTrueLiteral() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertNotEquals;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertNotEquals(true, 1 > 0);
                        }
                    }""", "assertFalse");
        }
    }

    @Nested
    class AssertFalseWithEquality {
        @Test
        void assertFalseWithEqualityOnPrimitives() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertFalse;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            assertFalse(1 == 1);
                        }
                    }""", "assertNotEquals");
        }

        @Test
        void assertFalseWithNotNullCheck() {
            assertHasViolation("""
                    import static org.junit.jupiter.api.Assertions.assertFalse;
                    import org.junit.jupiter.api.Test;
                    class MyTest {
                        @Test
                        void testFoo() {
                            Object o = null;
                            assertFalse(o == null);
                        }
                    }""", "assertNonNull");
        }
    }
}

