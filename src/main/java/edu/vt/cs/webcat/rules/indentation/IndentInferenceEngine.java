package edu.vt.cs.webcat.rules.indentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IndentInferenceEngine {

    private static final double MIN_WINNER_SUPPORT = 0.70;
    private static final int MIN_INDENT = 1;
    private static final int MAX_INDENT = 8;

    private IndentInferenceEngine() {
    }

    public static InferenceResult infer(List<LineInfo> lines,
                                        Map<Integer, LineKind> classifications,
                                        StructuralDepthModel depthModel) {
        Map<Integer, Integer> votes = new HashMap<>();
        int usableSamples = 0;

        for (LineInfo line : lines) {
            int lineNum = line.getLineNumber();
            LineKind kind = classifications.getOrDefault(lineNum, LineKind.IGNORE);

            if (kind != LineKind.BASE) {
                continue;
            }

            if (line.isBlank() || line.hasLeadingTab() || line.isCommentOnly()
                    || line.isInsideBlockComment() || line.isInsideJavadoc()
                    || line.isInsideTextBlock()) {
                continue;
            }

            int depth = depthModel.getDepth(lineNum);
            int spaces = line.getLeadingSpaces();

            if (depth <= 0 || spaces <= 0) {
                continue;
            }

            if (spaces % depth != 0) {
                continue;
            }

            int candidateN = spaces / depth;

            if (candidateN < MIN_INDENT || candidateN > MAX_INDENT) {
                continue;
            }

            votes.merge(candidateN, 1, Integer::sum);
            usableSamples++;
        }

        if (usableSamples == 0) {
            return InferenceResult.failed(usableSamples, votes,
                    "No usable indentation samples found.");
        }

        int winnerN = 0;
        int winnerCount = 0;

        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > winnerCount) {
                winnerCount = entry.getValue();
                winnerN = entry.getKey();
            }
        }

        double support = (double) winnerCount / usableSamples;
        if (support < MIN_WINNER_SUPPORT) {
            return InferenceResult.failed(usableSamples, votes,
                    String.format("Indentation ambiguous: winner N=%d has %.0f%% support, need at least %.0f%%.",
                            winnerN, support * 100, MIN_WINNER_SUPPORT * 100));
        }


        return InferenceResult.succeeded(winnerN, usableSamples, votes);
    }
}

