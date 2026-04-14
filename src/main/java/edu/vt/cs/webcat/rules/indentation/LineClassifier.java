package edu.vt.cs.webcat.rules.indentation;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;

import java.util.*;
import java.util.regex.Pattern;

public final class LineClassifier {

    private static final Pattern STARTS_WITH_CONTINUATION_TOKEN = Pattern.compile(
            "^\\s*(?:\\.|,|->|\\)|]|\\+[^+]?|-[^-]?|\\*|/[^/*]|%|&&|\\|\\||\\?|:[^:])");

    private static final Pattern ENDS_WITH_CONTINUATION = Pattern.compile(
            "(?:\\(|\\[|\\.|,|\\+|-|\\*|/|%|&&|\\|\\||\\?|:|->|\\{)\\s*(?://.*)?$");

    private static final Set<String> CLAUSE_KEYWORDS = new HashSet<>(
            Arrays.asList("extends", "implements", "permits", "throws"));

    private LineClassifier() {
    }

    public static Map<Integer, LineKind> classify(List<LineInfo> lines,
                                                  StructuralDepthModel depthModel,
                                                  Node rootNode) {
        Map<Integer, LineKind> result = new LinkedHashMap<>();
        Set<Integer> baseLines = collectBaseLines(rootNode);
        Set<Integer> multiLineStartLines = collectMultiLineConstructStartLines(rootNode);
        Set<Integer> blockStartLines = collectBlockStartLines(rootNode);

        for (int i = 0; i < lines.size(); i++) {
            LineInfo info = lines.get(i);
            int lineNum = info.getLineNumber();

            if (info.isBlank() || info.isCommentOnly() || info.isInsideBlockComment()
                    || info.isInsideJavadoc() || info.isInsideTextBlock()) {
                result.put(lineNum, LineKind.IGNORE);
                continue;
            }

            if (info.hasLeadingTab()) {
                result.put(lineNum, LineKind.IGNORE);
                continue;
            }

            boolean isCont = isContinuationLine(info, lines, i, multiLineStartLines);
            if (baseLines.contains(lineNum)) {
                String trimmed = info.getText().trim();
                if (isCont && blockStartLines.contains(lineNum)
                        && !trimmed.equals("{")) {
                    result.put(lineNum, LineKind.CONTINUATION);
                } else {
                    result.put(lineNum, LineKind.BASE);
                }
            } else if (isCont) {
                result.put(lineNum, LineKind.CONTINUATION);
            } else {
                result.put(lineNum, LineKind.BASE);
            }
        }

        return result;
    }

    private static boolean isContinuationLine(LineInfo info, List<LineInfo> lines,
                                              int index,
                                              Set<Integer> multiLineStartLines) {
        String trimmed = info.getText().trim();

        if (trimmed.equals(")") || trimmed.equals(");") || trimmed.equals("),")
                || trimmed.equals("]") || trimmed.equals("];") || trimmed.equals("],")) {
            return false;
        }

        if (STARTS_WITH_CONTINUATION_TOKEN.matcher(info.getText()).find()) {
            if (!trimmed.startsWith("}") && !trimmed.startsWith(");}")) {
                return true;
            }
        }

        if (trimmed.startsWith(".")) {
            return true;
        }

        LineInfo prev = findPreviousNonBlankNonComment(lines, index);
        if (prev != null) {
            String prevTrimmed = prev.getText().trim();

            if (ENDS_WITH_CONTINUATION.matcher(prevTrimmed).find()) {
                if (!prevTrimmed.endsWith("{") || isWrappedConstruct(prevTrimmed)) {
                    return true;
                }
            }

            if (multiLineStartLines.contains(prev.getLineNumber())
                    && !trimmed.startsWith("{") && !trimmed.startsWith("}")) {
                return true;
            }

            if (endsWithClauseKeyword(prevTrimmed)) {
                return true;
            }

            if (startsWithClauseKeyword(trimmed)
                    && !trimmed.startsWith("extends {")
                    && !trimmed.startsWith("implements {")) {
                return true;
            }
        }

        return false;
    }

