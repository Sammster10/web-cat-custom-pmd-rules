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

class CovariantEqualsRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new CovariantEqualsRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("CovariantEquals violation");
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
        void properEqualsOverride() {
            assertNoViolations(
                    "class T {\n"
                            + "    public boolean equals(Object o) { return false; }\n"
                            + "}");
        }

        @Test
        void covariantEqualsWithProperOverride() {
            assertNoViolations(
                    "class T {\n"
                            + "    public boolean equals(T other) { return false; }\n"
                            + "    public boolean equals(Object o) { return false; }\n"
                            + "}");
        }

        @Test
        void noEqualsMethod() {
            assertNoViolations(
                    "class T {\n"
                            + "    void doStuff() { }\n"
                            + "}");
        }

        @Test
        void equalsWithNoParameters() {
            assertNoViolations(
                    "class T {\n"
                            + "    public boolean equals() { return false; }\n"
                            + "}");
        }

        @Test
        void equalsWithTwoParameters() {
            assertNoViolations(
                    "class T {\n"
                            + "    public boolean equals(T a, T b) { return false; }\n"
                            + "}");
        }

        @Test
        void emptyClass() {
            assertNoViolations(
                    "class T { }");
        }

        @Test
        void fullyQualifiedObjectParameter() {
            assertNoViolations(
                    "class T {\n"
                            + "    public boolean equals(java.lang.Object o) { return false; }\n"
                            + "}");
        }
    }

    @Nested
    class Violations {
        @Test
        void covariantEqualsOnly() {
            assertHasViolation("class T {\n"
                            + "    public boolean equals(T other) { return false; }\n"
                            + "}",
                    "defines equals(T)");
        }

        @Test
        void covariantEqualsWithStringParam() {
            assertHasViolation("class T {\n"
                            + "    public boolean equals(String other) { return false; }\n"
                            + "}",
                    "defines equals(String)");
        }

        @Test
        void covariantEqualsInEnum() {
            assertHasViolation("enum E {\n"
                            + "    A, B;\n"
                            + "    public boolean equals(E other) { return false; }\n"
                            + "}",
                    "defines equals(E)");
        }

        @Test
        void covariantEqualsReportsOnlyOne() {
            assertViolationCount("class T {\n"
                    + "    public boolean equals(T other) { return false; }\n"
                    + "}", 1);
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customMessage() {
            setRuleProperty(rule, "violationMessage", "Bad equals in {0} with param {1}");
            assertHasViolation("class T {\n"
                            + "    public boolean equals(T other) { return false; }\n"
                            + "}",
                    "Bad equals in T with param T");
        }
    }
}
