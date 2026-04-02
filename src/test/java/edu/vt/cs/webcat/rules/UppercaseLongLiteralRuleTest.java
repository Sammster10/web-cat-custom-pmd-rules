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

class UppercaseLongLiteralRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new UppercaseLongLiteralRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("UppercaseLongLiteral violation");
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
    class ValidLiterals {
        @Test
        void uppercaseLongLiteral() {
            assertNoViolations("class T { long x = 100L; }");
        }

        @Test
        void intLiteral() {
            assertNoViolations("class T { int x = 100; }");
        }

        @Test
        void doubleLiteral() {
            assertNoViolations("class T { double x = 1.0; }");
        }

        @Test
        void floatLiteral() {
            assertNoViolations("class T { float x = 1.0f; }");
        }

        @Test
        void hexLongLiteral() {
            assertNoViolations("class T { long x = 0xFFL; }");
        }

        @Test
        void zeroLong() {
            assertNoViolations("class T { long x = 0L; }");
        }

        @Test
        void negativeLong() {
            assertNoViolations("class T { long x = -100L; }");
        }

        @Test
        void longMaxValue() {
            assertNoViolations("class T { long x = 9223372036854775807L; }");
        }
    }

    @Nested
    class InvalidLiterals {
        @Test
        void lowercaseLongLiteral() {
            assertHasViolation("class T { long x = 100l; }",
                    "Long literals should end with");
        }

        @Test
        void lowercaseZeroLong() {
            assertHasViolation("class T { long x = 0l; }",
                    "Long literals should end with");
        }

        @Test
        void lowercaseHexLong() {
            assertHasViolation("class T { long x = 0xFFl; }",
                    "Long literals should end with");
        }

        @Test
        void lowercaseLongInMethodBody() {
            assertHasViolation("class T { void m() { long x = 42l; } }",
                    "Long literals should end with");
        }

        @Test
        void multipleLowercaseLongs() {
            assertViolationCount("class T { long a = 1l; long b = 2l; }", 2);
        }
    }

    @Nested
    class CustomMessage {
        @Test
        void usesCustomMessageWhenSet() {
            setRuleProperty(rule, "message", "Use uppercase L");
            assertHasViolation("class T { long x = 100l; }", "Use uppercase L");
        }
    }
}
