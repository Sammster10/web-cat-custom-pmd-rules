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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineLengthRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new LineLengthRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("LineLength violation");
    }

    private List<RuleViolation> runRule(String code) {
        return runRule(code, "Test.java");
    }

    private List<RuleViolation> runRule(String code, String fileName) {
        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                JavaLanguageModule.getInstance().getVersion("17"));
        RuleSet ruleSet = RuleSet.forSingleRule(rule);
        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
            analysis.addRuleSet(ruleSet);
            analysis.files().addFile(
                    TextFile.forCharSeq(code,
                            FileId.fromPathLikeString(fileName),
                            JavaLanguageModule.getInstance().getVersion("17")));
            Report report = analysis.performAnalysisAndCollectReport();
            return report.getViolations();
        }
    }

    private void assertNoViolations(String code) {
        assertNoViolations(code, "Test.java");
    }

    private void assertNoViolations(String code, String fileName) {
        List<RuleViolation> violations = runRule(code, fileName);
        assertTrue(violations.isEmpty(),
                String.format("Expected no violations but found %d: %s",
                        violations.size(), violations));
    }

    private void assertHasViolation(String code, String messageFragment) {
        assertHasViolation(code, messageFragment, "Test.java");
    }

    private void assertHasViolation(String code, String messageFragment, String fileName) {
        List<RuleViolation> violations = runRule(code, fileName);
        boolean found = violations.stream()
                .anyMatch(v -> v.getDescription().contains(messageFragment));
        assertTrue(found,
                String.format("Expected violation containing '%s' but found: %s",
                        messageFragment, violations));
    }

    private void assertViolationCount(String code, int expected) {
        assertViolationCount(code, expected, "Test.java");
    }

    private void assertViolationCount(String code, int expected, String fileName) {
        List<RuleViolation> violations = runRule(code, fileName);
        assertEquals(expected, violations.size(),
                String.format("Expected %d violations but found %d: %s",
                        expected, violations.size(), violations));
    }

    @Nested
    class DefaultMaxLength {
        @Test
        void shortLineNoViolation() {
            assertNoViolations("class T { }");
        }

        @Test
        void exactlyAtLimit() {
            String line = "class T { int x" + " ".repeat(80 - 16) + ";}";
            if (line.length() <= 80) {
                assertNoViolations(line);
            }
        }

        @Test
        void lineExceedsDefaultLimit() {
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertHasViolation(longLine, "Line exceeds maximum length of 80");
        }

        @Test
        void multipleLinesSomeExceed() {
            String code = "class T {\n"
                    + "    int x = 1;\n"
                    + "    String s = \"" + "a".repeat(80) + "\";\n"
                    + "    int y = 2;\n"
                    + "}";
            assertViolationCount(code, 1);
        }

        @Test
        void allShortLines() {
            String code = "class T {\n    int x = 1;\n    int y = 2;\n}";
            assertNoViolations(code);
        }

        @Test
        void emptyFile() {
            assertNoViolations("");
        }
    }

    @Nested
    class CustomMaxLength {
        @BeforeEach
        void configureMaxLength() {
            setRuleProperty(rule, "maxLength", 40);
        }

        @Test
        void lineUnderCustomLimit() {
            assertNoViolations("class T { int x = 1; }");
        }

        @Test
        void lineExceedsCustomLimit() {
            String longLine = "class T { String s = \"" + "a".repeat(30) + "\"; }";
            assertHasViolation(longLine, "Line exceeds maximum length of 40");
        }
    }

    @Nested
    class IgnorePattern {
        @BeforeEach
        void configureIgnorePattern() {
            setRuleProperty(rule, "ignorePattern", Pattern.compile("^import "));
        }

        @Test
        void importLineIgnored() {
            String longImport = "import " + "a".repeat(80) + ";";
            assertNoViolations(longImport);
        }

        @Test
        void nonImportLongLineStillFlagged() {
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertHasViolation(longLine, "Line exceeds maximum length");
        }
    }

    @Nested
    class CustomMessage {
        @Test
        void usesCustomMessageWhenSet() {
            setRuleProperty(rule, "message", "Too long!");
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertHasViolation(longLine, "Too long!");
        }
    }

    @Nested
    class MultipleLongLines {
        @Test
        void twoLongLines() {
            String code = "class T {\n"
                    + "    String a = \"" + "x".repeat(80) + "\";\n"
                    + "    String b = \"" + "y".repeat(80) + "\";\n"
                    + "}";
            assertViolationCount(code, 2);
        }
    }

    @Nested
    class FilePatternExempt {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "filePattern", Pattern.compile("Test\\.java$"));
            setRuleProperty(rule, "filePatternMaxLength", 0);
        }

        @Test
        void matchingFileIsExempt() {
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertNoViolations(longLine, "FooTest.java");
        }

        @Test
        void nonMatchingFileStillEnforced() {
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertHasViolation(longLine, "Line exceeds maximum length of 80", "Foo.java");
        }
    }

    @Nested
    class FilePatternAlternativeLength {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "filePattern", Pattern.compile("Test\\.java$"));
            setRuleProperty(rule, "filePatternMaxLength", 120);
        }

        @Test
        void matchingFileUsesAlternativeLength() {
            String line = "class T { String s = \"" + "a".repeat(90) + "\"; }";
            assertNoViolations(line, "FooTest.java");
        }

        @Test
        void matchingFileViolatesAlternativeLength() {
            String line = "class T { String s = \"" + "a".repeat(120) + "\"; }";
            assertHasViolation(line, "Line exceeds maximum length of 120", "FooTest.java");
        }

        @Test
        void nonMatchingFileUsesDefaultLength() {
            String line = "class T { String s = \"" + "a".repeat(90) + "\"; }";
            assertHasViolation(line, "Line exceeds maximum length of 80", "Foo.java");
        }
    }

    @Nested
    class FilePatternDefaultBehavior {
        @Test
        void noFilePatternSetStillEnforces() {
            String longLine = "class T { String s = \"" + "a".repeat(80) + "\"; }";
            assertHasViolation(longLine, "Line exceeds maximum length of 80", "FooTest.java");
        }
    }
}
