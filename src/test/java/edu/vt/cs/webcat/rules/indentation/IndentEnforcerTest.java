package edu.vt.cs.webcat.rules.indentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndentEnforcerTest {

    private static LineInfo line(int lineNum, int leadingSpaces) {
        String text = spaces(leadingSpaces) + "int x;";
        return new LineInfo(lineNum, text, leadingSpaces,
                false, false, false, false, false, false);
    }

    private static LineInfo tabLine(int lineNum) {
        return new LineInfo(lineNum, "\tint x;", 0,
                true, false, false, false, false, false);
    }

    private static LineInfo blankLine(int lineNum) {
        return new LineInfo(lineNum, "", 0,
                false, true, false, false, false, false);
    }

    private static String spaces(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(' ');
        return sb.toString();
    }

    @Nested
    class BaseLineEnforcement {
        @Test
        void correctBaseLineNoViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 4));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 1));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void incorrectBaseLineViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 6));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 1));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("expected 4 spaces"));
        }

        @Test
        void depthZeroBaseLineAtColumnZero() {
            List<LineInfo> lines = Collections.singletonList(line(1, 0));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 0));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    class ContinuationLineEnforcement {
        @Test
        void continuationAtExactlyOneExtraLevelNoViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 12));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void continuationAtTwoExtraLevelsNoViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 16));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void insufficientContinuationViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 4));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("at least 12 spaces"));
        }

        @Test
        void continuationNotMultipleOfUnitViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 14));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("multiple of 4"));
        }

        @Test
        void continuationNotMultipleShowsNearestOptions() {
            List<LineInfo> lines = Collections.singletonList(line(1, 13));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("12 or 16"));
        }

        @Test
        void continuationExactlyAtBaseDepthViolation() {
            List<LineInfo> lines = Collections.singletonList(line(1, 8));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("at least 12 spaces"));
        }

        @Test
        void continuationWithTwoSpaceUnit() {
            List<LineInfo> lines = Collections.singletonList(line(1, 6));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 2);
            assertTrue(violations.isEmpty());
        }

        @Test
        void continuationWithTwoSpaceUnitNotMultiple() {
            List<LineInfo> lines = Collections.singletonList(line(1, 7));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 2);
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).getMessage().contains("multiple of 2"));
        }
    }

    @Nested
    class IgnoredLines {
        @Test
        void ignoreLineNotEnforced() {
            List<LineInfo> lines = Collections.singletonList(blankLine(1));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.IGNORE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 1));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void tabLineNotEnforced() {
            List<LineInfo> lines = Collections.singletonList(tabLine(1));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 1));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    class MultipleLines {
        @Test
        void mixOfCorrectAndIncorrectLines() {
            List<LineInfo> lines = List.of(
                    line(1, 4),
                    line(2, 6),
                    line(3, 4));
            Map<Integer, LineKind> kinds = Map.of(
                    1, LineKind.BASE,
                    2, LineKind.BASE,
                    3, LineKind.BASE);
            Map<Integer, Integer> depthMap = Map.of(1, 1, 2, 1, 3, 1);
            StructuralDepthModel model = new StructuralDepthModel(depthMap);

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
            assertEquals(2, violations.get(0).getLineNumber());
        }

        @Test
        void allCorrectNoViolations() {
            List<LineInfo> lines = List.of(
                    line(1, 0),
                    line(2, 4),
                    line(3, 8),
                    line(4, 4),
                    line(5, 0));
            Map<Integer, LineKind> kinds = Map.of(
                    1, LineKind.BASE,
                    2, LineKind.BASE,
                    3, LineKind.BASE,
                    4, LineKind.BASE,
                    5, LineKind.BASE);
            Map<Integer, Integer> depthMap = Map.of(1, 0, 2, 1, 3, 2, 4, 1, 5, 0);
            StructuralDepthModel model = new StructuralDepthModel(depthMap);

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void multipleViolationsReported() {
            List<LineInfo> lines = List.of(
                    line(1, 6),
                    line(2, 6),
                    line(3, 4));
            Map<Integer, LineKind> kinds = Map.of(
                    1, LineKind.BASE,
                    2, LineKind.BASE,
                    3, LineKind.BASE);
            Map<Integer, Integer> depthMap = Map.of(1, 1, 2, 1, 3, 1);
            StructuralDepthModel model = new StructuralDepthModel(depthMap);

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(2, violations.size());
        }
    }

    @Nested
    class ContinuationAtDepthZero {
        @Test
        void continuationAtDepthZeroMustBeAtLeastOneUnit() {
            List<LineInfo> lines = Collections.singletonList(line(1, 4));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 0));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertTrue(violations.isEmpty());
        }

        @Test
        void continuationAtDepthZeroTooShallow() {
            List<LineInfo> lines = Collections.singletonList(line(1, 2));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.CONTINUATION);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 0));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 4);
            assertEquals(1, violations.size());
        }
    }

    @Nested
    class DifferentIndentUnits {
        @Test
        void twoSpaceUnitCorrect() {
            List<LineInfo> lines = Collections.singletonList(line(1, 2));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 1));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 2);
            assertTrue(violations.isEmpty());
        }

        @Test
        void threeSpaceUnitCorrect() {
            List<LineInfo> lines = Collections.singletonList(line(1, 6));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 2));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 3);
            assertTrue(violations.isEmpty());
        }

        @Test
        void oneSpaceUnitCorrect() {
            List<LineInfo> lines = Collections.singletonList(line(1, 3));
            Map<Integer, LineKind> kinds = Collections.singletonMap(1, LineKind.BASE);
            StructuralDepthModel model = new StructuralDepthModel(
                    Collections.singletonMap(1, 3));

            List<IndentViolation> violations = IndentEnforcer.enforce(lines, kinds, model, 1);
            assertTrue(violations.isEmpty());
        }
    }
}

