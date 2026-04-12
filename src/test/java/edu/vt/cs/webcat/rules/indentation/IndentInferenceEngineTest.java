package edu.vt.cs.webcat.rules.indentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndentInferenceEngineTest {

    private static LineInfo baseLine(int lineNum, int leadingSpaces) {
        String text = spaces(leadingSpaces) + "int x;";
        return new LineInfo(lineNum, text, leadingSpaces,
                false, false, false, false, false, false);
    }

    private static String spaces(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(' ');
        return sb.toString();
    }

    private static Map<Integer, LineKind> allBase(int count) {
        Map<Integer, LineKind> map = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            map.put(i, LineKind.BASE);
        }
        return map;
    }

    @Nested
    class SuccessfulInference {
        @Test
        void infersUnitFour() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                lines.add(baseLine(i, 4));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getInferredUnit());
        }

        @Test
        void infersUnitTwo() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                lines.add(baseLine(i, 2));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(2, result.getInferredUnit());
        }

        @Test
        void infersUnitThree() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                lines.add(baseLine(i, 6));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 2);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(3, result.getInferredUnit());
        }

        @Test
        void infersFromSmallFile() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                lines.add(baseLine(i, 4));
            }
            Map<Integer, LineKind> kinds = allBase(3);
            StructuralDepthModel model = uniformDepthModel(3, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getInferredUnit());
        }

        @Test
        void infersFromSingleSample() {
            List<LineInfo> lines = List.of(baseLine(1, 2));
            Map<Integer, LineKind> kinds = Map.of(1, LineKind.BASE);
            StructuralDepthModel model = uniformDepthModel(1, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(2, result.getInferredUnit());
        }
    }

    @Nested
    class FailedInference {
        @Test
        void ambiguousVotes() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 4));
            }
            for (int i = 6; i <= 10; i++) {
                lines.add(baseLine(i, 2));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void noUsableSamples() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                lines.add(baseLine(i, 0));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 0);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
            assertTrue(result.getFailureReason().contains("No usable"));
        }

        @Test
        void ambiguousSmallFile() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            lines.add(baseLine(1, 4));
            lines.add(baseLine(2, 2));
            lines.add(baseLine(3, 4));
            Map<Integer, LineKind> kinds = allBase(3);
            StructuralDepthModel model = uniformDepthModel(3, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void allDepthZeroNoUsable() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 4));
            }
            Map<Integer, LineKind> kinds = allBase(5);
            StructuralDepthModel model = uniformDepthModel(5, 0);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void allContinuationLinesNoUsable() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 8));
            }
            Map<Integer, LineKind> kinds = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                kinds.put(i, LineKind.CONTINUATION);
            }
            StructuralDepthModel model = uniformDepthModel(5, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void spacesNotDivisibleByDepth() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 5));
            }
            Map<Integer, LineKind> kinds = allBase(5);
            StructuralDepthModel model = uniformDepthModel(5, 2);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void candidateOutOfRange() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 10));
            }
            Map<Integer, LineKind> kinds = allBase(5);
            StructuralDepthModel model = uniformDepthModel(5, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void allTabLinesNoUsable() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(new LineInfo(i, "\tint x;", 0,
                        true, false, false, false, false, false));
            }
            Map<Integer, LineKind> kinds = allBase(5);
            StructuralDepthModel model = uniformDepthModel(5, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
        }

        @Test
        void exactlyAt70PercentBoundaryFails() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 7; i++) {
                lines.add(baseLine(i, 4));
            }
            for (int i = 8; i <= 10; i++) {
                lines.add(baseLine(i, 2));
            }
            Map<Integer, LineKind> kinds = allBase(10);
            StructuralDepthModel model = uniformDepthModel(10, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getInferredUnit());
        }

        @Test
        void justBelow70PercentFails() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                lines.add(baseLine(i, 4));
            }
            for (int i = 7; i <= 10; i++) {
                lines.add(baseLine(i, 2));
            }
            for (int i = 11; i <= 13; i++) {
                lines.add(baseLine(i, 6));
            }
            Map<Integer, LineKind> kinds = allBase(13);
            Map<Integer, Integer> depthMap = new HashMap<>();
            for (int i = 1; i <= 6; i++) depthMap.put(i, 1);
            for (int i = 7; i <= 10; i++) depthMap.put(i, 1);
            for (int i = 11; i <= 13; i++) depthMap.put(i, 1);
            StructuralDepthModel model = new StructuralDepthModel(depthMap);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertFalse(result.isSuccess());
            assertTrue(result.getFailureReason().contains("ambiguous"));
        }
    }

    @Nested
    class MixedDepths {
        @Test
        void inferFromMixedDepthsConsistent() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            lines.add(baseLine(1, 4));
            lines.add(baseLine(2, 4));
            lines.add(baseLine(3, 8));
            lines.add(baseLine(4, 8));
            lines.add(baseLine(5, 12));
            Map<Integer, LineKind> kinds = allBase(5);
            Map<Integer, Integer> depthMap = new HashMap<>();
            depthMap.put(1, 1);
            depthMap.put(2, 1);
            depthMap.put(3, 2);
            depthMap.put(4, 2);
            depthMap.put(5, 3);
            StructuralDepthModel model = new StructuralDepthModel(depthMap);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getInferredUnit());
        }

        @Test
        void reportsUsableSamples() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                lines.add(baseLine(i, 4));
            }
            Map<Integer, LineKind> kinds = allBase(5);
            StructuralDepthModel model = uniformDepthModel(5, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertEquals(5, result.getUsableSamples());
        }

        @Test
        void reportsVotes() {
            List<LineInfo> lines = new java.util.ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                lines.add(baseLine(i, 4));
            }
            lines.add(baseLine(4, 2));
            Map<Integer, LineKind> kinds = allBase(4);
            StructuralDepthModel model = uniformDepthModel(4, 1);

            InferenceResult result = IndentInferenceEngine.infer(lines, kinds, model);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getInferredUnit());
            assertEquals(Integer.valueOf(3), result.getVotes().get(4));
            assertEquals(Integer.valueOf(1), result.getVotes().get(2));
        }
    }

    private static StructuralDepthModel uniformDepthModel(int lineCount, int depth) {
        Map<Integer, Integer> depthMap = new HashMap<>();
        for (int i = 1; i <= lineCount; i++) {
            depthMap.put(i, depth);
        }
        return new StructuralDepthModel(depthMap);
    }
}

