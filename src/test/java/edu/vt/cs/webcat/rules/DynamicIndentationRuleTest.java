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

    // ---------------------------------------------------------------
    // Helper: builds a class with enough members for inference (8+)
    // ---------------------------------------------------------------

    private static String fourSpaceClass(String... bodyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("class T {\n");
        sb.append("    int a;\n");
        sb.append("    int b;\n");
        sb.append("    int c;\n");
        sb.append("    int d;\n");
        sb.append("    int e;\n");
        sb.append("    int f;\n");
        sb.append("    int g;\n");
        sb.append("    int h;\n");
        for (String line : bodyLines) {
            sb.append(line).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String twoSpaceClass(String... bodyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("class T {\n");
        sb.append("  int a;\n");
        sb.append("  int b;\n");
        sb.append("  int c;\n");
        sb.append("  int d;\n");
        sb.append("  int e;\n");
        sb.append("  int f;\n");
        sb.append("  int g;\n");
        sb.append("  int h;\n");
        for (String line : bodyLines) {
            sb.append(line).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String threeSpaceClass(String... bodyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("class T {\n");
        sb.append("   int a;\n");
        sb.append("   int b;\n");
        sb.append("   int c;\n");
        sb.append("   int d;\n");
        sb.append("   int e;\n");
        sb.append("   int f;\n");
        sb.append("   int g;\n");
        sb.append("   int h;\n");
        for (String line : bodyLines) {
            sb.append(line).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Inference success cases
    // ---------------------------------------------------------------

    @Nested
    class DetectsIndentSize {
        @Test
        void detectsTwoSpaceIndent() {
            assertNoViolations(twoSpaceClass(
                    "  void m() {",
                    "    int z = 1;",
                    "  }"));
        }

        @Test
        void detectsThreeSpaceIndent() {
            assertNoViolations(threeSpaceClass(
                    "   void m() {",
                    "      int z = 1;",
                    "   }"));
        }

        @Test
        void detectsFourSpaceIndent() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        int z = 1;",
                    "    }"));
        }

        @Test
        void detectsEightSpaceIndent() {
            String code = "class T {\n"
                    + "        int a;\n"
                    + "        int b;\n"
                    + "        int c;\n"
                    + "        int d;\n"
                    + "        int e;\n"
                    + "        int f;\n"
                    + "        int g;\n"
                    + "        int h;\n"
                    + "        void m() {\n"
                    + "                int z = 1;\n"
                    + "        }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Consistency violations
    // ---------------------------------------------------------------

    @Nested
    class ConsistentWithDeviations {
        @Test
        void fourSpaceConventionWithOneDeviantLine() {
            assertHasViolation(fourSpaceClass(
                    "    void m() {",
                    "      int deviant = 1;",
                    "    }"), "indented incorrectly");
        }

        @Test
        void twoSpaceConventionWithOneDeviantLine() {
            assertHasViolation(twoSpaceClass(
                    "  void m() {",
                    "        int deviant = 1;",
                    "  }"), "indented incorrectly");
        }

        @Test
        void correctLinesNotReported() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "      int deviant = 1;",
                    "        int correct = 2;",
                    "    }");
            List<RuleViolation> violations = runRule(code);
            long indentViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("indented incorrectly"))
                    .count();
            assertEquals(1, indentViolations,
                    String.format("Expected exactly 1 indent violation but found %d: %s",
                            indentViolations, violations));
        }
    }

    // ---------------------------------------------------------------
    // Inference failure
    // ---------------------------------------------------------------

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
                    + "        int g;\n"
                    + "         int h;\n"
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
                    + "        int g;\n"
                    + "         int h;\n"
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
                    + "        int g;\n"
                    + "         int h;\n"
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

    // ---------------------------------------------------------------
    // Tab banning
    // ---------------------------------------------------------------

    @Nested
    class TabBanning {
        @Test
        void tabOnLineReportsViolation() {
            String code = fourSpaceClass().replace("    int a;", "\tint a;");
            assertHasViolation(code, "tab character");
        }

        @Test
        void tabBanDisabledNoTabViolation() {
            setRuleProperty(rule, "banTabs", false);
            String code = fourSpaceClass().replace("    int a;", "\tint a;");
            List<RuleViolation> violations = runRule(code);
            boolean hasTabViolation = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("tab character"));
            assertFalse(hasTabViolation);
        }

        @Test
        void multipleTabLinesReported() {
            String code = fourSpaceClass()
                    .replace("    int a;", "\tint a;")
                    .replace("    int b;", "\tint b;")
                    .replace("    int c;", "\tint c;");
            List<RuleViolation> violations = runRule(code);
            long tabViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("tab character"))
                    .count();
            assertEquals(3, tabViolations);
        }

        @Test
        void tabInsideStringNotReported() {
            String code = fourSpaceClass(
                    "    String s = \"hello\\tworld\";");
            List<RuleViolation> violations = runRule(code);
            boolean hasTabViolation = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("tab character"));
            assertFalse(hasTabViolation);
        }
    }

    // ---------------------------------------------------------------
    // Custom messages
    // ---------------------------------------------------------------

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
                    + "        int g;\n"
                    + "         int h;\n"
                    + "}";
            assertHasViolation(code, "Fix your indentation!");
        }

        @Test
        void customTabMessage() {
            setRuleProperty(rule, "tabViolationMessage", "No tabs allowed on line {0}!");
            String code = fourSpaceClass().replace("    int a;", "\tint a;");
            assertHasViolation(code, "No tabs allowed on line");
        }

        @Test
        void customIndentMessage() {
            setRuleProperty(rule, "indentViolationMessage", "Wrong indent at line {0}: expected {1} got {2}");
            String code = fourSpaceClass(
                    "    void m() {",
                    "      int deviant = 1;",
                    "    }");
            assertHasViolation(code, "Wrong indent at line");
        }
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

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
        void deepNestingConsistentTwoSpaces() {
            String code = "class T {\n"
                    + "  int a;\n"
                    + "  int b;\n"
                    + "  int c;\n"
                    + "  int d;\n"
                    + "  int e;\n"
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
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "    int e;\n"
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
            assertNoViolations(fourSpaceClass(
                    "",
                    "    void m() {",
                    "",
                    "        int x = 1;",
                    "",
                    "    }"));
        }

        @Test
        void fileWithOnlyTopLevelDeclarations() {
            assertNoViolations("class T {\n}");
        }
    }

    // ---------------------------------------------------------------
    // Nested classes
    // ---------------------------------------------------------------

    @Nested
    class NestedClasses {
        @Test
        void nestedClassCorrectlyIndented() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    class Inner {\n"
                    + "        int c;\n"
                    + "        int d;\n"
                    + "        void m() {\n"
                    + "            int x = 1;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void nestedClassWrongFieldIndent() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    class Inner {\n"
                    + "    int c;\n"
                    + "        int d;\n"
                    + "        void m() {\n"
                    + "            int x = 1;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void nestedClassWrongClosingBrace() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    class Inner {\n"
                    + "        int c;\n"
                    + "        int d;\n"
                    + "        void m() {\n"
                    + "            int x = 1;\n"
                    + "        }\n"
                    + "        }\n"
                    + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void staticNestedClassCorrectlyIndented() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    static class StaticInner {\n"
                    + "        int c;\n"
                    + "        int d;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void doubleNestedClass() {
            String code = "class A {\n"
                    + "    class B {\n"
                    + "        class C {\n"
                    + "            int x;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void doubleNestedClassWrongIndent() {
            String code = "class A {\n"
                    + "    class B {\n"
                    + "        class C {\n"
                    + "        int x;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void nestedClassWithMethodBody() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    class Inner {\n"
                    + "        void m() {\n"
                    + "            if (true) {\n"
                    + "                int x = 1;\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void nestedClassTwoSpaceIndent() {
            String code = "class Outer {\n"
                    + "  int a;\n"
                    + "  int b;\n"
                    + "  class Inner {\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "  }\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Small files
    // ---------------------------------------------------------------

    @Nested
    class SmallFiles {
        @Test
        void twoFieldClassInfers() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void singleFieldClassInfers() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void singleFieldConsistentSixSpaceInfers() {
            String code = "class T {\n"
                    + "      int a;\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void twoFieldsInconsistentSmallFile() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "      int b;\n"
                    + "}\n";
            List<RuleViolation> violations = runRule(code);
            assertFalse(violations.isEmpty());
        }

        @Test
        void singleMethodClass() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void singleMethodTwoSpaceIndent() {
            String code = "class T {\n"
                    + "  void m() {\n"
                    + "    int x = 1;\n"
                    + "  }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void singleMethodWrongInnerIndent() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "    int x = 1;\n"
                    + "    }\n"
                    + "}\n";
            List<RuleViolation> violations = runRule(code);
            assertFalse(violations.isEmpty());
        }

        @Test
        void emptyClassNoViolation() {
            assertNoViolations("class T {\n}\n");
        }

        @Test
        void interfaceWithSingleMethod() {
            String code = "interface I {\n"
                    + "    void m();\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void enumWithConstants() {
            String code = "enum Color {\n"
                    + "    RED,\n"
                    + "    GREEN,\n"
                    + "    BLUE\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void smallFileInconsistentReported() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "  int b;\n"
                    + "}\n";
            List<RuleViolation> violations = runRule(code);
            assertFalse(violations.isEmpty());
        }
    }

    // ---------------------------------------------------------------
    // Switch / case indentation
    // ---------------------------------------------------------------

    @Nested
    class SwitchCaseIndentation {
        @Test
        void detectsFourSpaceWithSwitchCase() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "    int e;\n"
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
                    + "  int a;\n"
                    + "  int b;\n"
                    + "  int c;\n"
                    + "  int d;\n"
                    + "  int e;\n"
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
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "    int e;\n"
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

    // ---------------------------------------------------------------
    // Package / imports
    // ---------------------------------------------------------------

    @Nested
    class PackageAndImports {
        @Test
        void packageAndImportsAccepted() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "\n"
                    + fourSpaceClass();
            assertNoViolations(code);
        }

        @Test
        void packageAndImportsWithMethods() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "import java.util.Map;\n"
                    + "\n"
                    + fourSpaceClass(
                    "    void m() {",
                    "        int z = 1;",
                    "    }");
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // String literals
    // ---------------------------------------------------------------

    @Nested
    class StringLiterals {
        @Test
        void bracesInStringLiteralIgnored() {
            assertNoViolations(fourSpaceClass(
                    "    String s = \"{ }\";"));
        }
    }

    // ---------------------------------------------------------------
    // Block comments
    // ---------------------------------------------------------------

    @Nested
    class BlockComments {
        @Test
        void blockCommentsSkipped() {
            assertNoViolations(fourSpaceClass(
                    "    /* { not a real brace } */"));
        }

        @Test
        void multiLineBlockCommentSkipped() {
            assertNoViolations(fourSpaceClass(
                    "    /*",
                    "    * comment",
                    "    */"));
        }
    }

    // ---------------------------------------------------------------
    // Javadoc
    // ---------------------------------------------------------------

    @Nested
    class JavadocHandling {
        @Test
        void javadocInteriorIgnored() {
            assertNoViolations(fourSpaceClass(
                    "    /**",
                    "     * Some javadoc",
                    "     * @param x something",
                    "     */",
                    "    void m(int x) {",
                    "        int y = x;",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Nested blocks
    // ---------------------------------------------------------------

    @Nested
    class NestedBlocks {
        @Test
        void nestedIfBlocks() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            if (true) {",
                    "                int x = 1;",
                    "            }",
                    "        }",
                    "    }"));
        }

        @Test
        void whileLoop() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        while (true) {",
                    "            int x = 1;",
                    "            break;",
                    "        }",
                    "    }"));
        }

        @Test
        void forLoop() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        for (int i = 0; i < 10; i++) {",
                    "            int x = i;",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Annotations
    // ---------------------------------------------------------------

    @Nested
    class AnnotationHandling {
        @Test
        void standaloneAnnotationAtMemberDepth() {
            assertNoViolations(fourSpaceClass(
                    "    @Override",
                    "    void m() {",
                    "        int x = 1;",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Continuation lines — strict enforcement
    // ---------------------------------------------------------------

    @Nested
    class ContinuationLines {
        @Test
        void wrappedMethodArgumentsAtOneExtraLevel() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "            \"hello\");",
                    "    }"));
        }

        @Test
        void wrappedMethodArgumentsAtTwoExtraLevels() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "                \"hello\");",
                    "    }"));
        }

        @Test
        void wrappedArgumentsNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "             \"hello\");",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void wrappedArgumentsUnderIndented() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "    \"hello\");",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void chainedMethodCallsAtExactLevel() {
            assertNoViolations(fourSpaceClass(
                    "    String s = \"hello\"",
                    "            .trim()",
                    "            .toLowerCase();"));
        }

        @Test
        void chainedMethodCallsNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    String s = \"hello\"",
                    "          .trim()",
                    "          .toLowerCase();");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void chainedMethodCallsTwoSpaceFile() {
            assertNoViolations(twoSpaceClass(
                    "  String s = \"hello\"",
                    "      .trim()",
                    "      .toLowerCase();"));
        }

        @Test
        void chainedMethodCallsTwoSpaceFileNotMultiple() {
            String code = twoSpaceClass(
                    "  String s = \"hello\"",
                    "       .trim()",
                    "       .toLowerCase();");
            assertHasViolation(code, "multiple of 2");
        }

        @Test
        void wrappedBinaryExpressionAtExactLevel() {
            assertNoViolations(fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "            + anotherLongVariable",
                    "            - yetAnotherVariable;",
                    "    }"));
        }

        @Test
        void wrappedBinaryExpressionNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "             + anotherLongVariable;",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void wrappedTernaryAtExactLevel() {
            assertNoViolations(fourSpaceClass(
                    "    int m(boolean b) {",
                    "        return b",
                    "            ? 1",
                    "            : 0;",
                    "    }"));
        }

        @Test
        void wrappedTernaryNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    int m(boolean b) {",
                    "        return b",
                    "           ? 1",
                    "           : 0;",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void wrappedParameterListAtExactLevel() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int a,",
                    "            int b,",
                    "            int c) {",
                    "        int x = a;",
                    "    }"));
        }

        @Test
        void wrappedParameterListNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    void m(int a,",
                    "           int b,",
                    "           int c) {",
                    "        int x = a;",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void stringConcatenationWithPlusOnNextLine() {
            String code =
                    "class TestClass {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    int e;\n"
                            + "    int f;\n"
                            + "    int g;\n"
                            + "    int h;\n"
                            + "    public void testBigBig() {\n"
                            + "        assertExponentEquals(\"20\", \"64\",\n"
                            + "            \"184467440737095516160000000000000000000000000000\"\n"
                            + "                + \"000000000000000000000000000000000000\");\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void stringConcatenationNotMultipleOfUnit() {
            String code =
                    "class TestClass {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    int e;\n"
                            + "    int f;\n"
                            + "    int g;\n"
                            + "    int h;\n"
                            + "    public void testBigBig() {\n"
                            + "        assertExponentEquals(\"20\", \"64\",\n"
                            + "           \"misaligned\");\n"
                            + "    }\n"
                            + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void wrappedAnnotationArguments() {
            assertNoViolations(fourSpaceClass(
                    "    @SuppressWarnings({",
                    "        \"unchecked\",",
                    "        \"rawtypes\"",
                    "    })",
                    "    void m() {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void wrappedThrowsClause() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "            throws Exception {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void wrappedExtendsClause() {
            String code =
                    "class Base {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    int e;\n"
                            + "    int f;\n"
                            + "    int g;\n"
                            + "    int h;\n"
                            + "}\n"
                            + "\n"
                            + "class Child\n"
                            + "        extends Base {\n"
                            + "    int i;\n"
                            + "    int j;\n"
                            + "    int k;\n"
                            + "    int l;\n"
                            + "    int m;\n"
                            + "    int n;\n"
                            + "    int o;\n"
                            + "    int p;\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void multipleContinuationsAtDifferentDepths() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        foo(",
                    "            bar(",
                    "                baz()));",
                    "    }"));
        }

        @Test
        void closingParenNotFlaggedAsContinuation() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "            \"hello\"",
                    "        );",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Else / catch / finally
    // ---------------------------------------------------------------

    @Nested
    class ElseCatchFinally {
        @Test
        void ifElseProperlyAligned() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            int x = 1;",
                    "        } else {",
                    "            int y = 2;",
                    "        }",
                    "    }"));
        }

        @Test
        void tryCatchFinally() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        try {",
                    "            int x = 1;",
                    "        } catch (Exception e) {",
                    "            int y = 2;",
                    "        } finally {",
                    "            int z = 3;",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Lambda expressions
    // ---------------------------------------------------------------

    @Nested
    class LambdaExpressions {
        @Test
        void lambdaWithBlock() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        Runnable r = () -> {",
                    "            int x = 1;",
                    "        };",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Wrong indentation on specific structures
    // ---------------------------------------------------------------

    @Nested
    class WrongIndentation {
        @Test
        void wrongFieldIndent() {
            String code = fourSpaceClass()
                    .replace("    int a;", "      int a;");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void wrongMethodIndent() {
            String code = fourSpaceClass(
                    "      void m() {",
                    "          int x = 1;",
                    "      }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void wrongClosingBraceIndent() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        int x = 1;",
                    "      }");
            assertHasViolation(code, "indented incorrectly");
        }
    }

    // ---------------------------------------------------------------
    // Explicit constructor invocations (super/this)
    // ---------------------------------------------------------------

    @Nested
    class ConstructorInvocations {
        @Test
        void superCallCorrectlyIndented() {
            String code =
                    "import java.util.NoSuchElementException;\n"
                            + "\n"
                            + "public class BigNumCalculator extends Stack<BigNum> {\n"
                            + "\n"
                            + "    public BigNumCalculator() {\n"
                            + "        super();\n"
                            + "    }\n"
                            + "\n"
                            + "    public void add() {\n"
                            + "        this.push(this.pop().add(this.pop()));\n"
                            + "    }\n"
                            + "\n"
                            + "    public void multiply() {\n"
                            + "        this.push(this.pop().multiply(this.pop()));\n"
                            + "    }\n"
                            + "\n"
                            + "    public void exponentiate() {\n"
                            + "        BigNum exponent = this.pop();\n"
                            + "        this.push(this.pop().exponentiate(exponent));\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void thisCallCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    Foo(int x) {",
                    "        this.x = x;",
                    "    }",
                    "",
                    "    Foo() {",
                    "        this(0);",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // For-each loops
    // ---------------------------------------------------------------

    @Nested
    class ForEachLoops {
        @Test
        void forEachCorrectlyIndented() {
            String code =
                    "class BigNum {\n"
                            + "    int[] digits;\n"
                            + "    int x;\n"
                            + "    int y;\n"
                            + "    int z;\n"
                            + "    int w;\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    private BigNum multiplyNumberByDigit(BigNum number, int digit) {\n"
                            + "        int carry = 0;\n"
                            + "        for (int numDigit : number.digits) {\n"
                            + "            int product = numDigit * digit + carry;\n"
                            + "            carry = product / 10;\n"
                            + "        }\n"
                            + "        if (carry > 0) {\n"
                            + "            carry = 0;\n"
                            + "        }\n"
                            + "        return new BigNum(carry);\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void traditionalForLoopCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        for (int i = 0; i < 10; i++) {",
                    "            int x = i;",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Operator continuation lines — strict enforcement
    // ---------------------------------------------------------------

    @Nested
    class OperatorContinuation {
        @Test
        void plusOperatorAtMultipleOfUnit() {
            assertNoViolations(fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "            + anotherLongVariable;",
                    "    }"));
        }

        @Test
        void minusOperatorAtMultipleOfUnit() {
            assertNoViolations(fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "            - anotherLongVariable;",
                    "    }"));
        }

        @Test
        void logicalAndOperatorAtMultipleOfUnit() {
            assertNoViolations(fourSpaceClass(
                    "    boolean test(boolean a, boolean b) {",
                    "        return a",
                    "            && b;",
                    "    }"));
        }

        @Test
        void logicalOrOperatorAtMultipleOfUnit() {
            assertNoViolations(fourSpaceClass(
                    "    boolean test(boolean a, boolean b) {",
                    "        return a",
                    "            || b;",
                    "    }"));
        }

        @Test
        void operatorContinuationNotMultipleOfUnit() {
            String code = fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "          + anotherLongVariable;",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void multipleOperatorsEachAtMultipleOfUnit() {
            assertNoViolations(fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "            + anotherLongVariable",
                    "            - yetAnotherVariable;",
                    "    }"));
        }

        @Test
        void operatorsAtTwoExtraLevels() {
            assertNoViolations(fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "                + anotherLongVariable",
                    "                - yetAnotherVariable;",
                    "    }"));
        }

        @Test
        void operatorsUnderIndented() {
            String code = fourSpaceClass(
                    "    int compute() {",
                    "        return longVariableName",
                    "    + anotherLongVariable;",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void operatorsTwoSpaceFileAtMultiple() {
            assertNoViolations(twoSpaceClass(
                    "  int compute() {",
                    "    return longVariableName",
                    "        + anotherLongVariable;",
                    "  }"));
        }

        @Test
        void operatorsTwoSpaceFileNotMultiple() {
            String code = twoSpaceClass(
                    "  int compute() {",
                    "    return longVariableName",
                    "       + anotherLongVariable;",
                    "  }");
            assertHasViolation(code, "multiple of 2");
        }
    }

    // ---------------------------------------------------------------
    // Strict continuation: additional edge cases
    // ---------------------------------------------------------------

    @Nested
    class StrictContinuationEdgeCases {
        @Test
        void threeSpaceContinuationAtExactMultiple() {
            assertNoViolations(threeSpaceClass(
                    "   void m() {",
                    "      System.out.println(",
                    "         \"hello\");",
                    "   }"));
        }

        @Test
        void threeSpaceContinuationNotMultiple() {
            String code = threeSpaceClass(
                    "   void m() {",
                    "      System.out.println(",
                    "          \"hello\");",
                    "   }");
            assertHasViolation(code, "multiple of 3");
        }

        @Test
        void threeSpaceContinuationUnderIndented() {
            String code = threeSpaceClass(
                    "   void m() {",
                    "      System.out.println(",
                    "   \"hello\");",
                    "   }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void wrappedArrayInitializerAtMultiple() {
            assertNoViolations(fourSpaceClass(
                    "    int[] arr = {",
                    "        1,",
                    "        2,",
                    "        3",
                    "    };"));
        }

        @Test
        void wrappedArrayInitializerNotMultiple() {
            String code = fourSpaceClass(
                    "    int[] arr = {",
                    "          1,",
                    "          2,",
                    "    };");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void closingParenAtStatementDepthNoViolation() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        methodCall(",
                    "            arg1,",
                    "            arg2",
                    "        );",
                    "    }"));
        }

        @Test
        void closingBracketAtStatementDepthNoViolation() {
            assertNoViolations(fourSpaceClass(
                    "    int[] arr = new int[]{",
                    "        1,",
                    "        2",
                    "    };"));
        }

        @Test
        void nestedMethodCallContinuationsAtMultiples() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        outer(",
                    "            inner(",
                    "                deepArg),",
                    "            anotherArg);",
                    "    }"));
        }

        @Test
        void nestedMethodCallNotMultiple() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        outer(",
                    "            inner(",
                    "                 deepArg));",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void continuationInsideNestedBlock() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            System.out.println(",
                    "                \"nested\");",
                    "        }",
                    "    }"));
        }

        @Test
        void continuationInsideNestedBlockNotMultiple() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            System.out.println(",
                    "                 \"nested\");",
                    "        }",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void multipleWrappedLinesAllMultiples() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int a,",
                    "            int b,",
                    "            int c,",
                    "            int d) {",
                    "        int x = a;",
                    "    }"));
        }

        @Test
        void continuationAtThreeExtraLevels() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "                        \"deeply indented\");",
                    "    }"));
        }

        @Test
        void operatorContinuationWithTernaryInBlock() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            int x = condition",
                    "                ? value1",
                    "                : value2;",
                    "        }",
                    "    }"));
        }

        @Test
        void operatorContinuationWithTernaryInBlockNotMultiple() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        if (true) {",
                    "            int x = condition",
                    "                 ? value1",
                    "                 : value2;",
                    "        }",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void builderPatternAtMultiple() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        Builder b = new Builder()",
                    "            .setName(\"a\")",
                    "            .setValue(1)",
                    "            .build();",
                    "    }"));
        }

        @Test
        void builderPatternNotMultiple() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        Builder b = new Builder()",
                    "             .setName(\"a\")",
                    "             .setValue(1)",
                    "             .build();",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void twoSpaceBuilderPattern() {
            assertNoViolations(twoSpaceClass(
                    "  void m() {",
                    "    Builder b = new Builder()",
                    "        .setName(\"a\")",
                    "        .setValue(1)",
                    "        .build();",
                    "  }"));
        }

        @Test
        void closingParenOnOwnLineTwoSpaceFile() {
            assertNoViolations(twoSpaceClass(
                    "  void m() {",
                    "    methodCall(",
                    "        arg1,",
                    "        arg2",
                    "    );",
                    "  }"));
        }

        @Test
        void wrappedImplementsClause() {
            String code =
                    "class Impl\n"
                            + "        implements Runnable {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    int e;\n"
                            + "    int f;\n"
                            + "    int g;\n"
                            + "    int h;\n"
                            + "    public void run() {\n"
                            + "        int x = 1;\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void wrappedImplementsClauseNotMultiple() {
            String code =
                    "class Impl\n"
                            + "       implements Runnable {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    int e;\n"
                            + "    int f;\n"
                            + "    int g;\n"
                            + "    int h;\n"
                            + "    public void run() {\n"
                            + "        int x = 1;\n"
                            + "    }\n"
                            + "}\n";
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void wrappedThrowsClauseNotMultiple() {
            String code = fourSpaceClass(
                    "    void m()",
                    "           throws Exception {",
                    "        int x = 1;",
                    "    }");
            assertHasViolation(code, "multiple of 4");
        }

        @Test
        void multiLineAnnotationNotMultiple() {
            String code = fourSpaceClass(
                    "    @SuppressWarnings({",
                    "      \"unchecked\"",
                    "    })",
                    "    void m() {",
                    "        int x = 1;",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void continuationViolationShowsMultipleMessage() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "             \"hello\");",
                    "    }");
            List<RuleViolation> violations = runRule(code);
            boolean hasMultipleMsg = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("multiple of 4"));
            assertTrue(hasMultipleMsg,
                    String.format("Expected violation about 'multiple of 4' but found: %s",
                            violations));
        }

        @Test
        void underIndentedContinuationShowsAtLeastMessage() {
            String code = fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "    \"hello\");",
                    "    }");
            List<RuleViolation> violations = runRule(code);
            boolean hasAtLeast = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("at least"));
            assertTrue(hasAtLeast,
                    String.format("Expected violation about 'at least' but found: %s",
                            violations));
        }
    }

    // ---------------------------------------------------------------
    // Records
    // ---------------------------------------------------------------

    @Nested
    class Records {
        @Test
        void simpleRecord() {
            String code = "record Point(int x, int y) {\n"
                    + "    int sum() {\n"
                    + "        return x + y;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void recordWithCompactConstructor() {
            String code = "record Range(int lo, int hi) {\n"
                    + "    Range {\n"
                    + "        if (lo > hi) {\n"
                    + "            throw new IllegalArgumentException();\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void nestedRecordInClass() {
            String code = "class Outer {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    record Inner(int x) {\n"
                    + "        int doubled() {\n"
                    + "            return x * 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Enums with bodies
    // ---------------------------------------------------------------

    @Nested
    class EnumBodies {
        @Test
        void enumWithMethods() {
            String code = "enum Direction {\n"
                    + "    NORTH,\n"
                    + "    SOUTH,\n"
                    + "    EAST,\n"
                    + "    WEST;\n"
                    + "\n"
                    + "    boolean isVertical() {\n"
                    + "        return this == NORTH || this == SOUTH;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void enumWithConstructor() {
            String code = "enum Planet {\n"
                    + "    MERCURY(1),\n"
                    + "    VENUS(2),\n"
                    + "    EARTH(3);\n"
                    + "\n"
                    + "    final int order;\n"
                    + "\n"
                    + "    Planet(int order) {\n"
                    + "        this.order = order;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void enumWithWrongMethodIndent() {
            String code = "enum Direction {\n"
                    + "    NORTH,\n"
                    + "    SOUTH,\n"
                    + "    EAST,\n"
                    + "    WEST;\n"
                    + "\n"
                    + "    int x;\n"
                    + "    int y;\n"
                    + "    int z;\n"
                    + "\n"
                    + "  boolean isVertical() {\n"
                    + "      return this == NORTH;\n"
                    + "  }\n"
                    + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }
    }

    // ---------------------------------------------------------------
    // Annotation types
    // ---------------------------------------------------------------

    @Nested
    class AnnotationTypes {
        @Test
        void annotationTypeDeclaration() {
            String code = "@interface MyAnnotation {\n"
                    + "    String value();\n"
                    + "    int count() default 0;\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Do-while loops
    // ---------------------------------------------------------------

    @Nested
    class DoWhileLoops {
        @Test
        void doWhileCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        do {",
                    "            int x = 1;",
                    "        } while (true);",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Synchronized blocks
    // ---------------------------------------------------------------

    @Nested
    class SynchronizedBlocks {
        @Test
        void synchronizedBlock() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        synchronized (this) {",
                    "            int x = 1;",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Try-with-resources
    // ---------------------------------------------------------------

    @Nested
    class TryWithResources {
        @Test
        void tryWithResourcesCorrectlyIndented() {
            String code =
                    "import java.io.*;\n"
                            + "\n"
                            + "class T {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    void m() throws Exception {\n"
                            + "        try (InputStream is = new FileInputStream(\"f\")) {\n"
                            + "            int x = is.read();\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void tryWithResourcesOnSeparateLine() {
            String code =
                    "import java.io.*;\n"
                            + "import java.util.*;\n"
                            + "\n"
                            + "class T {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    void m() throws Exception {\n"
                            + "        try (\n"
                            + "            Scanner fileInput = new Scanner(new File(\"x\"))\n"
                            + "            ) {\n"
                            + "            int x = fileInput.nextInt();\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Anonymous inner classes
    // ---------------------------------------------------------------

    @Nested
    class AnonymousClasses {
        @Test
        void anonymousClassCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        Runnable r = new Runnable() {",
                    "            public void run() {",
                    "                int x = 1;",
                    "            }",
                    "        };",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Multiple annotations
    // ---------------------------------------------------------------

    @Nested
    class MultipleAnnotations {
        @Test
        void twoAnnotationsOnMethod() {
            assertNoViolations(fourSpaceClass(
                    "    @Override",
                    "    @SuppressWarnings(\"all\")",
                    "    void m() {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void inlineAnnotationOnSameLine() {
            assertNoViolations(fourSpaceClass(
                    "    @Override void m() {",
                    "        int x = 1;",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Braceless if / else / for / while
    // ---------------------------------------------------------------

    @Nested
    class BracelessBodies {
        @Test
        void ifWithoutBraces() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true)",
                    "            System.out.println(1);",
                    "    }"));
        }

        @Test
        void ifElseWithoutBraces() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        if (true)",
                    "            System.out.println(1);",
                    "        else",
                    "            System.out.println(2);",
                    "    }"));
        }

        @Test
        void whileWithoutBraces() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        while (false)",
                    "            System.out.println(1);",
                    "    }"));
        }

        @Test
        void forWithoutBraces() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        for (int i = 0; i < 1; i++)",
                    "            System.out.println(i);",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Else-if chains
    // ---------------------------------------------------------------

    @Nested
    class ElseIfChains {
        @Test
        void elseIfChain() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int x) {",
                    "        if (x == 1) {",
                    "            System.out.println(1);",
                    "        } else if (x == 2) {",
                    "            System.out.println(2);",
                    "        } else if (x == 3) {",
                    "            System.out.println(3);",
                    "        } else {",
                    "            System.out.println(0);",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Switch edge cases
    // ---------------------------------------------------------------

    @Nested
    class SwitchEdgeCases {
        @Test
        void switchWithFallThrough() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int x) {",
                    "        switch (x) {",
                    "            case 1:",
                    "            case 2:",
                    "                System.out.println(x);",
                    "                break;",
                    "            default:",
                    "                break;",
                    "        }",
                    "    }"));
        }

        @Test
        void switchWrongCaseIndent() {
            String code = fourSpaceClass(
                    "    void m(int x) {",
                    "        switch (x) {",
                    "        case 1:",
                    "            break;",
                    "        }",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void switchWrongStatementIndent() {
            String code = fourSpaceClass(
                    "    void m(int x) {",
                    "        switch (x) {",
                    "            case 1:",
                    "            break;",
                    "        }",
                    "    }");
            assertHasViolation(code, "indented incorrectly");
        }

        @Test
        void nestedSwitch() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int x, int y) {",
                    "        switch (x) {",
                    "            case 1:",
                    "                switch (y) {",
                    "                    case 10:",
                    "                        System.out.println(10);",
                    "                        break;",
                    "                    default:",
                    "                        break;",
                    "                }",
                    "                break;",
                    "            default:",
                    "                break;",
                    "        }",
                    "    }"));
        }

        @Test
        void switchWithYield() {
            String code = fourSpaceClass(
                    "    int m(int x) {",
                    "        return switch (x) {",
                    "            case 1 -> 10;",
                    "            case 2 -> {",
                    "                int result = x * 5;",
                    "                yield result;",
                    "            }",
                    "            default -> 0;",
                    "        };",
                    "    }");
            assertNoViolations(code);
        }

        @Test
        void switchTwoSpaceWrongCase() {
            String code = twoSpaceClass(
                    "  void m(int x) {",
                    "    switch (x) {",
                    "    case 1:",
                    "        break;",
                    "    }",
                    "  }");
            assertHasViolation(code, "indented incorrectly");
        }
    }

    // ---------------------------------------------------------------
    // Lambda edge cases
    // ---------------------------------------------------------------

    @Nested
    class LambdaEdgeCases {
        @Test
        void expressionLambda() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        Runnable r = () -> System.out.println(1);",
                    "    }"));
        }

        @Test
        void multiLineLambdaBlock() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        Runnable r = () -> {",
                    "            System.out.println(1);",
                    "            System.out.println(2);",
                    "        };",
                    "    }"));
        }

        @Test
        void lambdaAsArgument() {
            assertNoViolations(fourSpaceClass(
                    "    void m(java.util.List<String> list) {",
                    "        list.forEach(s -> {",
                    "            System.out.println(s);",
                    "        });",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Text blocks
    // ---------------------------------------------------------------

    @Nested
    class TextBlockHandling {
        @Test
        void textBlockInteriorNotEnforced() {
            assertNoViolations(fourSpaceClass(
                    "    String s = \"\"\"",
                    "badly indented content",
                    "    that would normally fail",
                    "            \"\"\";"));
        }

        @Test
        void textBlockWithSurroundingCode() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        String s = \"\"\"",
                    "            hello",
                    "            world",
                    "            \"\"\";",
                    "        System.out.println(s);",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Multiple classes in one file
    // ---------------------------------------------------------------

    @Nested
    class MultipleClasses {
        @Test
        void twoClassesSameFile() {
            String code = "class A {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "}\n"
                    + "\n"
                    + "class B {\n"
                    + "    int d;\n"
                    + "    int e;\n"
                    + "    int f;\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void twoClassesOneWrong() {
            String code = "class A {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "}\n"
                    + "\n"
                    + "class B {\n"
                    + "    int d;\n"
                    + "      int e;\n"
                    + "    int f;\n"
                    + "}\n";
            assertHasViolation(code, "indented incorrectly");
        }
    }

    // ---------------------------------------------------------------
    // Mixed comments and code
    // ---------------------------------------------------------------

    @Nested
    class MixedComments {
        @Test
        void lineCommentsBetweenStatements() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        // a comment",
                    "        int x = 1;",
                    "        // another comment",
                    "        int y = 2;",
                    "    }"));
        }

        @Test
        void javadocOnField() {
            assertNoViolations(fourSpaceClass(
                    "    /** field doc */",
                    "    int x;"));
        }

        @Test
        void multiLineJavadocBadlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    /**",
                    "  * badly indented javadoc line",
                    "     */",
                    "    void m() {",
                    "        int x = 1;",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Static initializer
    // ---------------------------------------------------------------

    @Nested
    class StaticInitializer {
        @Test
        void staticInitializerBlock() {
            assertNoViolations(fourSpaceClass(
                    "    static {",
                    "        System.out.println(1);",
                    "    }"));
        }

        @Test
        void instanceInitializerBlock() {
            assertNoViolations(fourSpaceClass(
                    "    {",
                    "        System.out.println(1);",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Abstract classes / methods
    // ---------------------------------------------------------------

    @Nested
    class AbstractDeclarations {
        @Test
        void abstractMethodNoBody() {
            String code = "abstract class Base {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    abstract void m();\n"
                    + "    abstract void n();\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Wrapped implements with multiple interfaces
    // ---------------------------------------------------------------

    @Nested
    class WrappedImplementsMultiple {
        @Test
        void multipleImplements() {
            String code =
                    "class Impl\n"
                            + "        implements Runnable,\n"
                            + "        java.io.Serializable {\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "    int d;\n"
                            + "    public void run() {\n"
                            + "        int x = 1;\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Wrapped conditional in if/while/for
    // ---------------------------------------------------------------

    @Nested
    class WrappedConditions {
        @Test
        void wrappedIfCondition() {
            assertNoViolations(fourSpaceClass(
                    "    void m(boolean a, boolean b) {",
                    "        if (a",
                    "                && b) {",
                    "            System.out.println(1);",
                    "        }",
                    "    }"));
        }

        @Test
        void wrappedWhileCondition() {
            assertNoViolations(fourSpaceClass(
                    "    void m(boolean a, boolean b) {",
                    "        while (a",
                    "                && b) {",
                    "            System.out.println(1);",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Multiple violations in one file
    // ---------------------------------------------------------------

    @Nested
    class MultipleViolations {
        @Test
        void twoDistinctViolationsReported() {
            String code = fourSpaceClass(
                    "      void m() {",
                    "        int x = 1;",
                    "      }",
                    "      void n() {",
                    "        int y = 2;",
                    "      }");
            List<RuleViolation> violations = runRule(code);
            long indentViolations = violations.stream()
                    .filter(v -> v.getDescription().contains("indented incorrectly"))
                    .count();
            assertTrue(indentViolations >= 2,
                    String.format("Expected at least 2 indent violations but found %d: %s",
                            indentViolations, violations));
        }

        @Test
        void tabAndIndentViolationsSameFile() {
            String code = fourSpaceClass(
                    "\tvoid m() {",
                    "      int x = 1;",
                    "    }");
            List<RuleViolation> violations = runRule(code);
            boolean hasTab = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("tab"));
            boolean hasIndent = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("indented incorrectly"));
            assertTrue(hasTab, "Expected tab violation");
            assertTrue(hasIndent, "Expected indent violation");
        }
    }

    // ---------------------------------------------------------------
    // Violation message content verification
    // ---------------------------------------------------------------

    @Nested
    class ViolationMessages {
        @Test
        void baseViolationShowsExpectedAndActual() {
            String code = fourSpaceClass(
                    "      void m() {",
                    "          int x = 1;",
                    "      }");
            List<RuleViolation> violations = runRule(code);
            boolean hasMessage = violations.stream().anyMatch(v -> {
                String desc = v.getDescription();
                return desc.contains("Expected") && desc.contains("spaces")
                        && desc.contains("found");
            });
            assertTrue(hasMessage,
                    String.format("Expected message with expected/found but got: %s", violations));
        }

        @Test
        void inconsistentFileMessageIsCustomizable() {
            setRuleProperty(rule, "inconsistentFileMessage", "Custom inconsistent: {0}");
            String code = "class T {\n"
                    + "  int a;\n"
                    + "   int b;\n"
                    + "    int c;\n"
                    + "     int d;\n"
                    + "      int e;\n"
                    + "       int f;\n"
                    + "        int g;\n"
                    + "         int h;\n"
                    + "}";
            assertHasViolation(code, "Custom inconsistent:");
        }
    }

    // ---------------------------------------------------------------
    // Closing delimiters on own line
    // ---------------------------------------------------------------

    @Nested
    class ClosingDelimiters {
        @Test
        void closingBracketAlone() {
            assertNoViolations(fourSpaceClass(
                    "    int[] arr = new int[]{",
                    "        1,",
                    "        2",
                    "    };"));
        }

        @Test
        void closingParenAlone() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        System.out.println(",
                    "            \"hello\"",
                    "        );",
                    "    }"));
        }

        @Test
        void closingParenWithSemicolon() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        foo(",
                    "            1,",
                    "            2",
                    "        );",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Tab edge cases
    // ---------------------------------------------------------------

    @Nested
    class TabEdgeCases {
        @Test
        void tabInMiddleOfCodeNotReported() {
            String code = fourSpaceClass(
                    "    String s = \"a\\tb\";");
            List<RuleViolation> violations = runRule(code);
            boolean hasTab = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("tab"));
            assertFalse(hasTab);
        }

        @Test
        void tabOnlyLinesReported() {
            String code = fourSpaceClass().replace("    int a;", "\t\tint a;");
            assertHasViolation(code, "tab character");
        }

        @Test
        void mixedTabAndSpaceInIndentation() {
            String code = fourSpaceClass().replace("    int a;", "  \tint a;");
            assertHasViolation(code, "tab character");
        }
    }

    // ---------------------------------------------------------------
    // Interfaces
    // ---------------------------------------------------------------

    @Nested
    class Interfaces {
        @Test
        void interfaceWithDefaultMethod() {
            String code = "interface I {\n"
                    + "    int a = 1;\n"
                    + "    int b = 2;\n"
                    + "    default void m() {\n"
                    + "        System.out.println(a);\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }

        @Test
        void interfaceWithStaticMethod() {
            String code = "interface I {\n"
                    + "    int a = 1;\n"
                    + "    int b = 2;\n"
                    + "    static void m() {\n"
                    + "        System.out.println(a);\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Assert statement
    // ---------------------------------------------------------------

    @Nested
    class AssertStatement {
        @Test
        void assertCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        assert true;",
                    "    }"));
        }

        @Test
        void assertWithMessage() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        assert true : \"msg\";",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Throw statement
    // ---------------------------------------------------------------

    @Nested
    class ThrowStatement {
        @Test
        void throwCorrectlyIndented() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        throw new RuntimeException();",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Return statement varieties
    // ---------------------------------------------------------------

    @Nested
    class ReturnVarieties {
        @Test
        void returnVoid() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        return;",
                    "    }"));
        }

        @Test
        void returnExpression() {
            assertNoViolations(fourSpaceClass(
                    "    int m() {",
                    "        return 42;",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Nested class inside enum
    // ---------------------------------------------------------------

    @Nested
    class NestedInEnum {
        @Test
        void classInsideEnum() {
            String code = "enum E {\n"
                    + "    A,\n"
                    + "    B;\n"
                    + "\n"
                    + "    static class Helper {\n"
                    + "        int x;\n"
                    + "        int y;\n"
                    + "    }\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Break / continue
    // ---------------------------------------------------------------

    @Nested
    class BreakContinue {
        @Test
        void breakInLoop() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        for (int i = 0; i < 10; i++) {",
                    "            if (i == 5) {",
                    "                break;",
                    "            }",
                    "        }",
                    "    }"));
        }

        @Test
        void continueInLoop() {
            assertNoViolations(fourSpaceClass(
                    "    void m() {",
                    "        for (int i = 0; i < 10; i++) {",
                    "            if (i == 5) {",
                    "                continue;",
                    "            }",
                    "        }",
                    "    }"));
        }
    }

    // ---------------------------------------------------------------
    // Real-world-style file
    // ---------------------------------------------------------------

    @Nested
    class RealWorldFiles {
        @Test
        void calculatorClassNoViolations() {
            String code =
                    "import java.util.NoSuchElementException;\n"
                            + "\n"
                            + "public class BigNumCalculator {\n"
                            + "\n"
                            + "    private int[] stack;\n"
                            + "    private int size;\n"
                            + "\n"
                            + "    public BigNumCalculator() {\n"
                            + "        stack = new int[10];\n"
                            + "        size = 0;\n"
                            + "    }\n"
                            + "\n"
                            + "    public void push(int value) {\n"
                            + "        if (size >= stack.length) {\n"
                            + "            throw new RuntimeException(\"full\");\n"
                            + "        }\n"
                            + "        stack[size++] = value;\n"
                            + "    }\n"
                            + "\n"
                            + "    public int pop() {\n"
                            + "        if (size == 0) {\n"
                            + "            throw new NoSuchElementException();\n"
                            + "        }\n"
                            + "        return stack[--size];\n"
                            + "    }\n"
                            + "\n"
                            + "    /**\n"
                            + "     * Adds top two elements.\n"
                            + "     *\n"
                            + "     * @throws NoSuchElementException if fewer than 2.\n"
                            + "     */\n"
                            + "    public void add() {\n"
                            + "        push(pop() + pop());\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void bigNumMultiplyMethod() {
            String code =
                    "class BigNum {\n"
                            + "    int[] digits;\n"
                            + "    int x;\n"
                            + "    int y;\n"
                            + "    int z;\n"
                            + "    int w;\n"
                            + "    int a;\n"
                            + "    int b;\n"
                            + "    int c;\n"
                            + "\n"
                            + "    private BigNum multiplyNumberByDigit(BigNum number, int digit) {\n"
                            + "        int carry = 0;\n"
                            + "        for (int numDigit : number.digits) {\n"
                            + "            int product = numDigit * digit + carry;\n"
                            + "            carry = product / 10;\n"
                            + "        }\n"
                            + "        if (carry > 0) {\n"
                            + "            carry = 0;\n"
                            + "        }\n"
                            + "        return new BigNum(carry);\n"
                            + "    }\n"
                            + "}\n";
            assertNoViolations(code);
        }

        @Test
        void fileWithEverythingTwoSpaces() {
            String code = "package com.example;\n"
                    + "\n"
                    + "import java.util.List;\n"
                    + "\n"
                    + "class Service {\n"
                    + "  private int count;\n"
                    + "  private String name;\n"
                    + "\n"
                    + "  Service(String name) {\n"
                    + "    this.name = name;\n"
                    + "    this.count = 0;\n"
                    + "  }\n"
                    + "\n"
                    + "  void process(List<String> items) {\n"
                    + "    for (String item : items) {\n"
                    + "      if (item != null) {\n"
                    + "        count++;\n"
                    + "      } else {\n"
                    + "        System.out.println(\"null\");\n"
                    + "      }\n"
                    + "    }\n"
                    + "  }\n"
                    + "\n"
                    + "  int getCount() {\n"
                    + "    return count;\n"
                    + "  }\n"
                    + "}\n";
            assertNoViolations(code);
        }
    }

    // ---------------------------------------------------------------
    // Allman / BSD brace style — opening brace on its own line
    // ---------------------------------------------------------------

    @Nested
    class AllmanBraceStyle {

        @Test
        void methodWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void constructorWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    T()",
                    "    {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void ifWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        if (true)",
                    "        {",
                    "            int x = 1;",
                    "        }",
                    "    }"));
        }

        @Test
        void ifElseWithBracesOnOwnLines() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        if (true)",
                    "        {",
                    "            int x = 1;",
                    "        }",
                    "        else",
                    "        {",
                    "            int y = 2;",
                    "        }",
                    "    }"));
        }

        @Test
        void whileWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        while (true)",
                    "        {",
                    "            break;",
                    "        }",
                    "    }"));
        }

        @Test
        void forWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        for (int i = 0; i < 10; i++)",
                    "        {",
                    "            int x = i;",
                    "        }",
                    "    }"));
        }

        @Test
        void foreachWithBraceOnOwnLine() {
            String code = "import java.util.List;\n"
                    + "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "    int e;\n"
                    + "    int f;\n"
                    + "    int g;\n"
                    + "    int h;\n"
                    + "    void m(List<String> items)\n"
                    + "    {\n"
                    + "        for (String s : items)\n"
                    + "        {\n"
                    + "            System.out.println(s);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void doWhileWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        do",
                    "        {",
                    "            int x = 1;",
                    "        }",
                    "        while (false);",
                    "    }"));
        }

        @Test
        void synchronizedWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        synchronized (this)",
                    "        {",
                    "            int x = 1;",
                    "        }",
                    "    }"));
        }

        @Test
        void tryCatchFinallyWithBracesOnOwnLines() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        try",
                    "        {",
                    "            int x = 1;",
                    "        }",
                    "        catch (Exception e)",
                    "        {",
                    "            e.printStackTrace();",
                    "        }",
                    "        finally",
                    "        {",
                    "            int z = 3;",
                    "        }",
                    "    }"));
        }

        @Test
        void nestedClassWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    static class Inner",
                    "    {",
                    "        int x;",
                    "        int y;",
                    "    }"));
        }

        @Test
        void initializerBlockWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    int x;",
                    "    {",
                    "        x = 42;",
                    "    }"));
        }

        @Test
        void lambdaWithBraceOnOwnLine() {
            String code = "class T {\n"
                    + "    int a;\n"
                    + "    int b;\n"
                    + "    int c;\n"
                    + "    int d;\n"
                    + "    int e;\n"
                    + "    int f;\n"
                    + "    int g;\n"
                    + "    int h;\n"
                    + "    Runnable r = () ->\n"
                    + "    {\n"
                    + "        int x = 1;\n"
                    + "    };\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void switchWithBraceOnOwnLine() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int x)",
                    "    {",
                    "        switch (x)",
                    "        {",
                    "            case 1:",
                    "                break;",
                    "            default:",
                    "                break;",
                    "        }",
                    "    }"));
        }

        @Test
        void multipleMethodsAllmanStyle() {
            assertNoViolations(fourSpaceClass(
                    "    void m1()",
                    "    {",
                    "        int x = 1;",
                    "    }",
                    "    void m2()",
                    "    {",
                    "        int y = 2;",
                    "    }",
                    "    void m3()",
                    "    {",
                    "        int z = 3;",
                    "    }"));
        }

        @Test
        void nestedBlocksAllmanStyle() {
            assertNoViolations(fourSpaceClass(
                    "    void m()",
                    "    {",
                    "        if (true)",
                    "        {",
                    "            while (true)",
                    "            {",
                    "                for (int i = 0; i < 1; i++)",
                    "                {",
                    "                    break;",
                    "                }",
                    "                break;",
                    "            }",
                    "        }",
                    "    }"));
        }

        @Test
        void wrongIndentOnAllmanBraceStillReported() {
            assertHasViolation(fourSpaceClass(
                    "    void m()",
                    "  {",
                    "        int x = 1;",
                    "    }"), "indented incorrectly");
        }

        @Test
        void twoSpaceAllmanStyle() {
            assertNoViolations(twoSpaceClass(
                    "  void m()",
                    "  {",
                    "    int x = 1;",
                    "  }"));
        }

        @Test
        void mixedKRAndAllmanInSameFile() {
            assertNoViolations(fourSpaceClass(
                    "    void m1() {",
                    "        int x = 1;",
                    "    }",
                    "    void m2()",
                    "    {",
                    "        int y = 2;",
                    "    }"));
        }

        @Test
        void allmanWithAnnotatedMethod() {
            assertNoViolations(fourSpaceClass(
                    "    @Override",
                    "    void m()",
                    "    {",
                    "        int x = 1;",
                    "    }"));
        }

        @Test
        void elseIfChainAllmanStyle() {
            assertNoViolations(fourSpaceClass(
                    "    void m(int x)",
                    "    {",
                    "        if (x == 1)",
                    "        {",
                    "            int a = 1;",
                    "        }",
                    "        else if (x == 2)",
                    "        {",
                    "            int b = 2;",
                    "        }",
                    "        else",
                    "        {",
                    "            int c = 3;",
                    "        }",
                    "    }"));
        }
    }
}
