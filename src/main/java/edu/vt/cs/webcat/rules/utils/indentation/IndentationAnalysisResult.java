package edu.vt.cs.webcat.rules.utils.indentation;

import java.util.List;
import java.util.Map;

/** Immutable output of a dynamic indentation analysis. */
public final class IndentationAnalysisResult {

    private final List<IndentViolation> tabViolations;
    private final InferenceResult inference;
    private final List<IndentViolation> indentViolations;
    private final List<LineInfo> lines;
    private final Map<Integer, LineKind> classifications;
    private final StructuralDepthModel depthModel;

    public IndentationAnalysisResult(
            List<IndentViolation> tabViolations,
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
