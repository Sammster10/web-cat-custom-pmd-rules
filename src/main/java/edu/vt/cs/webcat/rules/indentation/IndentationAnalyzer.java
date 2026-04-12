package edu.vt.cs.webcat.rules.indentation;

import net.sourceforge.pmd.lang.ast.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndentationAnalyzer {

    private IndentationAnalyzer() {
    }

    public static AnalysisResult analyze(String source, Node rootNode) {
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

        return new AnalysisResult(tabViolations, inference, indentViolations, lines, classifications, depthModel);
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

    public static class AnalysisResult {
        private final List<IndentViolation> tabViolations;
        private final InferenceResult inference;
        private final List<IndentViolation> indentViolations;
        private final List<LineInfo> lines;
        private final Map<Integer, LineKind> classifications;
        private final StructuralDepthModel depthModel;

        public AnalysisResult(List<IndentViolation> tabViolations,
                              InferenceResult inference,
                              List<IndentViolation> indentViolations,
                              List<LineInfo> lines,
                              Map<Integer, LineKind> classifications,
                              StructuralDepthModel depthModel) {
            this.tabViolations = tabViolations;
            this.inference = inference;
            this.indentViolations = indentViolations;
            this.lines = lines;
            this.classifications = classifications;
            this.depthModel = depthModel;
        }

        public List<IndentViolation> getTabViolations() {
            return tabViolations;
        }

        public InferenceResult getInference() {
            return inference;
        }

        public List<IndentViolation> getIndentViolations() {
            return indentViolations;
        }

        public List<LineInfo> getLines() {
            return lines;
        }

        public Map<Integer, LineKind> getClassifications() {
            return classifications;
        }

        public StructuralDepthModel getDepthModel() {
            return depthModel;
        }
    }
}

