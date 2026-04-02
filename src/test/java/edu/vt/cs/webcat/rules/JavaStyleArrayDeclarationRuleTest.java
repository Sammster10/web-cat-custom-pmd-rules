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

class JavaStyleArrayDeclarationRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new JavaStyleArrayDeclarationRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("JavaStyleArrayDeclaration violation");
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
    class JavaStyleValid {
        @Test
        void fieldDeclaration() {
            assertNoViolations("class T { int[] x; }");
        }

        @Test
        void multiDimensionalField() {
            assertNoViolations("class T { int[][] x; }");
        }

        @Test
        void localVariable() {
            assertNoViolations("class T { void m() { int[] x = new int[1]; } }");
        }

        @Test
        void methodParameter() {
            assertNoViolations("class T { void m(String[] args) { } }");
        }

        @Test
        void methodReturnType() {
            assertNoViolations("class T { int[] m() { return null; } }");
        }

        @Test
        void multiDimensionalReturnType() {
            assertNoViolations("class T { int[][] m() { return null; } }");
        }

        @Test
        void objectArrayField() {
            assertNoViolations("class T { String[] names; }");
        }

        @Test
        void forEachLoop() {
            assertNoViolations("class T { void m() { for (int x : new int[]{1}) { } } }");
        }

        @Test
        void nonArrayDeclarations() {
            assertNoViolations("class T { int x; String s; double d; }");
        }

        @Test
        void genericArrayField() {
            assertNoViolations("class T { java.util.List<String>[] x; }");
        }
    }

    @Nested
    class CStyleVariableInvalid {
        @Test
        void fieldDeclaration() {
            assertHasViolation("class T { int x[]; }",
                    "not the variable: x");
        }

        @Test
        void multiDimensionalField() {
            assertHasViolation("class T { int x[][]; }",
                    "not the variable: x");
        }

        @Test
        void localVariable() {
            assertHasViolation(
                    "class T { void m() { int x[] = new int[1]; } }",
                    "not the variable: x");
        }

        @Test
        void methodParameter() {
            assertHasViolation(
                    "class T { void m(String args[]) { } }",
                    "not the variable: args");
        }

        @Test
        void multipleDeclaratorsOnlyOneCStyle() {
            String code = "class T { int[] a, b[]; }";
            assertViolationCount(code, 1);
            assertHasViolation(code, "not the variable: b");
        }

        @Test
        void multipleFieldsBothCStyle() {
            assertViolationCount("class T { int a[], b[]; }", 2);
        }

        @Test
        void objectArrayCStyle() {
            assertHasViolation("class T { String s[]; }", "not the variable: s");
        }

        @Test
        void mixedDimensions() {
            assertHasViolation("class T { int[] x[]; }", "not the variable: x");
        }

        @Test
        void forLoopVariable() {
            assertHasViolation(
                    "class T { void m() { for (int i[] = null;;) { } } }",
                    "not the variable: i");
        }
    }

    @Nested
    class CStyleMethodReturnInvalid {
        @Test
        void simpleReturnType() {
            assertHasViolation(
                    "class T { int m()[] { return null; } }",
                    "not after the parameter list: m");
        }

        @Test
        void multiDimensionalReturnType() {
            assertHasViolation(
                    "class T { int m()[][] { return null; } }",
                    "not after the parameter list: m");
        }
    }

    @Nested
    class MultipleCStyleDeclarations {
        @Test
        void fieldAndLocalVariable() {
            String code = "class T { int x[]; void m() { int y[] = null; } }";
            assertViolationCount(code, 2);
        }

        @Test
        void fieldAndMethodReturn() {
            String code = "class T { int x[]; int m()[] { return null; } }";
            assertViolationCount(code, 2);
        }

        @Test
        void multipleMethods() {
            String code = "class T { int a()[] { return null; } int b()[] { return null; } }";
            assertViolationCount(code, 2);
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customVariableMessage() {
            setRuleProperty(rule, "variableMessage", "Bad array style on {0}");
            assertHasViolation("class T { int x[]; }", "Bad array style on x");
        }

        @Test
        void customMethodMessage() {
            setRuleProperty(rule, "methodMessage", "Bad return array on {0}");
            assertHasViolation("class T { int m()[] { return null; } }", "Bad return array on m");
        }
    }
}
