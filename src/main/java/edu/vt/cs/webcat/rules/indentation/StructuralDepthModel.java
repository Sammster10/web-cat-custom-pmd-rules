package edu.vt.cs.webcat.rules.indentation;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class StructuralDepthModel {

    private final Map<Integer, Integer> lineDepths;

    StructuralDepthModel(Map<Integer, Integer> lineDepths) {
        this.lineDepths = lineDepths;
    }

    public int getDepth(int lineNumber) {
        return lineDepths.getOrDefault(lineNumber, 0);
    }

    public Map<Integer, Integer> getAllDepths() {
        return Collections.unmodifiableMap(lineDepths);
    }

    public static StructuralDepthModel build(Node rootNode, int totalLines) {
        Map<Integer, Integer> depthMap = new HashMap<>();
        for (int i = 1; i <= totalLines; i++) {
            depthMap.put(i, 0);
        }
        walk(rootNode, 0, depthMap);
        return new StructuralDepthModel(depthMap);
    }

    private static void walk(Node node, int depth, Map<Integer, Integer> depthMap) {
        if (node instanceof ASTCompilationUnit) {
            walkChildren(node, 0, depthMap);
            return;
        }

        if (node instanceof ASTPackageDeclaration
                || node instanceof ASTImportDeclaration) {
            setStartLineDepth(depthMap, node, 0);
            return;
        }

        if (isTypeDeclaration(node)) {
            setStartLineDepth(depthMap, node, depth);
            Node body = findClassBody(node);
            if (body != null) {
                int braceRegionEnd = body.getBeginLine();
                for (int line = node.getBeginLine(); line <= braceRegionEnd; line++) {
                    depthMap.put(line, depth);
                }
                setClosingBraceDepth(depthMap, body, depth);
            }
            walkTypeBody(node, depth + 1, depthMap);
            return;
        }

        if (node instanceof ASTEnumConstant) {
            setStartLineDepth(depthMap, node, depth);
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTAnonymousClassDeclaration) {
                    setClosingBraceDepth(depthMap, child, depth);
                    walkTypeBody(child, depth + 1, depthMap);
                }
            }
            return;
        }

        if (node instanceof ASTMethodDeclaration
                || node instanceof ASTConstructorDeclaration) {
            assignAnnotations(node, depth, depthMap);
            setStartLineDepth(depthMap, node, depth);
            Node block = null;
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTBlock) {
                    block = child;
                    break;
                }
            }
            if (block != null) {
                for (int line = node.getBeginLine(); line < block.getBeginLine(); line++) {
                    depthMap.put(line, depth);
                }
                walkBlock(block, depth, depthMap);
            } else {
                setAllLinesDepth(depthMap, node, depth);
            }
            return;
        }

        if (node instanceof ASTFieldDeclaration) {
            assignAnnotations(node, depth, depthMap);
            setAllLinesDepth(depthMap, node, depth);
            assignFieldChildLines(node, depth, depthMap);
            walkChildExpressions(node, depth, depthMap);
            return;
        }

        if (node instanceof ASTInitializer) {
            setStartLineDepth(depthMap, node, depth);
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTBlock) {
                    walkBlock(child, depth, depthMap);
                }
            }
            return;
        }

        if (node instanceof ASTBlock) {
            walkBlock(node, depth, depthMap);
            return;
        }

        if (node instanceof ASTSwitchStatement || node instanceof ASTSwitchExpression) {
            walkSwitch(node, depth, depthMap);
            return;
        }

        if (isControlStructure(node)) {
            walkControlStructure(node, depth, depthMap);
            return;
        }

        if (node instanceof ASTLambdaExpression) {
            walkLambda(node, depth, depthMap);
            return;
        }

        if (node instanceof ASTLocalVariableDeclaration
                || node instanceof ASTExpressionStatement
                || node instanceof ASTReturnStatement
                || node instanceof ASTThrowStatement
                || node instanceof ASTAssertStatement
                || node instanceof ASTBreakStatement
                || node instanceof ASTContinueStatement
                || node instanceof ASTYieldStatement
                || node instanceof ASTExplicitConstructorInvocation) {
            setAllLinesDepth(depthMap, node, depth);
            assignFieldChildLines(node, depth, depthMap);
            walkChildExpressions(node, depth, depthMap);
            return;
        }

        walkChildren(node, depth, depthMap);
    }

    private static void walkTypeBody(Node typeNode, int memberDepth,
                                     Map<Integer, Integer> depthMap) {
        for (int i = 0; i < typeNode.getNumChildren(); i++) {
            Node child = typeNode.getChild(i);
            if (child instanceof ASTModifierList) {
                continue;
            }
            walk(child, memberDepth, depthMap);
        }
    }

    private static void walkBlock(Node block, int ownerDepth,
                                  Map<Integer, Integer> depthMap) {
        depthMap.put(block.getBeginLine(), ownerDepth);
        setClosingBraceDepth(depthMap, block, ownerDepth);
        int statementDepth = ownerDepth + 1;
        for (int i = 0; i < block.getNumChildren(); i++) {
            walk(block.getChild(i), statementDepth, depthMap);
        }
    }

    private static void walkSwitch(Node switchNode, int depth,
                                   Map<Integer, Integer> depthMap) {
        setStartLineDepth(depthMap, switchNode, depth);
        setClosingBraceDepth(depthMap, switchNode, depth);

        int firstChildLine = findFirstSwitchChildLine(switchNode);
        if (firstChildLine > switchNode.getBeginLine()) {
            for (int line = switchNode.getBeginLine(); line < firstChildLine; line++) {
                depthMap.put(line, depth);
            }
        }

        int caseDepth = depth + 1;
        int caseBodyDepth = depth + 2;

        for (int i = 0; i < switchNode.getNumChildren(); i++) {
            Node child = switchNode.getChild(i);
            if (child instanceof ASTSwitchLabel) {
                setStartLineDepth(depthMap, child, caseDepth);
            } else if (child instanceof ASTSwitchArrowBranch) {
                setStartLineDepth(depthMap, child, caseDepth);
                for (int j = 0; j < child.getNumChildren(); j++) {
                    Node grandchild = child.getChild(j);
                    if (grandchild instanceof ASTSwitchLabel) {
                        continue;
                    }
                    if (grandchild instanceof ASTBlock) {
                        walkBlock(grandchild, caseDepth, depthMap);
                    } else {
                        walk(grandchild, caseBodyDepth, depthMap);
                    }
                }
            } else if (child instanceof ASTSwitchFallthroughBranch) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    Node grandchild = child.getChild(j);
                    if (grandchild instanceof ASTSwitchLabel) {
                        setStartLineDepth(depthMap, grandchild, caseDepth);
                    } else {
                        walk(grandchild, caseBodyDepth, depthMap);
                    }
                }
            } else {
                walk(child, caseBodyDepth, depthMap);
            }
        }
    }

    private static int findFirstSwitchChildLine(Node switchNode) {
        int min = switchNode.getEndLine();
        for (int i = 0; i < switchNode.getNumChildren(); i++) {
            Node child = switchNode.getChild(i);
            if (child.getBeginLine() > switchNode.getBeginLine()
                    && child.getBeginLine() < min) {
                min = child.getBeginLine();
            }
        }
        return min;
    }

    private static void walkControlStructure(Node node, int depth,
                                             Map<Integer, Integer> depthMap) {
        setStartLineDepth(depthMap, node, depth);

        if (node instanceof ASTIfStatement) {
            ASTIfStatement ifStmt = (ASTIfStatement) node;
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTBlock) {
                    walkBlock(child, depth, depthMap);
                } else if (child instanceof ASTIfStatement) {
                    walkElseIf(child, depth, depthMap);
                } else if (isStatement(child) && i > 0) {
                    walk(child, depth + 1, depthMap);
                }
            }
            handleElseLine(ifStmt, depth, depthMap);
            return;
        }

        if (node instanceof ASTTryStatement) {
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTBlock) {
                    walkBlock(child, depth, depthMap);
                } else if (child instanceof ASTCatchClause) {
                    setStartLineDepth(depthMap, child, depth);
                    for (int j = 0; j < child.getNumChildren(); j++) {
                        Node grandchild = child.getChild(j);
                        if (grandchild instanceof ASTBlock) {
                            walkBlock(grandchild, depth, depthMap);
                        }
                    }
                } else if (child instanceof ASTFinallyClause) {
                    setStartLineDepth(depthMap, child, depth);
                    for (int j = 0; j < child.getNumChildren(); j++) {
                        Node grandchild = child.getChild(j);
                        if (grandchild instanceof ASTBlock) {
                            walkBlock(grandchild, depth, depthMap);
                        }
                    }
                } else if (child instanceof ASTResourceList) {
                    setStartLineDepth(depthMap, child, depth);
                }
            }
            return;
        }

        if (node instanceof ASTDoStatement) {
            for (int i = 0; i < node.getNumChildren(); i++) {
                Node child = node.getChild(i);
                if (child instanceof ASTBlock) {
                    walkBlock(child, depth, depthMap);
                    for (int line = child.getEndLine() + 1; line <= node.getEndLine(); line++) {
                        depthMap.put(line, depth);
                    }
                } else if (isStatement(child)
                        && child.getBeginLine() != node.getBeginLine()) {
                    walk(child, depth + 1, depthMap);
                }
            }
            depthMap.put(node.getEndLine(), depth);
            return;
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            if (child instanceof ASTBlock) {
                walkBlock(child, depth, depthMap);
            } else if (isStatement(child)
                    && child.getBeginLine() != node.getBeginLine()) {
                walk(child, depth + 1, depthMap);
            }
        }
    }

    private static void walkElseIf(Node ifNode, int depth,
                                   Map<Integer, Integer> depthMap) {
        setStartLineDepth(depthMap, ifNode, depth);
        for (int i = 0; i < ifNode.getNumChildren(); i++) {
            Node child = ifNode.getChild(i);
            if (child instanceof ASTBlock) {
                walkBlock(child, depth, depthMap);
            } else if (child instanceof ASTIfStatement) {
                walkElseIf(child, depth, depthMap);
            } else if (isStatement(child) && i > 0) {
                walk(child, depth + 1, depthMap);
            }
        }
        if (ifNode instanceof ASTIfStatement) {
            handleElseLine((ASTIfStatement) ifNode, depth, depthMap);
        }
    }

    private static void handleElseLine(ASTIfStatement ifStmt, int depth,
                                       Map<Integer, Integer> depthMap) {
        if (ifStmt.hasElse()) {
            Node elseChild = ifStmt.getChild(ifStmt.getNumChildren() - 1);
            int elseLine = elseChild.getBeginLine();
            if (elseChild instanceof ASTIfStatement) {
                depthMap.put(elseLine, depth);
            } else if (elseChild instanceof ASTBlock) {
                int prevChildEnd = getPreviousChildEnd(ifStmt);
                if (prevChildEnd > 0) {
                    for (int line = prevChildEnd + 1; line <= elseLine; line++) {
                        depthMap.put(line, depth);
                    }
                }
            } else {
                int prevChildEnd = getPreviousChildEnd(ifStmt);
                if (prevChildEnd > 0 && prevChildEnd < elseLine) {
                    for (int line = prevChildEnd + 1; line < elseLine; line++) {
                        depthMap.put(line, depth);
                    }
                }
            }
        }
    }

    private static int getPreviousChildEnd(ASTIfStatement ifStmt) {
        int prevChildEnd = -1;
        for (int i = 0; i < ifStmt.getNumChildren() - 1; i++) {
            prevChildEnd = ifStmt.getChild(i).getEndLine();
        }
        return prevChildEnd;
    }

    private static void walkLambda(Node lambda, int depth,
                                   Map<Integer, Integer> depthMap) {
        for (int i = 0; i < lambda.getNumChildren(); i++) {
            Node child = lambda.getChild(i);
            if (child instanceof ASTBlock) {
                walkBlock(child, depth, depthMap);
            }
        }
    }

    private static void walkChildExpressions(Node node, int depth,
                                             Map<Integer, Integer> depthMap) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            if (child instanceof ASTLambdaExpression) {
                walkLambda(child, depth, depthMap);
            } else if (child instanceof ASTAnonymousClassDeclaration) {
                setClosingBraceDepth(depthMap, child, depth);
                walkTypeBody(child, depth + 1, depthMap);
            } else if (child instanceof ASTSwitchExpression) {
                walkSwitch(child, depth, depthMap);
            } else {
                walkChildExpressions(child, depth, depthMap);
            }
        }
    }

    private static void walkChildren(Node node, int depth,
                                     Map<Integer, Integer> depthMap) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            walk(node.getChild(i), depth, depthMap);
        }
    }

    private static void assignAnnotations(Node node, int depth,
                                          Map<Integer, Integer> depthMap) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            if (child instanceof ASTModifierList) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    Node mod = child.getChild(j);
                    if (mod instanceof ASTAnnotation) {
                        int annotLine = mod.getBeginLine();
                        if (annotLine < node.getBeginLine()
                                || (annotLine == node.getBeginLine()
                                && mod.getEndLine() < node.getEndLine())) {
                            for (int line = mod.getBeginLine(); line <= mod.getEndLine(); line++) {
                                depthMap.put(line, depth);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void setStartLineDepth(Map<Integer, Integer> depthMap,
                                          Node node, int depth) {
        depthMap.put(node.getBeginLine(), depth);
    }

    private static void setAllLinesDepth(Map<Integer, Integer> depthMap,
                                         Node node, int depth) {
        for (int line = node.getBeginLine(); line <= node.getEndLine(); line++) {
            depthMap.put(line, depth);
        }
    }

    private static void assignFieldChildLines(Node node, int depth,
                                              Map<Integer, Integer> depthMap) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            for (int line = child.getBeginLine(); line <= child.getEndLine(); line++) {
                depthMap.put(line, depth);
            }
            assignFieldChildLines(child, depth, depthMap);
        }
    }

    private static void setClosingBraceDepth(Map<Integer, Integer> depthMap,
                                             Node node, int depth) {
        int endLine = node.getEndLine();
        depthMap.put(endLine, depth);
    }

    private static boolean isTypeDeclaration(Node node) {
        return node instanceof ASTClassDeclaration
                || node instanceof ASTEnumDeclaration
                || node instanceof ASTRecordDeclaration
                || node instanceof ASTAnnotationTypeDeclaration;
    }

    private static Node findClassBody(Node typeNode) {
        Node best = null;
        for (int i = 0; i < typeNode.getNumChildren(); i++) {
            Node child = typeNode.getChild(i);
            if (child instanceof ASTModifierList) {
                continue;
            }
            if (best == null || child.getEndLine() > best.getEndLine()) {
                best = child;
            }
        }
        return best;
    }

    private static boolean isControlStructure(Node node) {
        return node instanceof ASTIfStatement
                || node instanceof ASTWhileStatement
                || node instanceof ASTDoStatement
                || node instanceof ASTForStatement
                || node instanceof ASTForeachStatement
                || node instanceof ASTTryStatement
                || node instanceof ASTSynchronizedStatement;
    }

    private static boolean isStatement(Node node) {
        return node instanceof ASTLocalVariableDeclaration
                || node instanceof ASTExpressionStatement
                || node instanceof ASTReturnStatement
                || node instanceof ASTThrowStatement
                || node instanceof ASTBreakStatement
                || node instanceof ASTContinueStatement
                || node instanceof ASTYieldStatement
                || node instanceof ASTAssertStatement
                || node instanceof ASTExplicitConstructorInvocation
                || node instanceof ASTBlock
                || isControlStructure(node)
                || node instanceof ASTSwitchStatement;
    }
}

