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
            assertNoViolations("import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertEquals(1, 1);\n"
                    + "    }\n"
                    + "}");
        }

        @Test
        void properAssertTrue() {
            assertNoViolations("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertTrue(someCondition());\n"
                    + "    }\n"
                    + "    boolean someCondition() { return true; }\n"
                    + "}");
        }

        @Test
        void nonTestCode() {
            assertNoViolations("class Foo {\n"
                    + "    void doStuff() {\n"
                    + "        boolean x = (1 == 1);\n"
                    + "    }\n"
                    + "}");
        }
    }

    @Nested
    class AssertTrueWithEquality {
        @Test
        void assertTrueWithEqualityOnPrimitives() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertTrue(1 == 1);\n"
                    + "    }\n"
                    + "}", "assertEquals");
        }

        @Test
        void assertTrueWithNullCheck() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        Object o = null;\n"
                    + "        assertTrue(o == null);\n"
                    + "    }\n"
                    + "}", "assertNull");
        }

        @Test
        void assertTrueWithInequalityOnPrimitives() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertTrue(1 != 2);\n"
                    + "    }\n"
                    + "}", "assertNotEquals");
        }
    }

    @Nested
    class AssertTrueWithNegation {
        @Test
        void assertTrueWithBooleanNegation() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertTrue(!true);\n"
                    + "    }\n"
                    + "}", "assertFalse");
        }

        @Test
        void assertFalseWithBooleanNegation() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertFalse;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertFalse(!true);\n"
                    + "    }\n"
                    + "}", "assertTrue");
        }
    }

    @Nested
    class AssertTrueWithObjectEquals {
        @Test
        void assertTrueWithEqualsCall() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        String a = \"a\";\n"
                    + "        String b = \"a\";\n"
                    + "        assertTrue(a.equals(b));\n"
                    + "    }\n"
                    + "}", "assertEquals");
        }

        @Test
        void assertTrueWithNegatedEqualsCall() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        String a = \"a\";\n"
                    + "        String b = \"b\";\n"
                    + "        assertTrue(!a.equals(b));\n"
                    + "    }\n"
                    + "}", "assertNotEquals");
        }
    }

    @Nested
    class AssertEqualsWithBoolean {
        @Test
        void assertEqualsWithTrueLiteral() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertEquals(true, 1 > 0);\n"
                    + "    }\n"
                    + "}", "assertTrue");
        }

        @Test
        void assertEqualsWithFalseLiteral() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertEquals(false, 1 > 0);\n"
                    + "    }\n"
                    + "}", "assertFalse");
        }

        @Test
        void assertNotEqualsWithTrueLiteral() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertNotEquals;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertNotEquals(true, 1 > 0);\n"
                    + "    }\n"
                    + "}", "assertFalse");
        }
    }

    @Nested
    class AssertFalseWithEquality {
        @Test
        void assertFalseWithEqualityOnPrimitives() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertFalse;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        assertFalse(1 == 1);\n"
                    + "    }\n"
                    + "}", "assertNotEquals");
        }

        @Test
        void assertFalseWithNotNullCheck() {
            assertHasViolation("import static org.junit.jupiter.api.Assertions.assertFalse;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "class MyTest {\n"
                    + "    @Test\n"
                    + "    void testFoo() {\n"
                    + "        Object o = null;\n"
                    + "        assertFalse(o == null);\n"
                    + "    }\n"
                    + "}", "assertNonNull");
        }
    }
}

