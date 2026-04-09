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

class FieldVisibilityRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new FieldVisibilityRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("FieldVisibility violation");
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
        void privateField() {
            assertNoViolations("""
                    class T {
                        private int x;
                    }""");
        }

        @Test
        void protectedFieldAllowedByDefault() {
            assertNoViolations("""
                    class T {
                        protected int x;
                    }""");
        }

        @Test
        void publicStaticFinalField() {
            assertNoViolations("""
                    class T {
                        public static final int MAX = 10;
                    }""");
        }

        @Test
        void privateStaticField() {
            assertNoViolations("""
                    class T {
                        private static int count;
                    }""");
        }

        @Test
        void privateFinalField() {
            assertNoViolations("""
                    class T {
                        private final int x = 1;
                    }""");
        }
    }

    @Nested
    class MissingVisibilityModifier {
        @Test
        void packagePrivateField() {
            assertHasViolation("""
                    class T {
                        int x;
                    }""", "missing a visibility modifier");
        }

        @Test
        void packagePrivateStaticField() {
            assertHasViolation("""
                    class T {
                        static int x;
                    }""", "missing a visibility modifier");
        }

        @Test
        void packagePrivateFinalField() {
            assertHasViolation("""
                    class T {
                        final int x = 1;
                    }""", "missing a visibility modifier");
        }

        @Test
        void multiplePackagePrivateFields() {
            assertViolationCount("""
                    class T {
                        int x;
                        int y;
                    }""", 2);
        }
    }

    @Nested
    class PublicNonConstantField {
        @Test
        void publicNonStaticField() {
            assertHasViolation("""
                    class T {
                        public int x;
                    }""", "not static final");
        }

        @Test
        void publicStaticNonFinalField() {
            assertHasViolation("""
                    class T {
                        public static int x;
                    }""", "not static final");
        }

        @Test
        void publicFinalNonStaticField() {
            assertHasViolation("""
                    class T {
                        public final int x = 1;
                    }""", "not static final");
        }
    }

    @Nested
    class ProtectedFieldRestriction {
        @Test
        void protectedFieldFlaggedWhenDisallowed() {
            setRuleProperty(rule, "allowProtected", false);
            assertHasViolation("""
                    class T {
                        protected int x;
                    }""", "should be private");
        }

        @Test
        void protectedFieldAllowedByDefault() {
            assertNoViolations("""
                    class T {
                        protected int x;
                    }""");
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customMissingModifierMessage() {
            setRuleProperty(rule, "missingModifierMessage", "No visibility on {0}");
            assertHasViolation("""
                    class T {
                        int x;
                    }""", "No visibility on x");
        }

        @Test
        void customPublicFieldMessage() {
            setRuleProperty(rule, "publicFieldMessage", "Bad public field {0}");
            assertHasViolation("""
                    class T {
                        public int x;
                    }""", "Bad public field x");
        }

        @Test
        void customProtectedFieldMessage() {
            setRuleProperty(rule, "allowProtected", false);
            setRuleProperty(rule, "protectedFieldMessage", "Protected not ok: {0}");
            assertHasViolation("""
                    class T {
                        protected int x;
                    }""", "Protected not ok: x");
        }
    }

    @Nested
    class MixedFields {
        @Test
        void mixOfValidAndInvalidFields() {
            String code = """
                    class T {
                        private int a;
                        int b;
                        public int c;
                        public static final int D = 1;
                    }""";
            assertViolationCount(code, 2);
        }

        @Test
        void interfacePublicStaticFinalFieldsAllowed() {
            assertNoViolations("""
                    interface T {
                        int X = 1;
                    }""");
        }
    }
}
