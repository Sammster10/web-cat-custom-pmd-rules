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

class HiddenFieldRuleTest {

    private Rule rule;

    @SuppressWarnings("unchecked")
    private static <T> void setRuleProperty(Rule rule, String name, T value) {
        PropertyDescriptor<T> descriptor = (PropertyDescriptor<T>) rule.getPropertyDescriptor(name);
        rule.setProperty(descriptor, value);
    }

    @BeforeEach
    void setUp() {
        rule = new HiddenFieldRule();
        rule.setLanguage(JavaLanguageModule.getInstance());
        rule.setMessage("HiddenField violation");
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
    class NoViolations {
        @Test
        void parameterDoesNotMatchField() {
            assertNoViolations("""
                    class T {
                        private int count;
                        void m(int other) { }
                    }""");
        }

        @Test
        void localVariableDoesNotMatchField() {
            assertNoViolations("""
                    class T {
                        private String name;
                        void m() { int x = 0; }
                    }""");
        }

        @Test
        void noFieldsInClass() {
            assertNoViolations("""
                    class T {
                        void m(int x) { int y = 0; }
                    }""");
        }

        @Test
        void emptyMethodBody() {
            assertNoViolations("""
                    class T {
                        private int x;
                        void m() { }
                    }""");
        }
    }

    @Nested
    class ConstructorExclusion {
        @Test
        void constructorExcludedByDefault() {
            assertNoViolations("""
                    class T {
                        private int x;
                        T(int x) { this.x = x; }
                    }""");
        }

        @Test
        void constructorCheckedWhenEnabled() {
            setRuleProperty(rule, "checkConstructors", true);
            assertHasViolation("""
                            class T {
                                private int x;
                                T(int x) { this.x = x; }
                            }""",
                    "Parameter");
        }

        @Test
        void constructorLocalVariableCheckedWhenEnabled() {
            setRuleProperty(rule, "checkConstructors", true);
            assertHasViolation("""
                            class T {
                                private int x;
                                T() { int x = 5; this.x = x; }
                            }""",
                    "Local variable");
        }
    }

    @Nested
    class SetterExclusion {
        @Test
        void setterExcludedByDefault() {
            assertNoViolations("""
                    class T {
                        private int count;
                        void setCount(int count) { this.count = count; }
                    }""");
        }

        @Test
        void setterCheckedWhenEnabled() {
            setRuleProperty(rule, "checkSetters", true);
            assertHasViolation("""
                            class T {
                                private int count;
                                void setCount(int count) { this.count = count; }
                            }""",
                    "Parameter");
        }

        @Test
        void methodNamedSetButNotASetter() {
            assertHasViolation("""
                            class T {
                                private int x;
                                int setup(int x) { return x; }
                            }""",
                    "Parameter");
        }

        @Test
        void setterWithTwoParamsNotExcluded() {
            assertHasViolation("""
                            class T {
                                private int x;
                                void setX(int x, int y) { this.x = x; }
                            }""",
                    "Parameter");
        }

        @Test
        void setterWithReturnTypeNotExcluded() {
            assertHasViolation("""
                            class T {
                                private int x;
                                int setX(int x) { this.x = x; return x; }
                            }""",
                    "Parameter");
        }
    }

    @Nested
    class AbstractMethodExclusion {
        @Test
        void abstractMethodExcludedByDefault() {
            assertNoViolations("""
                    abstract class T {
                        private int x;
                        abstract void m(int x);
                    }""");
        }

        @Test
        void abstractMethodCheckedWhenEnabled() {
            setRuleProperty(rule, "checkAbstractMethods", true);
            assertHasViolation("""
                            abstract class T {
                                private int x;
                                abstract void m(int x);
                            }""",
                    "Parameter");
        }
    }

    @Nested
    class ParameterShadowing {
        @Test
        void singleParameterMatchesField() {
            assertHasViolation("""
                            class T {
                                private int x;
                                void m(int x) { }
                            }""",
                    "Parameter");
        }

        @Test
        void multipleParametersOneMatches() {
            assertViolationCount("""
                    class T {
                        private int x;
                        private int y;
                        void m(int x, int z) { }
                    }""", 1);
        }

        @Test
        void multipleParametersAllMatch() {
            assertViolationCount("""
                    class T {
                        private int x;
                        private int y;
                        void m(int x, int y) { }
                    }""", 2);
        }
    }

    @Nested
    class LocalVariableShadowing {
        @Test
        void localVariableMatchesField() {
            assertHasViolation("""
                            class T {
                                private int x;
                                void m() { int x = 0; }
                            }""",
                    "Local variable");
        }

        @Test
        void nestedBlockLocalVariable() {
            assertHasViolation("""
                            class T {
                                private int x;
                                void m() {
                                    if (true) { int x = 1; }
                                }
                            }""",
                    "Local variable");
        }

        @Test
        void forLoopVariable() {
            assertHasViolation("""
                            class T {
                                private int i;
                                void m() {
                                    for (int i = 0; i < 10; i++) { }
                                }
                            }""",
                    "Local variable");
        }
    }

    @Nested
    class MixedViolations {
        @Test
        void parameterAndLocalVariableBothMatch() {
            assertViolationCount("""
                    class T {
                        private int x;
                        private int y;
                        void m(int x) { int y = 0; }
                    }""", 2);
        }

        @Test
        void multipleMethods() {
            assertViolationCount("""
                    class T {
                        private int x;
                        void a(int x) { }
                        void b(int x) { }
                    }""", 2);
        }

        @Test
        void publicFieldShadowed() {
            assertHasViolation("""
                            class T {
                                public int x;
                                void m(int x) { }
                            }""",
                    "Parameter");
        }

        @Test
        void staticFieldShadowed() {
            assertHasViolation("""
                            class T {
                                static int x;
                                void m(int x) { }
                            }""",
                    "Parameter");
        }
    }

    @Nested
    class CustomMessages {
        @Test
        void customParameterMessage() {
            setRuleProperty(rule, "parameterMessage", "Param {0} shadows field");
            assertHasViolation("""
                            class T {
                                private int x;
                                void m(int x) { }
                            }""",
                    "Param x shadows field");
        }

        @Test
        void customLocalVariableMessage() {
            setRuleProperty(rule, "localVariableMessage", "Local {0} shadows field");
            assertHasViolation("""
                            class T {
                                private int x;
                                void m() { int x = 0; }
                            }""",
                    "Local x shadows field");
        }
    }
}