    private static boolean isWrappedConstruct(String line) {
        return line.endsWith("(") || line.endsWith("[") || line.endsWith(",")
                || line.endsWith("({") || line.endsWith("= {")
                || line.endsWith("[]{") || line.endsWith("]{");
    }

    private static boolean endsWithClauseKeyword(String line) {
        for (String kw : CLAUSE_KEYWORDS) {
            if (line.endsWith(kw)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithClauseKeyword(String line) {
        for (String kw : CLAUSE_KEYWORDS) {
            if (line.startsWith(kw + " ") || line.startsWith(kw + "\t")
                    || line.equals(kw)) {
                return true;
            }
        }
        return false;
    }

    private static LineInfo findPreviousNonBlankNonComment(List<LineInfo> lines,
                                                           int currentIndex) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            LineInfo candidate = lines.get(i);
            if (!candidate.isBlank() && !candidate.isCommentOnly()
                    && !candidate.isInsideBlockComment()
                    && !candidate.isInsideJavadoc()
                    && !candidate.isInsideTextBlock()) {
                return candidate;
            }
        }
        return null;
    }

    private static Set<Integer> collectBaseLines(Node root) {
        Set<Integer> baseLines = new HashSet<>();
        collectBaseLinesRecursive(root, baseLines);
        return baseLines;
    }

    private static void collectBaseLinesRecursive(Node node, Set<Integer> baseLines) {
        if (node instanceof ASTPackageDeclaration
                || node instanceof ASTImportDeclaration) {
            baseLines.add(node.getBeginLine());
            return;
        }

        if (isTypeDeclaration(node)
                || node instanceof ASTMethodDeclaration
                || node instanceof ASTConstructorDeclaration
                || node instanceof ASTFieldDeclaration
                || node instanceof ASTEnumConstant
                || node instanceof ASTInitializer) {
            baseLines.add(node.getBeginLine());
            int endLine = node.getEndLine();
            if (endLine != node.getBeginLine()) {
                baseLines.add(endLine);
            }
            if (isTypeDeclaration(node)) {
                int bodyEnd = findBodyEndLine(node);
                if (bodyEnd > 0) {
                    baseLines.add(bodyEnd);
                }
            }
        }

        if (node instanceof ASTBlock) {
            baseLines.add(node.getBeginLine());
            baseLines.add(node.getEndLine());
        }

        if (node instanceof ASTSwitchLabel) {
            baseLines.add(node.getBeginLine());
        }

        if (node instanceof ASTSwitchArrowBranch) {
            baseLines.add(node.getBeginLine());
        }

        if (node instanceof ASTSwitchFallthroughBranch) {
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTSwitchLabel) {
                    baseLines.add(child.getBeginLine());
                }
            }
        }

        if (isStatement(node)) {
            baseLines.add(node.getBeginLine());
        }

        if (node instanceof ASTIfStatement) {
            ASTIfStatement ifStmt = (ASTIfStatement) node;
            baseLines.add(ifStmt.getBeginLine());
            if (ifStmt.hasElse()) {
                Node lastChild = ifStmt.getChild(ifStmt.getNumChildren() - 1);
                baseLines.add(lastChild.getBeginLine());
            }
        }

        if (node instanceof ASTCatchClause || node instanceof ASTFinallyClause) {
            baseLines.add(node.getBeginLine());
        }

        if (node instanceof ASTAnnotation) {
            Node parent = node.getParent();
            if (parent instanceof ASTModifierList) {
                Node ownerNode = parent.getParent();
                if (ownerNode != null && node.getEndLine() < ownerNode.getEndLine()) {
                    if (node.getBeginLine() != ownerNode.getBeginLine()
                            || isStandaloneAnnotationLine(node, ownerNode)) {
                        baseLines.add(node.getBeginLine());
                    }
                }
            }
        }

        if (node instanceof ASTSwitchStatement || node instanceof ASTSwitchExpression) {
            baseLines.add(node.getBeginLine());
            baseLines.add(node.getEndLine());
        }

