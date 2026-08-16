package edu.vt.cs.webcat.rules.utils.indentation;

import net.sourceforge.pmd.lang.ast.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndentationAnalyzer {

    private IndentationAnalyzer() {
    }

    public static IndentationAnalysisResult analyze(String source, Node rootNode) {
        List<LineInfo> lines = LineScanner.scan(source);

        List<IndentViolation> tabViolations = detectTabViolations(lines);

        StructuralDepthModel depthModel = StructuralDepthModel.build(rootNode, lines.size());

        Map<Integer, LineKind> classifications = LineClassifier.classify(lines, depthModel, rootNode);

        InferenceResult inference = IndentInferenceEngine.infer(lines, classifications, depthModel);

        List<IndentViolation> indentViolations;
        if (inference.isSuccess()) {
            indentViolations = IndentEnforcer.enforce(
                    lines, classifications, depthModel, inference.getInferredUnit());
        } else {
            indentViolations = new ArrayList<>();
        }

        return new IndentationAnalysisResult(
                tabViolations, inference, indentViolations,
                lines, classifications, depthModel);
    }

    private static List<IndentViolation> detectTabViolations(List<LineInfo> lines) {
        List<IndentViolation> violations = new ArrayList<>();
        for (LineInfo line : lines) {
            if (line.hasLeadingTab()) {
                violations.add(new IndentViolation(line.getLineNumber(),
                        String.format("Line %d contains a tab character in its indentation. Use spaces instead.",
                                line.getLineNumber())));
            }
        }
        return violations;
    }

}

