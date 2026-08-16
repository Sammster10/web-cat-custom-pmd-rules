package edu.vt.cs.webcat.rules.utils.spacing;

import net.sourceforge.pmd.lang.ast.GenericToken;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.document.FileLocation;
import net.sourceforge.pmd.lang.document.TextRegion;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTTypeArguments;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameters;
import net.sourceforge.pmd.lang.java.ast.JavaTokenKinds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reusable token mechanics shared by spacing rules and their contexts. */
public final class SpacingUtils {

    private SpacingUtils() {
        throw new AssertionError("Utility class");
    }

    public static List<JavaccToken> tokens(ASTCompilationUnit root) {
        List<JavaccToken> tokens = new ArrayList<>();
        for (JavaccToken token : GenericToken.range(
                root.getFirstToken(), root.getLastToken())) {
            tokens.add(token);
        }
        return tokens;
    }

    public static int startOf(JavaccToken token) {
        return token.getRegion().getStartOffset();
    }

    public static int endOf(JavaccToken token) {
        return token.getRegion().getEndOffset();
    }

    public static Set<Integer> genericAngleBracketOffsets(
            ASTCompilationUnit root) {
        Set<Integer> offsets = new HashSet<>();
        for (ASTTypeArguments arguments
                : root.descendants(ASTTypeArguments.class)) {
            offsets.add(startOf(arguments.getFirstToken()));
            offsets.add(startOf(arguments.getLastToken()));
        }
        for (ASTTypeParameters parameters
                : root.descendants(ASTTypeParameters.class)) {
            offsets.add(startOf(parameters.getFirstToken()));
            offsets.add(startOf(parameters.getLastToken()));
        }
        return offsets;
    }

    public static boolean hasWhitespaceBetween(
            JavaccToken first, JavaccToken second) {
        return startOf(second) > endOf(first);
    }

    public static boolean hasHorizontalWhitespaceBetween(
            JavaccToken first, JavaccToken second) {
        if (startOf(second) <= endOf(first)) {
            return false;
        }
        String text = first.getDocument().getTextDocument()
                .sliceOriginalText(TextRegion.fromBothOffsets(
                        endOf(first), startOf(second)))
                .toString();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == ' ' || character == '\t') {
                return true;
            }
        }
        return false;
    }

    public static boolean sameLine(JavaccToken first, JavaccToken second) {
        FileLocation firstLocation = first.getReportLocation();
        FileLocation secondLocation = second.getReportLocation();
        return firstLocation.getEndLine() == secondLocation.getStartLine();
    }

    public static boolean isModifierOrKeyword(JavaccToken token) {
        int kind = token.kind;
        return kind == JavaTokenKinds.PUBLIC || kind == JavaTokenKinds.PRIVATE
                || kind == JavaTokenKinds.PROTECTED || kind == JavaTokenKinds.STATIC
                || kind == JavaTokenKinds.FINAL || kind == JavaTokenKinds.ABSTRACT
                || kind == JavaTokenKinds.NATIVE || kind == JavaTokenKinds.TRANSIENT
                || kind == JavaTokenKinds.VOLATILE || kind == JavaTokenKinds.STRICTFP
                || kind == JavaTokenKinds.SYNCHRONIZED
                || kind == JavaTokenKinds.VOID || kind == JavaTokenKinds.CLASS
                || kind == JavaTokenKinds.INTERFACE || kind == JavaTokenKinds.RETURN;
    }
}
