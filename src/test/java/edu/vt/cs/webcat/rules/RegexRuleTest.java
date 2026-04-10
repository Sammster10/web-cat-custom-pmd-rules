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

import static org.junit.jupiter.api.Assertions.*;

class RegexRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new RegexRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Regex violation");
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
    class ShouldMatchMode {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "shouldMatch", true);
        }

        @Test
        void patternPresentNoViolation() {
            setRuleProperty(rule, "pattern", Pattern.compile("class"));
            assertNoViolations("class T { }");
        }

        @Test
        void patternMissingViolation() {
            setRuleProperty(rule, "pattern", Pattern.compile("@Override"));
            assertHasViolation("class T { }", "Required pattern not found");
        }

        @Test
        void regexPatternPresent() {
            setRuleProperty(rule, "pattern", Pattern.compile("public\\s+class"));
            assertNoViolations("public class T { }");
        }

        @Test
        void regexPatternMissing() {
            setRuleProperty(rule, "pattern", Pattern.compile("public\\s+class"));
            assertHasViolation("class T { }", "Required pattern not found");
        }
    }

    @Nested
    class ShouldNotMatchMode {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "shouldMatch", false);
        }

        @Test
        void forbiddenPatternAbsentNoViolation() {
            setRuleProperty(rule, "pattern", Pattern.compile("System\\.exit"));
            assertNoViolations("class T { void m() { } }");
        }

        @Test
        void forbiddenPatternPresentViolation() {
            setRuleProperty(rule, "pattern", Pattern.compile("System\\.exit"));
            assertHasViolation(
                    "class T { void m() { System.exit(0); } }",
                    "Forbidden pattern found");
        }

        @Test
        void multipleForbiddenOccurrences() {
            setRuleProperty(rule, "pattern", Pattern.compile("System\\.out"));
            assertViolationCount(
                    "class T {\n"
                            + "    void m() {\n"
                            + "        System.out.println(1);\n"
                            + "        System.out.println(2);\n"
                            + "    }\n"
                            + "}",
                    2);
        }

        @Test
        void forbiddenPatternNotInSource() {
            setRuleProperty(rule, "pattern", Pattern.compile("goto"));
            assertNoViolations("class T { void m() { int x = 1; } }");
        }
    }

    @Nested
    class CustomMessage {
        @Test
        void shouldMatchCustomMessage() {
            setRuleProperty(rule, "shouldMatch", true);
            setRuleProperty(rule, "pattern", Pattern.compile("@author"));
            setRuleProperty(rule, "message", "Missing author tag");
            assertHasViolation("class T { }", "Missing author tag");
        }

        @Test
        void shouldNotMatchCustomMessage() {
            setRuleProperty(rule, "shouldMatch", false);
            setRuleProperty(rule, "pattern", Pattern.compile("TODO"));
            setRuleProperty(rule, "message", "Remove TODOs");
            String code = "class T {\n"
                    + "    // TODO fix this\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            assertFalse(violations.isEmpty(),
                    "Expected at least one violation for forbidden pattern TODO");
        }
    }

    @Nested
    class DefaultBehavior {
        @Test
        void defaultPatternMatchesAnything() {
            assertNoViolations("class T { }");
        }
    }

    @Nested
    class MultilineMode {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "multiline", true);
        }

        @Test
        void caretMatchesLineStart() {
            setRuleProperty(rule, "shouldMatch", false);
            setRuleProperty(rule, "pattern", Pattern.compile("^\\s*\\*\\s*@author\\s+.*\\byour(-|\\s+)(name|pid|username)"));
            String code = "/**\n"
                    + " * @author your username\n"
                    + " */\n"
                    + "class T { }";
            assertViolationCount(code, 1);
        }

        @Test
        void caretMatchesLineStartNoMatch() {
            setRuleProperty(rule, "shouldMatch", false);
            setRuleProperty(rule, "pattern", Pattern.compile("^\\s*\\*\\s*@author\\s+.*\\byour(-|\\s+)(name|pid|username)"));
            String code = "/**\n"
                    + " * @author Jane Doe\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void shouldMatchMultilineCaretPresent() {
            setRuleProperty(rule, "shouldMatch", true);
            setRuleProperty(rule, "pattern", Pattern.compile("^package"));
            assertNoViolations("package foo;\n"
                    + "class T { }");
        }

        @Test
        void shouldMatchMultilineCaretAbsent() {
            setRuleProperty(rule, "shouldMatch", true);
            setRuleProperty(rule, "pattern", Pattern.compile("^import"));
            assertHasViolation("package foo;\n"
                    + "class T { }", "Required pattern not found");
        }

        @Test
        void multilineDisabledCaretMatchesOnlyStart() {
            setRuleProperty(rule, "multiline", false);
            setRuleProperty(rule, "shouldMatch", false);
            setRuleProperty(rule, "pattern", Pattern.compile("^class"));
            String code = "package foo;\n"
                    + "class T { }";
            assertNoViolations(code);
        }
    }

    @Nested
    class DotallMode {
        @Test
        void dotMatchesNewlineWhenEnabled() {
            setRuleProperty(rule, "dotall", true);
            setRuleProperty(rule, "shouldMatch", true);
            setRuleProperty(rule, "pattern", Pattern.compile("/\\*.*\\*/"));
            String code = "/*\n"
                    + " * comment\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void dotDoesNotMatchNewlineWhenDisabled() {
            setRuleProperty(rule, "dotall", false);
            setRuleProperty(rule, "shouldMatch", true);
            setRuleProperty(rule, "pattern", Pattern.compile("/\\*.*\\*/"));
            String code = "/*\n"
                    + " * comment\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "Required pattern not found");
        }
    }

    @Nested
    class AuthorUsernamePreventer {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "shouldMatch", false);
            setRuleProperty(rule, "multiline", true);
            setRuleProperty(rule, "pattern", Pattern.compile("^\\s*\\*\\s*@author\\s+.*\\byour(-|\\s+)(name|pid|username)"));
            setRuleProperty(rule, "message", "This is a placeholder and should be replaced.");
        }

        @Test
        void detectsYourUsername() {
            String code = "/**\n"
                    + " * @author your username\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "This is a placeholder and should be replaced.");
        }

        @Test
        void detectsYourName() {
            String code = "/**\n"
                    + " * @author your name\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "This is a placeholder and should be replaced.");
        }

        @Test
        void detectsYourPid() {
            String code = "/**\n"
                    + " * @author your pid\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "This is a placeholder and should be replaced.");
        }

        @Test
        void detectsYourHyphenUsername() {
            String code = "/**\n"
                    + " * @author your-username\n"
                    + " */\n"
                    + "class T { }";
            assertHasViolation(code, "This is a placeholder and should be replaced.");
        }

        @Test
        void allowsRealAuthor() {
            String code = "/**\n"
                    + " * @author Jane Doe\n"
                    + " */\n"
                    + "class T { }";
            assertNoViolations(code);
        }

        @Test
        void allowsNoAuthorTag() {
            assertNoViolations("class T { }");
        }
    }
}

