package edu.vt.cs.webcat.rules.indentation;

import java.util.Collections;
import java.util.Map;

public class InferenceResult {

    private final boolean success;
    private final Integer inferredUnit;
    private final int usableSamples;
    private final Map<Integer, Integer> votes;
    private final String failureReason;

    private InferenceResult(boolean success, Integer inferredUnit,
                            int usableSamples, Map<Integer, Integer> votes,
                            String failureReason) {
        this.success = success;
        this.inferredUnit = inferredUnit;
        this.usableSamples = usableSamples;
        this.votes = votes;
        this.failureReason = failureReason;
    }

    public static InferenceResult succeeded(int inferredUnit, int usableSamples,
                                            Map<Integer, Integer> votes) {
        return new InferenceResult(true, inferredUnit, usableSamples,
                Collections.unmodifiableMap(votes), null);
    }

    public static InferenceResult failed(int usableSamples,
                                         Map<Integer, Integer> votes,
                                         String failureReason) {
        return new InferenceResult(false, null, usableSamples,
                Collections.unmodifiableMap(votes), failureReason);
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getInferredUnit() {
        return inferredUnit;
    }

    public int getUsableSamples() {
        return usableSamples;
    }

    public Map<Integer, Integer> getVotes() {
        return votes;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("InferenceResult{unit=%d, samples=%d}", inferredUnit, usableSamples);
        }
        return String.format("InferenceResult{FAILED: %s, samples=%d}", failureReason, usableSamples);
    }
}

