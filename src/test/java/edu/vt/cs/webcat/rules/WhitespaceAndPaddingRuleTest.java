package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhitespaceAndPaddingRuleTest {

    private Rule rule;

    @BeforeEach
    void setUp() {
        rule = new WhitespaceAndPaddingRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("WhitespaceAndPadding violation");
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
    class PrefixUnarySpacing {
        @Test
        void validPrefixBang() {
            assertNoViolations("class T { void m() { boolean b = !true; } }");
        }

        @Test
        void validPrefixIncrement() {
            assertNoViolations("class T { void m() { int i = 0; ++i; } }");
        }

        @Test
        void validPrefixDecrement() {
            assertNoViolations("class T { void m() { int i = 1; --i; } }");
        }

        @Test
        void validPrefixPlus() {
            assertNoViolations("class T { void m() { int v = +1; } }");
        }

        @Test
        void validPrefixMinus() {
            assertNoViolations("class T { void m() { int v = -1; } }");
        }

        @Test
        void invalidSpaceAfterBang() {
            assertHasViolation("class T { void m() { boolean b = ! true; } }",
                    "prefix unary operator '!'");
        }

        @Test
        void invalidSpaceAfterPrefixIncrement() {
            assertHasViolation("class T { void m() { int i = 0; ++ i; } }",
                    "prefix unary operator '++'");
        }

        @Test
        void invalidSpaceAfterPrefixDecrement() {
            assertHasViolation("class T { void m() { int i = 1; -- i; } }",
                    "prefix unary operator '--'");
        }

        @Test
        void invalidLineBreakAfterBang() {
            assertHasViolation("class T {\n  void m() {\n    boolean b = !\ntrue;\n  }\n}",
                    "prefix unary operator '!'");
        }

        @Test
        void doesNotFlagPostfixAsPrefixIncrement() {
            assertNoViolations("class T { void m() { int i = 0; i++; } }");
        }

        @Test
        void doesNotFlagBinaryPlusAsPrefix() {
            assertNoViolations("class T { void m() { int v = 1 + 2; } }");
        }
    }

    @Nested
    class PostfixIncDecSpacing {
        @Test
        void validPostfixIncrement() {
            assertNoViolations("class T { void m() { int i = 0; i++; } }");
        }

        @Test
        void validPostfixDecrement() {
            assertNoViolations("class T { void m() { int i = 1; i--; } }");
        }

        @Test
        void invalidSpaceBeforePostfixIncrement() {
            assertHasViolation("class T { void m() { int i = 0; i ++; } }",
                    "postfix operator '++'");
        }

        @Test
        void invalidSpaceBeforePostfixDecrement() {
            assertHasViolation("class T { void m() { int i = 1; i --; } }",
                    "postfix operator '--'");
        }

        @Test
        void doesNotFlagPrefixAsPostfix() {
            assertNoViolations("class T { void m() { int i = 0; ++i; } }");
        }
    }

    @Nested
    class DotSpacing {
        @Test
        void validDotAccess() {
            assertNoViolations("class T { void m() { String s = \"hello\".length() + \"\"; } }");
        }

        @Test
        void validLineBreakBeforeDot() {
            assertNoViolations("class T {\n  void m() {\n    String s = \"hello\"\n        .toString();\n  }\n}");
        }

        @Test
        void invalidSpaceBeforeDot() {
            assertHasViolation("class T { void m() { String s = \"hello\" .toString(); } }",
                    "No horizontal whitespace allowed before '.'");
        }
    }

    @Nested
    class DelimiterSpacing {
        @Test
        void validCommaSpace() {
            assertNoViolations("class T { void m(int a, int b) { } }");
        }

        @Test
        void validCommaNewline() {
            assertNoViolations("class T {\n  void m(int a,\n         int b) {\n  }\n}");
        }

        @Test
        void validSemicolonNewline() {
            assertNoViolations("class T {\n  void m() {\n    int x = 1;\n    return;\n  }\n}");
        }

        @Test
        void invalidCommaNoSpace() {
            assertHasViolation("class T { void m(int a,int b) { } }", "','");
        }

        @Test
        void invalidSemicolonNoSpace() {
            assertHasViolation("class T { void m() { int x = 1;int y = 2; } }", "';'");
        }

        @Test
        void validSemicolonEndOfFile() {
            assertNoViolations("class T { void m() { return; } }");
        }
    }

    @Nested
    class OperatorAndKeywordSpacing {
        @Test
        void validBinaryPlus() {
            assertNoViolations("class T { void m() { int v = 1 + 2; } }");
        }

        @Test
        void invalidBinaryPlusNoSpace() {
            assertHasViolation("class T { void m() { int v = 1+2; } }", "Whitespace required");
        }

        @Test
        void validAssignment() {
            assertNoViolations("class T { void m() { int x = 1; } }");
        }

        @Test
        void invalidAssignmentNoSpace() {
            assertHasViolation("class T { void m() { int x=1; } }", "Whitespace required");
        }

        @Test
        void validIfKeyword() {
            assertNoViolations("class T { void m() { if (true) { } } }");
        }

        @Test
        void invalidIfNoSpaceAfter() {
            assertHasViolation("class T { void m() { if(true) { } } }",
                    "Whitespace required after keyword 'if'");
        }

        @Test
        void validReturnKeyword() {
            assertNoViolations("class T { int m() { return 1; } }");
        }

        @Test
        void invalidReturnNoSpace() {
            assertHasViolation("class T { String m() { return\"hello\"; } }",
                    "Whitespace required after keyword 'return'");
        }

        @Test
        void validTernary() {
            assertNoViolations("class T { void m() { int x = true ? 1 : 2; } }");
        }

        @Test
        void invalidTernaryNoSpaceAroundQuestion() {
            assertHasViolation("class T { void m() { int x = true? 1 : 2; } }",
                    "Whitespace required before '?'");
        }

        @Test
        void validLeftShift() {
            assertNoViolations("class T { void m() { int v = 1 << 2; } }");
        }

        @Test
        void invalidLeftShiftNoSpace() {
            assertHasViolation("class T { void m() { int v = 1<<2; } }", "Whitespace required");
        }

        @Test
        void validBrace() {
            assertNoViolations("class T { void m() { } }");
        }

        @Test
        void invalidBraceNoSpaceBefore() {
            assertHasViolation("class T { void m(){ } }", "Whitespace required before '{'");
        }

        @Test
        void validEqualsEquals() {
            assertNoViolations("class T { void m() { boolean b = 1 == 1; } }");
        }

        @Test
        void invalidEqualsEqualsNoSpace() {
            assertHasViolation("class T { void m() { boolean b = 1==1; } }", "Whitespace required");
        }

        @Test
        void validNotEquals() {
            assertNoViolations("class T { void m() { boolean b = 1 != 2; } }");
        }

        @Test
        void validLineBreakBeforeOperator() {
            assertNoViolations("class T {\n  void m() {\n    int v = 1\n        + 2;\n  }\n}");
        }

        @Test
        void validForKeyword() {
            assertNoViolations("class T { void m() { for (int i = 0; i < 10; i++) { } } }");
        }

        @Test
        void invalidForNoSpace() {
            assertHasViolation("class T { void m() { for(int i = 0; i < 10; i++) { } } }",
                    "Whitespace required after keyword 'for'");
        }

        @Test
        void validElseKeyword() {
            assertNoViolations("class T { void m() { if (true) { } else { } } }");
        }

        @Test
        void validWhileKeyword() {
            assertNoViolations("class T { void m() { while (true) { break; } } }");
        }

        @Test
        void validDoKeyword() {
            assertNoViolations("class T { void m() { do { break; } while (true); } }");
        }

        @Test
        void validTryKeyword() {
            assertNoViolations("class T { void m() { try { } catch (Exception e) { } finally { } } }");
        }

        @Test
        void validSynchronizedKeyword() {
            assertNoViolations("class T { void m() { synchronized (this) { } } }");
        }
    }

    @Nested
    class MethodConstructorParenPadding {
        @Test
        void validMethodDeclaration() {
            assertNoViolations("class T { void run() { } }");
        }

        @Test
        void validMethodCall() {
            assertNoViolations("class T { void m() { run(); } void run() { } }");
        }

        @Test
        void invalidMethodDeclarationSpaceBefore() {
            assertHasViolation("class T { void run () { } }", "No space allowed before '('");
        }

        @Test
        void invalidMethodCallSpaceBefore() {
            assertHasViolation("class T { void m() { run (); } void run() { } }",
                    "No space allowed before '('");
        }

        @Test
        void invalidMethodDeclarationLineBreak() {
            assertHasViolation("class T {\n  void run\n  () {\n  }\n}",
                    "'(' must be on the same line");
        }

        @Test
        void doesNotFlagIfParen() {
            List<RuleViolation> violations = runRule("class T { void m() { if (true) { } } }");
            boolean hasFalsePositive = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("No space allowed before '('"));
            assertFalse(hasFalsePositive);
        }

        @Test
        void doesNotFlagForParen() {
            List<RuleViolation> violations = runRule(
                    "class T { void m() { for (int i = 0; i < 10; i++) { } } }");
            boolean hasFalsePositive = violations.stream()
                    .anyMatch(v -> v.getDescription().contains("No space allowed before '('"));
            assertFalse(hasFalsePositive);
        }

        @Test
        void validConstructorDeclaration() {
            assertNoViolations("class T { T() { } }");
        }

        @Test
        void invalidConstructorDeclarationSpace() {
            assertHasViolation("class T { T () { } }", "No space allowed before '('");
        }

        @Test
        void validConstructorCall() {
            assertNoViolations("class T { void m() { Object o = new Object(); } }");
        }

        @Test
        void validExplicitThisCall() {
            assertNoViolations("class T { T() { this(1); } T(int x) { } }");
        }

        @Test
        void validExplicitSuperCall() {
            assertNoViolations("class T { T() { super(); } }");
        }
    }

    @Nested
    class GenericTypeSpacing {
        @Test
        void validSimpleGeneric() {
            assertNoViolations("import java.util.List;\nclass T { List<String> names; }");
        }

        @Test
        void validNestedGeneric() {
            assertNoViolations("import java.util.*;\nclass T { Map<String, List<Integer>> m; }");
        }

        @Test
        void validTypeParameter() {
            assertNoViolations("class Box<T> { T value; }");
        }

        @Test
        void validMethodTypeParameter() {
            assertNoViolations("class T { public <E> E id(E x) { return x; } }");
        }

        @Test
        void invalidSpaceBeforeOpenAngle() {
            assertHasViolation("import java.util.List;\nclass T { List <String> names; }",
                    "No space allowed before '<' in generic type");
        }

        @Test
        void invalidSpaceAfterOpenAngle() {
            assertHasViolation("import java.util.List;\nclass T { List< String> names; }",
                    "No space allowed after '<' in generic type");
        }

        @Test
        void invalidSpaceBeforeCloseAngle() {
            assertHasViolation("import java.util.List;\nclass T { List<String > names; }",
                    "No space allowed before '>' in generic type");
        }

        @Test
        void invalidNoSpaceAfterCloseAngle() {
            assertHasViolation("import java.util.List;\nclass T { List<String>names; }",
                    "Space required after '>' in generic type");
        }

        @Test
        void validGenericDoesNotAffectRelationalOp() {
            assertNoViolations("class T { void m() { boolean b = 1 < 2; } }");
        }

        @Test
        void validDiamond() {
            assertNoViolations("import java.util.*;\nclass T { void m() { List<String> l = new ArrayList<>(); } }");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void forHeaderSemicolons() {
            assertNoViolations("class T { void m() { for (int i = 0; i < 10; i++) { } } }");
        }

        @Test
        void stringLiteralNotChecked() {
            assertNoViolations("class T { String s = \"a+b if(x){y}\"; }");
        }

        @Test
        void emptyClass() {
            assertNoViolations("class T { }");
        }

        @Test
        void multipleViolationsOnSameLine() {
            List<RuleViolation> violations = runRule("class T { void m() { int v=1+2; } }");
            assertTrue(violations.size() >= 2,
                    String.format("Expected at least 2 violations but found %d: %s",
                            violations.size(), violations));
        }

        @Test
        void validEnhancedForColon() {
            assertNoViolations("class T { void m() { int[] a = {1}; for (int x : a) { } } }");
        }

        @Test
        void invalidEnhancedForColonNoSpace() {
            assertHasViolation("class T { void m() { int[] a = {1}; for (int x :a) { } } }",
                    "Whitespace required after ':'");
        }

        @Test
        void validEmptyBlock() {
            assertNoViolations("class T { void m() {} }");
        }

        @Test
        void annotationNotFlagged() {
            assertNoViolations("class T {\n  @Override\n  public String toString() {\n    return \"\";\n  }\n}");
        }

        @Test
        void lambdaArrow() {
            assertNoViolations("class T { void m() { Runnable r = () -> { }; } }");
        }

        @Test
        void methodReferenceNotFlagged() {
            assertNoViolations("import java.util.function.*;\nclass T { void m() { Function<String, Integer> f = Integer::parseInt; } }");
        }
    }
}

