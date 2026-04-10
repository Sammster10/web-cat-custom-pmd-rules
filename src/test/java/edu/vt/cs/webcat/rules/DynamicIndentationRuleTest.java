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

import static org.junit.jupiter.api.Assertions.*;

class DynamicIndentationRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new DynamicIndentationRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Dynamic indentation violation");
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
    class DetectsIndentSize {
        @Test
        void detectsTwoSpaceIndent() {
            String code = "class T {\n"
                    + "  int x;\n"
                    + "  int y;\n"
                    + "  void m() {\n"
                    + "    int z = 1;\n"
                    + "  }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void detectsThreeSpaceIndent() {
            String code = "class T {\n"
                    + "   int x;\n"
                    + "   int y;\n"
                    + "   void m() {\n"
                    + "      int z = 1;\n"
                    + "   }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void detectsFourSpaceIndent() {
            String code = "class T {\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "    void m() {\n"
                    + "        int z = 1;\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void detectsEightSpaceIndent() {
            String code = "class T {\n"
                    + "        int x;\n"
                    + "        int y;\n"
                    + "        void m() {\n"
                    + "                int z = 1;\n"
                    + "        }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class ConsistentWithDeviations {
        @Test
        void fourSpaceConventionWithOneDeviantLine() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    void m() {\n"
                    + "      int x = 1;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void twoSpaceConventionWithOneDeviantLine() {
            String code = "class T {\n"
                    + "  int a;\n"
                    + "  int b;\n"
                    + "  int c;\n"
                    + "  void m() {\n"
                    + "        int x = 1;\n"
                    + "  }\n"
                    + "}";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void correctLinesNotReported() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    void m() {\n"
                    + "      int deviant = 1;\n"
                    + "        int correct = 2;\n"
                    + "    }\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            assertEquals(1, violations.size(),
                    String.format("Expected exactly 1 violation but found %d: %s",
                            violations.size(), violations));
            assertTrue(violations.get(0).getDescription().contains("indented incorrectly"));
        }
    }

    @Nested
    class InconsistentFile {
        @Test
        void noMajorityConventionReportsFileViolation() {
            String code = "class T {\n"
                    + "  int a;\n"
                    + "   int b;\n"
                    + "    int c;\n"
                    + "     int d;\n"
                    + "      int e;\n"
                    + "       int f;\n"
                    + "}";
            assertHasViolation(code, "consistent indentation");
        }

        @Test
        void inconsistentFileExactlyOneViolation() {
            String code = "class T {\n"
                    + "  int a;\n"
                    + "   int b;\n"
                    + "    int c;\n"
                    + "     int d;\n"
                    + "      int e;\n"
                    + "       int f;\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            long inconsistentViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("consistent indentation"))
                    .count();
            assertEquals(1, inconsistentViolations);
        }

        @Test
        void inconsistentFileExcludesPackageAndImports() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "import java.util.Map;\n"
                    + "\n"
                    + "class T {\n"
                    + "  int a;\n"
                    + "   int b;\n"
                    + "    int c;\n"
                    + "     int d;\n"
                    + "      int e;\n"
                    + "       int f;\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            long inconsistentViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("consistent indentation"))
                    .count();
            assertEquals(1, inconsistentViolations);
            RuleViolation violation = violations.stream()
                    .filter(v -> v.getDescription().contains("consistent indentation"))
                    .findFirst().orElseThrow();
            assertTrue(violation.getBeginLine() > 4,
                    String.format("Violation should start after imports, but starts at line %d",
                            violation.getBeginLine()));
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
        void tabBanDisabledNoTabViolation() {
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
    class CustomMessages {
        @Test
        void customInconsistentFileMessage() {
            setRuleProperty(rule, "inconsistentFileMessage", "Fix your indentation!");
            String code = "class T {\n"
                    + "  int a;\n"
                    + "   int b;\n"
                    + "    int c;\n"
                    + "     int d;\n"
                    + "      int e;\n"
                    + "       int f;\n"
                    + "}";
            assertHasViolation(code, "Fix your indentation!");
        }

        @Test
        void customTabMessage() {
            setRuleProperty(rule, "tabViolationMessage", "No tabs allowed on line {0}!");
            String code = "class T {\n"
                    + "\tint x;\n"
                    + "}";
            assertHasViolation(code, "No tabs allowed on line");
        }

        @Test
        void customIndentMessage() {
            setRuleProperty(rule, "indentViolationMessage", "Wrong indent at line {0}: expected {1} got {2}");
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    void m() {\n"
                    + "      int deviant = 1;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Wrong indent at line");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void emptyClassNoViolations() {
            assertNoViolations("class T {\n}");
        }

        @Test
        void onlyCommentsAndBlanks() {
            String code = "// just a comment\n"
                    + "/* block comment */\n";
            assertNoViolations(code);
        }

        @Test
        void singleIndentedLineEstablishesConvention() {
            String code = "class T {\n"
                    + "    int x;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void deepNestingConsistentTwoSpaces() {
            String code = "class T {\n"
                    + "  void m() {\n"
                    + "    if (true) {\n"
                    + "      if (true) {\n"
                    + "        int x = 1;\n"
                    + "      }\n"
                    + "    }\n"
                    + "  }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void deepNestingConsistentFourSpaces() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            if (true) {\n"
                    + "                int x = 1;\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void blankLinesIgnored() {
            String code = "class T {\n"
                    + "\n"
                    + "    int x;\n"
                    + "\n"
                    + "    int y;\n"
                    + "\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void fileWithOnlyTopLevelDeclarations() {
            String code = "class T {\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class SwitchCaseIndentation {
        @Test
        void detectsFourSpaceWithSwitchCase() {
            String code = "class T {\n"
                    + "    void m(int x) {\n"
                    + "        switch (x) {\n"
                    + "            case 1:\n"
                    + "                int y = 1;\n"
                    + "                break;\n"
                    + "            default:\n"
                    + "                break;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void detectsTwoSpaceWithSwitchCase() {
            String code = "class T {\n"
                    + "  void m(int x) {\n"
                    + "    switch (x) {\n"
                    + "      case 1:\n"
                    + "        int y = 1;\n"
                    + "        break;\n"
                    + "      default:\n"
                    + "        break;\n"
                    + "    }\n"
                    + "  }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void arrowCaseDetectedCorrectly() {
            String code = "class T {\n"
                    + "    void m(int x) {\n"
                    + "        switch (x) {\n"
                    + "            case 1 -> System.out.println(1);\n"
                    + "            case 2 -> System.out.println(2);\n"
                    + "            default -> System.out.println(0);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class PackageAndImports {
        @Test
        void packageAndImportsAccepted() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "\n"
                    + "class T {\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void packageAndImportsWithMixedIndent() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "import java.util.Map;\n"
                    + "\n"
                    + "class T {\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "    void m() {\n"
                    + "        int z = 1;\n"
                    + "    }\n"
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
                    + "    int x;\n"
                    + "    int y;\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class BlockComments {
        @Test
        void blockCommentsSkipped() {
            String code = "class T {\n"
                    + "    /* { not a real brace } */\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void multiLineBlockCommentSkipped() {
            String code = "class T {\n"
                    + "    /*\n"
                    + "    * comment\n"
                    + "    */\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "}";
            assertNoViolations(code);
        }
    }
}

