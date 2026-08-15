package edu.vt.cs.webcat.rules.indentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndentEnforcer {

    private IndentEnforcer() {
    }

    public static List<IndentViolation> enforce(List<LineInfo> lines,
                                                Map<Integer, LineKind> classifications,
                                                StructuralDepthModel depthModel,
                                                int indentUnit) {
        List<IndentViolation> violations = new ArrayList<>();

        for (LineInfo line : lines) {
            int lineNum = line.getLineNumber();
            LineKind kind = classifications.getOrDefault(lineNum, LineKind.IGNORE);

            if (kind == LineKind.IGNORE) {
                continue;
            }

            if (line.isBlank() || line.hasLeadingTab()) {
                continue;
            }

            int depth = depthModel.getDepth(lineNum);
            int actual = line.getLeadingSpaces();

            if (kind == LineKind.BASE) {
                int expected = depth * indentUnit;
                if (actual != expected) {
                    violations.add(new IndentViolation(lineNum,
                            String.format("Incorrect indentation: expected %d spaces "
                                            + "for depth %d using inferred unit %d, found %d.",
                                    expected, depth, indentUnit, actual)));
                }
            } else if (kind == LineKind.CONTINUATION
                    || kind == LineKind.BASE_OR_CONTINUATION) {
                int base = depth * indentUnit;
                if (kind == LineKind.BASE_OR_CONTINUATION && actual == base) {
                    continue;
                }

                int minimum = (depth + 1) * indentUnit;
                if (actual < minimum) {
                    String expectation = kind == LineKind.BASE_OR_CONTINUATION
                            ? String.format("either %d spaces or at least %d spaces", base, minimum)
                            : String.format("at least %d spaces", minimum);
                    violations.add(new IndentViolation(lineNum,
                            String.format("Incorrect continuation indentation: expected %s "
                                            + "using inferred unit %d, found %d.",
                                    expectation, indentUnit, actual)));
                } else if (actual % indentUnit != 0) {
                    int lower = (actual / indentUnit) * indentUnit;
                    int upper = lower + indentUnit;
                    violations.add(new IndentViolation(lineNum,
                            String.format("Incorrect continuation indentation: expected a "
                                            + "multiple of %d spaces (e.g. %d or %d), found %d.",
                                    indentUnit, lower, upper, actual)));
                }
            }
        }

        return violations;
    }
}

