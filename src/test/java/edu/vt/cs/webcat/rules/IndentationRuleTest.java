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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndentationRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new IndentationRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Indentation violation");
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

    @Nested
    class CorrectIndentation {
        @Test
        void singleLevelClass() {
            String code = "class T {\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void nestedBlocks() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1;\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void deepNesting() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void emptyClass() {
            assertNoViolations("class T {\n}");
        }

        @Test
        void blankLinesIgnored() {
            String code = "class T {\n"
                    + "\n"
                    + "    int x;\n"
                    + "\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void closingBraceAtCorrectLevel() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class IncorrectIndentation {
        @Test
        void tooManySpaces() {
            String code = "class T {\n"
                    + "        int x;\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void tooFewSpaces() {
            String code = "class T {\n"
                    + "  int x;\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void noIndentInBlock() {
            String code = "class T {\n"
                    + "void m() {\n"
                    + "}\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }
    }

    @Nested
    class TabBanning {
        @Test
        void tabOnLineReportsViolation() {
            String code = "class T {\n"
                    + "\tint x;\n"
                    + "}";
            assertHasViolation(code, "tab character");
        }

        @Test
        void tabBanDisabledNoViolation() {
            setRuleProperty(rule, "banTabs", false);
            String code = "class T {\n"
                    + "\tint x;\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            boolean hasTabViolation = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("tab character"));
            assertFalse(hasTabViolation);
        }
    }

    @Nested
    class CustomIndentSize {
        @BeforeEach
        void configure() {
            setRuleProperty(rule, "indentSize", 2);
        }

        @Test
        void correctTwoSpaceIndent() {
            String code = "class T {\n"
                    + "  int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void fourSpacesWithTwoSizeIndent() {
            String code = "class T {\n"
                    + "    int x;\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void nestedTwoSpaceIndent() {
            String code = "class T {\n"
                    + "  void m() {\n"
                    + "    int x = 1;\n"
                    + "  }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customTabMessage() {
            setRuleProperty(rule, "tabViolationMessage", "No tabs on line {0}!");
            String code = "class T {\n\tint x;\n}";
            assertHasViolation(code, "No tabs on line");
        }

        @Test
        void customIndentMessage() {
            setRuleProperty(rule, "indentViolationMessage", "Bad indent at line {0}: wanted {1} got {2}");
            String code = "class T {\n  int x;\n}";
            assertHasViolation(code, "Bad indent at line");
        }
    }

    @Nested
    class BlockComments {
        @Test
        void blockCommentDoesNotAffectDepth() {
            String code = "class T {\n"
                    + "    /* { not a real brace } */\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void multiLineBlockComment() {
            String code = "class T {\n"
                    + "    /*\n"
                    + "    * comment\n"
                    + "    */\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedBlockCommentIgnored() {
            String code = "class T {\n"
                    + "/* misindented block comment */\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedMultiLineBlockCommentIgnored() {
            String code = "class T {\n"
                    + "  /*\n"
                    + "      * inside block\n"
                    + "  */\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedLineCommentIgnored() {
            String code = "class T {\n"
                    + "// misindented line comment\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedJavadocIgnored() {
            String code = "class T {\n"
                    + "/**\n"
                    + " * Javadoc\n"
                    + " */\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class StringLiterals {
        @Test
        void bracesInStringLiteralIgnored() {
            String code = "class T {\n"
                    + "    String s = \"{ }\";\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class Parentheses {
        @Test
        void parenNesting() {
            String code = "class T {\n"
                    + "    void m(\n"
                    + "        int x,\n"
                    + "        int y\n"
                    + "    ) {\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }
    }
}
