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

class SimpleIndentationRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new SimpleIndentationRule();
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
            String code = """
                    class T {
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void nestedBlocks() {
            String code = """
                    class T {
                        void m() {
                            int x = 1;
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void deepNesting() {
            String code = """
                    class T {
                        void m() {
                            if (true) {
                                int x = 1;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void emptyClass() {
            assertNoViolations("class T {\n}");
        }

        @Test
        void blankLinesIgnored() {
            String code = """
                    class T {
                    
                        int x;
                    
                    }""";
            assertNoViolations(code);
        }

        @Test
        void closingBraceAtCorrectLevel() {
            String code = """
                    class T {
                        void m() {
                        }
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class IncorrectIndentation {
        @Test
        void tooManySpaces() {
            String code = """
                    class T {
                            int x;
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void tooFewSpaces() {
            String code = """
                    class T {
                      int x;
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void noIndentInBlock() {
            String code = """
                    class T {
                    void m() {
                    }
                    }""";
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
            String code = """
                    class T {
                      int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void fourSpacesWithTwoSizeIndent() {
            String code = """
                    class T {
                        int x;
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void nestedTwoSpaceIndent() {
            String code = """
                    class T {
                      void m() {
                        int x = 1;
                      }
                    }""";
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
            String code = """
                    class T {
                        /* { not a real brace } */
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void multiLineBlockComment() {
            String code = """
                    class T {
                        /*
                        * comment
                        */
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedBlockCommentIgnored() {
            String code = """
                    class T {
                    /* misindented block comment */
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedMultiLineBlockCommentIgnored() {
            String code = """
                    class T {
                      /*
                          * inside block
                      */
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedLineCommentIgnored() {
            String code = """
                    class T {
                    // misindented line comment
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void badlyIndentedJavadocIgnored() {
            String code = """
                    class T {
                    /**
                     * Javadoc
                     */
                        int x;
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class StringLiterals {
        @Test
        void bracesInStringLiteralIgnored() {
            String code = """
                    class T {
                        String s = "{ }";
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class Parentheses {
        @Test
        void parenNesting() {
            String code = """
                    class T {
                        void m(
                            int x,
                            int y
                        ) {
                        }
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class SwitchCaseIndentation {
        @Test
        void correctSwitchCaseIndentation() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1:
                                    int y = 1;
                                    break;
                                case 2:
                                    int z = 2;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void caseBodyAtSameLevelAsLabel() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1:
                                int y = 1;
                                break;
                            }
                        }
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void defaultLabelBodyIndented() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                default:
                                    int y = 0;
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void multipleCaseLabelsFallThrough() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1:
                                case 2:
                                case 3:
                                    int y = 1;
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void arrowCaseNoExtraIndent() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1 -> System.out.println(1);
                                case 2 -> System.out.println(2);
                                default -> System.out.println(0);
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void arrowCaseWithBlock() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1 -> {
                                    System.out.println(1);
                                }
                                default -> {
                                    System.out.println(0);
                                }
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void nestedSwitchStatements() {
            String code = """
                    class T {
                        void m(int x, int y) {
                            switch (x) {
                                case 1:
                                    switch (y) {
                                        case 10:
                                            int z = 10;
                                            break;
                                        default:
                                            break;
                                    }
                                    break;
                                case 2:
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void nestedSwitchIncorrectInnerIndent() {
            String code = """
                    class T {
                        void m(int x, int y) {
                            switch (x) {
                                case 1:
                                    switch (y) {
                                        case 10:
                                        int z = 10;
                                        break;
                                    }
                                    break;
                            }
                        }
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void switchWithCaseContainingBlock() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1: {
                                    int y = 1;
                                    break;
                                }
                                default:
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void colonInTernaryNotConfused() {
            String code = """
                    class T {
                        void m(int x) {
                            int y = x > 0 ? 1 : 0;
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void colonInEnhancedForNotConfused() {
            String code = """
                    class T {
                        void m(int[] arr) {
                            for (int x : arr) {
                                System.out.println(x);
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void colonInMethodReferenceNotConfused() {
            String code = """
                    class T {
                        void m() {
                            Runnable r = System.out::println;
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void switchAfterCodeInMethod() {
            String code = """
                    class T {
                        void m(int x) {
                            int a = 1;
                            switch (x) {
                                case 1:
                                    break;
                            }
                            int b = 2;
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void caseBodyOverIndented() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1:
                                        break;
                            }
                        }
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }
    }
}
