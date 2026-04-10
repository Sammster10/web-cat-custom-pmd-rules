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

class LineLayoutRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new LineLayoutRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("Line layout violation");
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
    //  Behavior 1: Right curly brace must be alone on its line           //
    // ================================================================== //

    @Nested
    class RCurlyAloneValid {

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
        void classClosingBraceAlone() {
            String code = "class T {\n"
                    + "    int x;\n"
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
    }

    @Nested
    class RCurlyAloneInvalid {

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
        void braceFollowedBySemicolon() {
            String code = "class T {\n"
                    + "    Object o = new Object() {\n"
                    + "        public String toString() {\n"
                    + "            return \"\";\n"
                    + "        }\n"
                    + "    };\n"
                    + "}";
            assertHasViolation(code, "Right curly brace must be alone on its line.");
        }

        @Test
        void braceFollowedByCloseParen() {
            String code = "class T {\n"
                    + "    void run(Runnable r) { }\n"
                    + "    void m() {\n"
                    + "        run(new Runnable() {\n"
                    + "            public void run() {\n"
                    + "            }\n"
                    + "        });\n"
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
    }

    // ================================================================== //
    //  Behavior 2: One statement per line                                //
    // ================================================================== //

    @Nested
    class OneStatementValid {

        @Test
        void singleStatementPerLine() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1;\n"
                    + "        int y = 2;\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void forHeaderSemicolons() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        for (int i = 0; i < 10; i++) {\n"
                    + "            System.out.println(i);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void emptyForHeader() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        for (;;) {\n"
                    + "            break;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void forEachLoop() {
            String code = "class T {\n"
                    + "    void m(int[] arr) {\n"
                    + "        for (int x : arr) {\n"
                    + "            System.out.println(x);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void ifOnOneLine() {
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
        void separateLinesForCaseStatements() {
            String code = "class T {\n"
                    + "    void m(int x) {\n"
                    + "        switch (x) {\n"
                    + "            case 1:\n"
                    + "                System.out.println(1);\n"
                    + "                break;\n"
                    + "            default:\n"
                    + "                break;\n"
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
        void singleReturnStatement() {
            String code = "class T {\n"
                    + "    int m() {\n"
                    + "        return 1;\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void chainedMethodCalls() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        new StringBuilder().append(\"a\").append(\"b\").toString();\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void annotationsOnSeparateLines() {
            String code = "class T {\n"
                    + "    @Override\n"
                    + "    public String toString() {\n"
                    + "        return \"\";\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void ifWithBlockBody() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void whileWithBlockBody() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        while (false) {\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    @Nested
    class OneStatementInvalid {

        @Test
        void twoMethodCallsOnOneLine() {
            setRuleProperty(rule, "checkRCurly", false);
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        a(); b();\n"
                    + "    }\n"
                    + "    void a() { }\n"
                    + "    void b() { }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void twoAssignmentsOnOneLine() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1; int y = 2;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void ifWithSameLineBody() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) System.out.println(1);\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void forWithSameLineBody() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        for (int i = 0; i < 10; i++) System.out.println(i);\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void whileWithSameLineBody() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        while (true) break;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void doSomethingThenReturn() {
            setRuleProperty(rule, "checkRCurly", false);
            String code = "class T {\n"
                    + "    int m() {\n"
                    + "        int x = 0; return x;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
        }

        @Test
        void caseWithTwoStatements() {
            String code = "class T {\n"
                    + "    void m(int x) {\n"
                    + "        switch (x) {\n"
                    + "            case 1: System.out.println(1); break;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Only one statement is allowed per line.");
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
        void singleLineEmptyClass() {
            setRuleProperty(rule, "checkRCurly", false);
            String code = "class T { }";
            assertNoViolations(code);
        }

        @Test
        void stringContainingSemicolons() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        String s = \"a; b; c;\";\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void stringContainingBraces() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        String s = \"} else {\";\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void multipleForHeadersOnSeparateLines() {
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        for (int i = 0; i < 5; i++) {\n"
                    + "            for (int j = 0; j < 5; j++) {\n"
                    + "                System.out.println(i + j);\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }

        @Test
        void lambdaExpression() {
            String code = "class T {\n"
                    + "    Runnable r = () -> {\n"
                    + "        System.out.println(1);\n"
                    + "    };\n"
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

        @Test
        void singleStatementAfterCaseLabel() {
            String code = "class T {\n"
                    + "    void m(int x) {\n"
                    + "        switch (x) {\n"
                    + "            case 1: System.out.println(1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            assertNoViolations(code);
        }
    }

    // ================================================================== //
    //  Custom messages                                                   //
    // ================================================================== //

    @Nested
    class CustomMessages {

        @Test
        void customRCurlyMessage() {
            setRuleProperty(rule, "rcurlyMessage", "Brace not alone!");
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

        @Test
        void customOneStatementMessage() {
            setRuleProperty(rule, "oneStatementMessage", "Too many statements!");
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1; int y = 2;\n"
                    + "    }\n"
                    + "}";
            assertHasViolation(code, "Too many statements!");
        }
    }

    // ================================================================== //
    //  Disabling individual checks                                       //
    // ================================================================== //

    @Nested
    class DisabledChecks {

        @Test
        void disableRCurlyCheck() {
            setRuleProperty(rule, "checkRCurly", false);
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        if (true) {\n"
                    + "            int x = 1;\n"
                    + "        } else {\n"
                    + "            int y = 2;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            boolean hasRCurlyViolation = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("Right curly brace"));
            assertFalse(hasRCurlyViolation);
        }

        @Test
        void disableOneStatementCheck() {
            setRuleProperty(rule, "checkOneStatement", false);
            String code = "class T {\n"
                    + "    void m() {\n"
                    + "        int x = 1; int y = 2;\n"
                    + "    }\n"
                    + "}";
            List<RuleViolation> violations = runRule(code);
            boolean hasStmtViolation = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("Only one statement"));
            assertFalse(hasStmtViolation);
        }
    }
}