        if (node instanceof ASTDoStatement) {
            baseLines.add(node.getBeginLine());
            baseLines.add(node.getEndLine());
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            collectBaseLinesRecursive(node.getChild(i), baseLines);
        }
    }

    private static boolean isStandaloneAnnotationLine(Node annotation, Node owner) {
        return annotation.getEndLine() < getDeclarationStartLine(owner);
    }

    private static int getDeclarationStartLine(Node node) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            if (!(child instanceof ASTModifierList)) {
                return child.getBeginLine();
            }
        }
        return node.getBeginLine();
    }

    private static Set<Integer> collectMultiLineConstructStartLines(Node root) {
        Set<Integer> result = new HashSet<>();
        collectMultiLineRecursive(root, result);
        return result;
    }

    private static void collectMultiLineRecursive(Node node, Set<Integer> result) {
        if ((node instanceof ASTMethodDeclaration
                || node instanceof ASTConstructorDeclaration)
                && node.getBeginLine() != node.getEndLine()) {
            Node body = findBlock(node);
            if (body != null && body.getBeginLine() > node.getBeginLine()) {
                for (int line = node.getBeginLine(); line < body.getBeginLine(); line++) {
                    result.add(line);
                }
            }
        }

        if (isTypeDeclaration(node)) {
            int bodyBeginLine = findTypeBodyBeginLine(node);
            if (bodyBeginLine > node.getBeginLine()) {
                for (int line = node.getBeginLine(); line < bodyBeginLine; line++) {
                    result.add(line);
                }
            }
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            collectMultiLineRecursive(node.getChild(i), result);
        }
    }

    private static Node findBlock(Node node) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (node.getChild(i) instanceof ASTBlock) {
                return node.getChild(i);
            }
        }
        return null;
    }

    private static boolean isTypeDeclaration(Node node) {
        return node instanceof ASTClassDeclaration
                || node instanceof ASTEnumDeclaration
                || node instanceof ASTRecordDeclaration
                || node instanceof ASTAnnotationTypeDeclaration;
    }

    private static Set<Integer> collectBlockStartLines(Node root) {
        Set<Integer> result = new HashSet<>();
        collectBlockStartLinesRecursive(root, result);
        return result;
    }

    private static void collectBlockStartLinesRecursive(Node node, Set<Integer> result) {
        if (node instanceof ASTBlock) {
            result.add(node.getBeginLine());
        }
        for (int i = 0; i < node.getNumChildren(); i++) {
            collectBlockStartLinesRecursive(node.getChild(i), result);
        }
    }

    private static int findBodyEndLine(Node typeNode) {
        int maxEnd = 0;
        for (int i = 0; i < typeNode.getNumChildren(); i++) {
            Node child = typeNode.getChild(i);
            if (child instanceof ASTModifierList) {
                continue;
            }
            if (child.getEndLine() > maxEnd) {
                maxEnd = child.getEndLine();
            }
        }
        return maxEnd;
    }

    private static int findTypeBodyBeginLine(Node typeNode) {
        for (int i = 0; i < typeNode.getNumChildren(); i++) {
            Node child = typeNode.getChild(i);
            if (child instanceof ASTModifierList) {
                continue;
            }
            if (child.getBeginLine() != child.getEndLine()
                    || child.getEndLine() == typeNode.getEndLine()) {
                return child.getBeginLine();
            }
        }
        return typeNode.getBeginLine();
    }

    private static boolean isStatement(Node node) {
        return node instanceof ASTLocalVariableDeclaration
                || node instanceof ASTExpressionStatement
                || node instanceof ASTReturnStatement
                || node instanceof ASTThrowStatement
                || node instanceof ASTBreakStatement
                || node instanceof ASTContinueStatement
                || node instanceof ASTAssertStatement
                || node instanceof ASTYieldStatement
                || node instanceof ASTExplicitConstructorInvocation
                || node instanceof ASTWhileStatement
                || node instanceof ASTDoStatement
                || node instanceof ASTForStatement
                || node instanceof ASTForeachStatement
                || node instanceof ASTSynchronizedStatement
                || node instanceof ASTTryStatement;
    }
}

