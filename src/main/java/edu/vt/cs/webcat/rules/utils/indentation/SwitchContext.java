package edu.vt.cs.webcat.rules.utils.indentation;

/** Indentation bonuses restored when leaving a switch block. */
public final class SwitchContext {

    private final int braceDepth;
    private final int previousFrozenBonus;
    private final int previousActiveCaseBonus;

    public SwitchContext(int braceDepth, int previousFrozenBonus,
                         int previousActiveCaseBonus) {
        this.braceDepth = braceDepth;
        this.previousFrozenBonus = previousFrozenBonus;
        this.previousActiveCaseBonus = previousActiveCaseBonus;
    }

    public int getBraceDepth() {
        return braceDepth;
    }

    public int getPreviousFrozenBonus() {
        return previousFrozenBonus;
    }

    public int getPreviousActiveCaseBonus() {
        return previousActiveCaseBonus;
    }
}
