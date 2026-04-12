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

class RightCurlyBlockRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new RightCurlyBlockRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Right curly brace violation");
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

    // ================================================================== //
    //  No violations – brace alone on its line                           //
    // ================================================================== //

    @Nested
    class NoViolations {

        @Test
        void closingBraceAloneOnLine() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1;\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void ifBlockClosingBraceAlone() {
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
        void emptyBlock() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void nestedEmptyBlocks() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void classClosingBraceNotChecked() {
            String code = "class T { int x; }";
            assertNoViolations(code);
        }

        @Test
        void anonymousClassBraceNotChecked() {
            String code = "class T {\n"
                    + "    Object o = new Object() {\n"
                    + "        public String toString() {\n"
                    + "            return \"\";\n"
                    + "        }\n"
                    + "    };\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    // ================================================================== //
    //  Violations – brace not alone on its line                          //
    // ================================================================== //

    @Nested
    class Violations {

        @Test
        void braceFollowedByElse() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        } else {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void braceFollowedByWhileInDoWhile() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        do {\n"
                    + "            int x = 1;\n"
                    + "        } while (true);\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void braceFollowedByCatch() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        try {\n"
                    + "            int x = 1;\n"
                    + "        } catch (Exception e) {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void braceFollowedByFinally() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        try {\n"
                    + "            int x = 1;\n"
                    + "        } finally {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void braceFollowedByComment() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        } // end if\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void codeBeforeBrace() {
            String code = "class T {\n"
                    + "    void m() { }\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void multipleBraceViolationsOnDifferentLines() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        } else {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertViolationCount(code, 1);
        }
    }

    // ================================================================== //
    //  Edge cases                                                        //
    // ================================================================== //

    @Nested
    class EdgeCases {

        @Test
        void emptyFile() {
            assertNoViolations("");
        }

        @Test
        void lambdaBlockBrace() {
            String code = "class T {\n"
                    + "    Runnable r = () -> {\n"
                    + "        System.out.println(1);\n"
                    + "    };\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }
    }

    // ================================================================== //
    //  Custom messages                                                   //
    // ================================================================== //

    @Nested
    class CustomMessages {

        @Test
        void customMessage() {
            setRuleProperty(rule, "message", "Brace not alone!");
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        } else {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Brace not alone!");
        }
    }
}

