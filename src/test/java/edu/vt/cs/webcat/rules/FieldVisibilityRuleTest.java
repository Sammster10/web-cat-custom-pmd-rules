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
            assertNoViolations("class T {\n    private int x;\n}");
        }

        @Test
        void protectedFieldAllowedByDefault() {
            assertNoViolations("class T {\n    protected int x;\n}");
        }

        @Test
        void publicStaticFinalField() {
            assertNoViolations("class T {\n    public static final int MAX = 10;\n}");
        }

        @Test
        void privateStaticField() {
            assertNoViolations("class T {\n    private static int count;\n}");
        }

        @Test
        void privateFinalField() {
            assertNoViolations("class T {\n    private final int x = 1;\n}");
        }
    }

    @Nested
    class MissingVisibilityModifier {
        @Test
        void packagePrivateField() {
            assertHasViolation("class T {\n    int x;\n}", "missing a visibility modifier");
        }

        @Test
        void packagePrivateStaticField() {
            assertHasViolation("class T {\n    static int x;\n}", "missing a visibility modifier");
        }

        @Test
        void packagePrivateFinalField() {
            assertHasViolation("class T {\n    final int x = 1;\n}", "missing a visibility modifier");
        }

        @Test
        void multiplePackagePrivateFields() {
            assertViolationCount("class T {\n    int x;\n    int y;\n}", 2);
        }
    }

    @Nested
    class PublicNonConstantField {
        @Test
        void publicNonStaticField() {
            assertHasViolation("class T {\n    public int x;\n}", "not static final");
        }

        @Test
        void publicStaticNonFinalField() {
            assertHasViolation("class T {\n    public static int x;\n}", "not static final");
        }

        @Test
        void publicFinalNonStaticField() {
            assertHasViolation("class T {\n    public final int x = 1;\n}", "not static final");
        }
    }

    @Nested
    class ProtectedFieldRestriction {
        @Test
        void protectedFieldFlaggedWhenDisallowed() {
            setRuleProperty(rule, "allowProtected", false);
            assertHasViolation("class T {\n    protected int x;\n}", "should be private");
        }

        @Test
        void protectedFieldAllowedByDefault() {
            assertNoViolations("class T {\n    protected int x;\n}");
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customMissingModifierMessage() {
            setRuleProperty(rule, "missingModifierMessage", "No visibility on {0}");
            assertHasViolation("class T {\n    int x;\n}", "No visibility on x");
        }

        @Test
        void customPublicFieldMessage() {
            setRuleProperty(rule, "publicFieldMessage", "Bad public field {0}");
            assertHasViolation("class T {\n    public int x;\n}", "Bad public field x");
        }

        @Test
        void customProtectedFieldMessage() {
            setRuleProperty(rule, "allowProtected", false);
            setRuleProperty(rule, "protectedFieldMessage", "Protected not ok: {0}");
            assertHasViolation("class T {\n    protected int x;\n}", "Protected not ok: x");
        }
    }

    @Nested
    class MixedFields {
        @Test
        void mixOfValidAndInvalidFields() {
            String code = "class T {\n"
                    + "    private int a;\n"
                    + "    int b;\n"
                    + "    public int c;\n"
                    + "    public static final int D = 1;\n"
                    + "}";
            assertViolationCount(code, 2);
        }

        @Test
        void interfacePublicStaticFinalFieldsAllowed() {
            assertNoViolations("interface T {\n    int X = 1;\n}");
        }
    }
}

