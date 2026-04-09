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
            String code = """
                    class T {
                      int x;
                      int y;
                      void m() {
                        int z = 1;
                      }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void detectsThreeSpaceIndent() {
            String code = """
                    class T {
                       int x;
                       int y;
                       void m() {
                          int z = 1;
                       }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void detectsFourSpaceIndent() {
            String code = """
                    class T {
                        int x;
                        int y;
                        void m() {
                            int z = 1;
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void detectsEightSpaceIndent() {
            String code = """
                    class T {
                            int x;
                            int y;
                            void m() {
                                    int z = 1;
                            }
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class ConsistentWithDeviations {
        @Test
        void fourSpaceConventionWithOneDeviantLine() {
            String code = """
                    class T {
                        int a;
                        int b;
                        int c;
                        void m() {
                          int x = 1;
                        }
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void twoSpaceConventionWithOneDeviantLine() {
            String code = """
                    class T {
                      int a;
                      int b;
                      int c;
                      void m() {
                            int x = 1;
                      }
                    }""";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void correctLinesNotReported() {
            String code = """
                    class T {
                        int a;
                        int b;
                        int c;
                        void m() {
                          int deviant = 1;
                            int correct = 2;
                        }
                    }""";
            List<RuleViolation> violations = runRule(code);
            assertEquals(1, violations.size(),
                    String.format("Expected exactly 1 violation but found %d: %s",
                            violations.size(), violations));
            assertTrue(violations.getFirst().getDescription().contains("indented incorrectly"));
        }
    }

    @Nested
    class InconsistentFile {
        @Test
        void noMajorityConventionReportsFileViolation() {
            String code = """
                    class T {
                      int a;
                       int b;
                        int c;
                         int d;
                          int e;
                           int f;
                    }""";
            assertHasViolation(code, "consistent indentation");
        }

        @Test
        void inconsistentFileExactlyOneViolation() {
            String code = """
                    class T {
                      int a;
                       int b;
                        int c;
                         int d;
                          int e;
                           int f;
                    }""";
            List<RuleViolation> violations = runRule(code);
            long inconsistentViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("consistent indentation"))
                    .count();
            assertEquals(1, inconsistentViolations);
        }

        @Test
        void inconsistentFileExcludesPackageAndImports() {
            String code = """
                    package com.example;
                    
                    import java.util.List;
                    import java.util.Map;
                    
                    class T {
                      int a;
                       int b;
                        int c;
                         int d;
                          int e;
                           int f;
                    }""";
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
            String code = """
                    class T {
                      int a;
                       int b;
                        int c;
                         int d;
                          int e;
                           int f;
                    }""";
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
            String code = """
                    class T {
                        int a;
                        int b;
                        int c;
                        void m() {
                          int deviant = 1;
                        }
                    }""";
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
            String code = """
                    // just a comment
                    /* block comment */
                    """;
            assertNoViolations(code);
        }

        @Test
        void singleIndentedLineEstablishesConvention() {
            String code = """
                    class T {
                        int x;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void deepNestingConsistentTwoSpaces() {
            String code = """
                    class T {
                      void m() {
                        if (true) {
                          if (true) {
                            int x = 1;
                          }
                        }
                      }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void deepNestingConsistentFourSpaces() {
            String code = """
                    class T {
                        void m() {
                            if (true) {
                                if (true) {
                                    int x = 1;
                                }
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void blankLinesIgnored() {
            String code = """
                    class T {
                    
                        int x;
                    
                        int y;
                    
                    }""";
            assertNoViolations(code);
        }

        @Test
        void fileWithOnlyTopLevelDeclarations() {
            String code = """
                    class T {
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class SwitchCaseIndentation {
        @Test
        void detectsFourSpaceWithSwitchCase() {
            String code = """
                    class T {
                        void m(int x) {
                            switch (x) {
                                case 1:
                                    int y = 1;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void detectsTwoSpaceWithSwitchCase() {
            String code = """
                    class T {
                      void m(int x) {
                        switch (x) {
                          case 1:
                            int y = 1;
                            break;
                          default:
                            break;
                        }
                      }
                    }""";
            assertNoViolations(code);
        }

        @Test
        void arrowCaseDetectedCorrectly() {
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
    }

    @Nested
    class PackageAndImports {
        @Test
        void packageAndImportsAccepted() {
            String code = """
                    package com.example;
                    
                    import java.util.List;
                    
                    class T {
                        int x;
                        int y;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void packageAndImportsWithMixedIndent() {
            String code = """
                    package com.example;
                    
                    import java.util.List;
                    import java.util.Map;
                    
                    class T {
                        int x;
                        int y;
                        void m() {
                            int z = 1;
                        }
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
                        int x;
                        int y;
                    }""";
            assertNoViolations(code);
        }
    }

    @Nested
    class BlockComments {
        @Test
        void blockCommentsSkipped() {
            String code = """
                    class T {
                        /* { not a real brace } */
                        int x;
                        int y;
                    }""";
            assertNoViolations(code);
        }

        @Test
        void multiLineBlockCommentSkipped() {
            String code = """
                    class T {
                        /*
                        * comment
                        */
                        int x;
                        int y;
                    }""";
            assertNoViolations(code);
        }
    }
}

