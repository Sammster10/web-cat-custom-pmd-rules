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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllRulesIntegrationTest {

    private static final String RULE_PACKAGE = "edu.vt.cs.webcat.rules.";

    private static final Set<String> EXPECTED_RULES = Set.of(
            RULE_PACKAGE + "BraceSpacingRule",
            RULE_PACKAGE + "CovariantEqualsRule",
            RULE_PACKAGE + "DelimiterSpacingRule",
            RULE_PACKAGE + "DynamicIndentationRule",
            RULE_PACKAGE + "FieldVisibilityRule",
            RULE_PACKAGE + "HiddenFieldRule",
            RULE_PACKAGE + "JavaStyleArrayDeclarationRule",
            RULE_PACKAGE + "JUnitSpellingRule",
            RULE_PACKAGE + "JUnitStaticSuiteRule",
            RULE_PACKAGE + "LineLayoutRule",
            RULE_PACKAGE + "LineLengthRule",
            RULE_PACKAGE + "OperatorSpacingRule",
            RULE_PACKAGE + "RegexRule",
            RULE_PACKAGE + "RightCurlyBlockRule",
            RULE_PACKAGE + "SimpleIndentationRule",
            RULE_PACKAGE + "SimplifiableTestAssertionRule",
            RULE_PACKAGE + "StrictJavadocRule",
            RULE_PACKAGE + "TestClassWithoutTestCasesRule",
            RULE_PACKAGE + "UnitTestShouldIncludeAssertRule",
            RULE_PACKAGE + "UppercaseLongLiteralRule"
    );

    @Test
    void everyCustomRuleIsDiscoverableThroughTheServiceProvider() {
        Set<String> discovered = loadCustomRules().stream()
                .map(rule -> rule.getClass().getName())
                .collect(Collectors.toSet());

        assertEquals(EXPECTED_RULES, discovered);
    }

    @Test
    void completeRuleSuiteAcceptsAConsistentSourceFile() {
        String source = "/**\n"
                + " * A production sample.\n"
                + " * @author Someone\n"
                + " * @version 1.0\n"
                + " */\n"
                + "public class ProductionSample {\n"
                + "    private long value = 1L;\n"
                + "\n"
                + "    /**\n"
                + "     * Gets the value.\n"
                + "     * @return the value\n"
                + "     */\n"
                + "    public long value() {\n"
                + "        return value;\n"
                + "    }\n"
                + "}\n";

        Report report = runCompleteSuite(source, "ProductionSample.java");

        assertTrue(report.getProcessingErrors().isEmpty(),
                () -> "Unexpected processing errors: " + report.getProcessingErrors());
        assertTrue(report.getViolations().isEmpty(),
                () -> "Expected a clean full-suite run but found: " + report.getViolations());
    }

    @Test
    void completeRuleSuiteReportsIndependentProblemsTogether() {
        String source = "class Bad {\n"
                + "    public long value = 1l;\n"
                + "    void run() { int first=1+2; int second = 3; }\n"
                + "}\n";

        Report report = runCompleteSuite(source, "Bad.java");
        Set<Class<?>> reportingRules = report.getViolations().stream()
                .map(RuleViolation::getRule)
                .map(Object::getClass)
                .collect(Collectors.toSet());

        assertTrue(report.getProcessingErrors().isEmpty(),
                () -> "Unexpected processing errors: " + report.getProcessingErrors());
        assertTrue(reportingRules.contains(FieldVisibilityRule.class));
        assertTrue(reportingRules.contains(LineLayoutRule.class));
        assertTrue(reportingRules.contains(RightCurlyBlockRule.class));
        assertTrue(reportingRules.contains(StrictJavadocRule.class));
        assertTrue(reportingRules.contains(UppercaseLongLiteralRule.class));
        assertTrue(reportingRules.contains(OperatorSpacingRule.class));
    }

    private static Report runCompleteSuite(String source, String fileName) {
        PMDConfiguration config = new PMDConfiguration();
        config.setThreads(1);
        config.setDefaultLanguageVersion(
                JavaLanguageModule.getInstance().getVersion("17"));

        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
            for (Rule rule : loadCustomRules()) {
                rule.setLanguage(JavaLanguageModule.getInstance());
                rule.setName(rule.getClass().getSimpleName());
                rule.setMessage(rule.getClass().getSimpleName() + " violation");
                analysis.addRuleSet(RuleSet.forSingleRule(rule));
            }
            analysis.files().addFile(TextFile.forCharSeq(
                    source,
                    FileId.fromPathLikeString(fileName),
                    JavaLanguageModule.getInstance().getVersion("17")));
            return analysis.performAnalysisAndCollectReport();
        }
    }

    private static List<Rule> loadCustomRules() {
        List<Rule> rules = new ArrayList<>();
        for (Rule rule : ServiceLoader.load(Rule.class)) {
            if (rule.getClass().getName().startsWith(RULE_PACKAGE)) {
                rules.add(rule);
            }
        }
        return rules;
    }
}
