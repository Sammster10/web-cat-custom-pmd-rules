package edu.vt.cs.webcat.rules.indentation;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;

import java.util.*;
import java.util.regex.Pattern;

public final class LineClassifier {

    private static final Pattern STARTS_WITH_CONTINUATION_TOKEN = Pattern.compile(
            "^\\s*(?:\\.|,|->|\\)|]|\\+[^+]?|-[^-]?|\\*|/[^/*]|%|&&|\\|\\||\\||&|\\?|:(?!:))");

    /*
     * Deliberately excludes bare < and >. They are grammar-sensitive in Java:
     * generic type syntax, generic method syntax, comparison operators, and
     * shift operators all share angle-bracket characters.
     */
    private static final Pattern ENDS_WITH_CONTINUATION = Pattern.compile(
            "(?:\\(|\\[|\\.|,|\\+|-|\\*|/|%|&&|\\|\\||\\||&|\\?|:(?!:)|->|\\{|=|==|!=|>=|<=|>>>|>>)\\s*$");

    private static final Pattern SIMPLE_LABEL_LINE = Pattern.compile(
            "^\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*:\\s*$");

    private static final Pattern SWITCH_LABEL_LINE = Pattern.compile(
            "^\\s*(?:case\\b.*|default)\\s*:\\s*$");

    private static final Set<String> CLAUSE_KEYWORDS = new HashSet<>(
            Arrays.asList("extends", "implements", "permits", "throws", "super"));

    private LineClassifier() {
    }

    public static Map<Integer, LineKind> classify(List<LineInfo> lines,
                                                  StructuralDepthModel depthModel,
                                                  Node rootNode) {
        Map<Integer, LineKind> result = new LinkedHashMap<>();
        Set<Integer> baseLines = collectBaseLines(rootNode);
        Set<Integer> multiLineStartLines = collectMultiLineConstructStartLines(rootNode);
        Set<Integer> enumConstantStartLines = collectEnumConstantStartLines(rootNode);
        AstLineFacts astFacts = AstLineFacts.from(rootNode);

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

            if (isTryResourceClosingLine(info, lines, i)) {
                result.put(lineNum, LineKind.IGNORE);
                continue;
            }

            boolean isCont = isContinuationLine(info, lines, i, multiLineStartLines, astFacts);

            if (isCont && astFacts.startsChainAfterAnonymousClass(lineNum)) {
                result.put(lineNum, LineKind.BASE_OR_CONTINUATION);
                continue;
            }

            if (enumConstantStartLines.contains(lineNum)
                    && !startsWithExplicitContinuationToken(trimmedCode(info))) {
                result.put(lineNum, LineKind.BASE);
                continue;
            }

            if (baseLines.contains(lineNum)) {
                if (isContinuationDespiteBaseLine(info, lines, i, isCont)) {
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

    private static boolean isContinuationDespiteBaseLine(LineInfo info,
                                                         List<LineInfo> lines,
                                                         int index,
                                                         boolean isCont) {
        if (!isCont) {
            return false;
        }

        String trimmed = stripTrailingLineComment(info.getText()).trim();

        if (isStandaloneClosingDelimiterLine(trimmed)) {
            return false;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("}")) {
            return false;
        }

        LineInfo prev = findPreviousNonBlankNonComment(lines, index);
        if (prev == null) {
            return true;
        }

        String prevTrimmed = stripTrailingLineComment(prev.getText()).trim();

        return leadingSpaceCount(info.getText()) > leadingSpaceCount(prev.getText())
                || startsWithExplicitContinuationToken(trimmed)
                || previousLineRequiresContinuation(prevTrimmed);
    }

    private static boolean isContinuationLine(LineInfo info,
                                              List<LineInfo> lines,
                                              int index,
                                              Set<Integer> multiLineStartLines,
                                              AstLineFacts astFacts) {
        String code = stripTrailingLineComment(info.getText());
        String trimmed = code.trim();

        if (isStandaloneClosingDelimiterLine(trimmed)) {
            return false;
        }

        if (STARTS_WITH_CONTINUATION_TOKEN.matcher(code).find()) {
            if (!trimmed.startsWith("}") && !trimmed.startsWith(");}")) {
                return true;
            }
        }

        if (trimmed.startsWith(".")) {
            return true;
        }

        LineInfo prev = findPreviousNonBlankNonComment(lines, index);
        if (prev == null) {
            return false;
        }

        String prevCode = stripTrailingLineComment(prev.getText());
        String prevTrimmed = prevCode.trim();

        if (SIMPLE_LABEL_LINE.matcher(prevCode).matches()
                || SWITCH_LABEL_LINE.matcher(prevCode).matches()) {
            return false;
        }

        if (isTryResourceListOpen(prevTrimmed)
                || isNextTopLevelTryResource(lines, index, prevTrimmed)) {
            return false;
        }

        if (prevTrimmed.endsWith(">")) {
            if (astFacts.trailingGreaterThanClosesGeneric(prev)) {
                return astFacts.genericCloseContinuesIntoLine(prev, info);
            }

            return true;
        }

        if (prevTrimmed.endsWith("<")) {
            return true;
        }

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

        return startsWithClauseKeyword(trimmed)
                && !trimmed.startsWith("extends {")
                && !trimmed.startsWith("implements {");
    }

    private static boolean isStandaloneClosingDelimiterLine(String trimmed) {
        return trimmed.equals(")")
                || trimmed.equals(");")
                || trimmed.equals("),")
                || trimmed.equals("]")
                || trimmed.equals("];")
                || trimmed.equals("],")
                || trimmed.startsWith(") {")
                || trimmed.startsWith(") throws ")
                || trimmed.startsWith("] {");
    }

    private static boolean isTryResourceListOpen(String trimmed) {
        return trimmed.equals("try (")
                || trimmed.matches("try\\s*\\(");
    }

    private static boolean isNextTopLevelTryResource(List<LineInfo> lines,
                                                     int currentIndex,
                                                     String previousTrimmed) {
        return previousTrimmed.endsWith(";")
                && isInsideOpenTryResourceList(lines, currentIndex);
    }

    private static boolean isTryResourceClosingLine(LineInfo info,
                                                    List<LineInfo> lines,
                                                    int index) {
        String trimmed = stripTrailingLineComment(info.getText()).trim();
        return trimmed.startsWith(")") && isInsideOpenTryResourceList(lines, index);
    }

    private static boolean isInsideOpenTryResourceList(List<LineInfo> lines, int currentIndex) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            LineInfo candidate = lines.get(i);
            if (candidate.isBlank() || candidate.isCommentOnly()
                    || candidate.isInsideBlockComment()
                    || candidate.isInsideJavadoc()
                    || candidate.isInsideTextBlock()) {
                continue;
            }

            String trimmed = stripTrailingLineComment(candidate.getText()).trim();
            if (isTryResourceListOpen(trimmed)) {
                return true;
            }
            if (trimmed.contains("{") || trimmed.startsWith("}")
                    || trimmed.startsWith(")")) {
                return false;
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

    private static boolean previousLineRequiresContinuation(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }

        if (SWITCH_LABEL_LINE.matcher(trimmed).matches()
                || SIMPLE_LABEL_LINE.matcher(trimmed).matches()) {
            return false;
        }

        return trimmed.endsWith(",")
                || trimmed.endsWith("(")
                || trimmed.endsWith("[")
                || trimmed.endsWith(".")
                || trimmed.endsWith("+")
                || trimmed.endsWith("-")
                || trimmed.endsWith("*")
                || trimmed.endsWith("/")
                || trimmed.endsWith("%")
                || trimmed.endsWith("&&")
                || trimmed.endsWith("||")
                || trimmed.endsWith("|")
                || trimmed.endsWith("&")
                || trimmed.endsWith("?")
                || trimmed.endsWith(":")
                || trimmed.endsWith("->")
                || trimmed.endsWith("=")
                || trimmed.endsWith("==")
                || trimmed.endsWith("!=")
                || trimmed.endsWith(">=")
                || trimmed.endsWith("<=")
                || trimmed.endsWith(">")
                || trimmed.endsWith("<")
                || trimmed.endsWith(">>")
                || trimmed.endsWith(">>>");
    }

    private static boolean startsWithExplicitContinuationToken(String trimmed) {
        return trimmed.startsWith(".")
                || trimmed.startsWith(",")
                || trimmed.startsWith("+")
                || trimmed.startsWith("-")
                || trimmed.startsWith("*")
                || trimmed.startsWith("/")
                || trimmed.startsWith("%")
                || trimmed.startsWith("&&")
                || trimmed.startsWith("||")
                || trimmed.startsWith("|")
                || trimmed.startsWith("&")
                || trimmed.startsWith("?")
                || trimmed.startsWith(":");
    }

    private static int leadingSpaceCount(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
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

    private static Set<Integer> collectEnumConstantStartLines(Node root) {
        Set<Integer> result = new HashSet<>();
        collectEnumConstantStartLinesRecursive(root, result);
        return result;
    }

    private static void collectEnumConstantStartLinesRecursive(Node node, Set<Integer> result) {
        if (node instanceof ASTEnumConstant) {
            result.add(declarationHeaderStartLine(node));
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            collectEnumConstantStartLinesRecursive(node.getChild(i), result);
        }
    }

    private static String trimmedCode(LineInfo info) {
        return stripTrailingLineComment(info.getText()).trim();
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
            baseLines.add(declarationHeaderStartLine(node));
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
            if (body != null) {
                int start = declarationHeaderStartLine(node);
                if (body.getBeginLine() > start) {
                    for (int line = start; line < body.getBeginLine(); line++) {
                        result.add(line);
                    }
                }
            }
        }

        if (isTypeDeclaration(node)) {
            int start = declarationHeaderStartLine(node);
            int bodyBeginLine = findTypeBodyBeginLine(node);
            if (bodyBeginLine > start) {
                for (int line = start; line < bodyBeginLine; line++) {
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

    private static int declarationHeaderStartLine(Node node) {
        int start = node.getBeginLine();
        return minBeginLineOutsideModifierList(node, start);
    }

    private static int minBeginLineOutsideModifierList(Node node, int currentMin) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            Node child = node.getChild(i);
            if (child instanceof ASTModifierList) {
                continue;
            }
            if (child.getBeginLine() > 0 && child.getBeginLine() < currentMin) {
                currentMin = child.getBeginLine();
            }
            currentMin = minBeginLineOutsideModifierList(child, currentMin);
        }
        return currentMin;
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
                || node instanceof ASTTryStatement
                || node instanceof ASTLabeledStatement;
    }

    private static String stripTrailingLineComment(String line) {
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < line.length() - 1; i++) {
            char current = line.charAt(i);
            char next = line.charAt(i + 1);

            if (escaped) {
                escaped = false;
                continue;
            }

            if ((inString || inChar) && current == '\\') {
                escaped = true;
                continue;
            }

            if (!inChar && current == '"') {
                inString = !inString;
                continue;
            }

            if (!inString && current == '\'') {
                inChar = !inChar;
                continue;
            }

            if (!inString && !inChar && current == '/' && next == '/') {
                return line.substring(0, i);
            }
        }

        return line;
    }

    private static int lastNonWhitespaceColumn1Based(String line) {
        for (int i = line.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean startsWithBlockDelimiter(String trimmedLine) {
        return trimmedLine.startsWith("{") || trimmedLine.startsWith("}");
    }

    private static final class AstLineFacts {

        private final List<GenericAngleNode> genericAngleNodes;
        private final Set<Integer> anonymousClassChainStartLines;

        private AstLineFacts(List<GenericAngleNode> genericAngleNodes,
                             Set<Integer> anonymousClassChainStartLines) {
            this.genericAngleNodes = genericAngleNodes;
            this.anonymousClassChainStartLines = anonymousClassChainStartLines;
        }

        static AstLineFacts from(Node root) {
            List<GenericAngleNode> genericAngleNodes = new ArrayList<>();
            collectGenericAngleNodes(root, genericAngleNodes);
            Set<Integer> anonymousClassChainStartLines = new HashSet<>();
            collectAnonymousClassChainStartLines(root, anonymousClassChainStartLines);
            return new AstLineFacts(genericAngleNodes, anonymousClassChainStartLines);
        }

        boolean startsChainAfterAnonymousClass(int lineNumber) {
            return anonymousClassChainStartLines.contains(lineNumber);
        }

        boolean trailingGreaterThanClosesGeneric(LineInfo line) {
            String code = stripTrailingLineComment(line.getText());
            int trailingGreaterColumn = lastNonWhitespaceColumn1Based(code);
            int closeRunLength = trailingGreaterThanRunLength(code);

            if (trailingGreaterColumn < 1 || closeRunLength == 0) {
                return false;
            }

            int lineNo = line.getLineNumber();

            for (GenericAngleNode generic : genericAngleNodes) {
                if (generic.closesAtTrailingGreaterThan(
                        lineNo,
                        trailingGreaterColumn,
                        closeRunLength,
                        code)) {
                    return true;
                }
            }

            return false;
        }

        boolean genericCloseContinuesIntoLine(LineInfo previousLine, LineInfo currentLine) {
            String previousCode = stripTrailingLineComment(previousLine.getText());
            int trailingGreaterColumn = lastNonWhitespaceColumn1Based(previousCode);
            int closeRunLength = trailingGreaterThanRunLength(previousCode);

            if (trailingGreaterColumn < 1 || closeRunLength == 0) {
                return false;
            }

            int previousLineNumber = previousLine.getLineNumber();
            int currentLineNumber = currentLine.getLineNumber();
            String currentTrimmed = stripTrailingLineComment(currentLine.getText()).trim();

            if (startsWithBlockDelimiter(currentTrimmed)) {
                return false;
            }

            if (leadingSpaceCount(currentLine.getText()) > leadingSpaceCount(previousLine.getText())) {
                return true;
            }

            for (GenericAngleNode generic : genericAngleNodes) {
                if (!generic.closesAtTrailingGreaterThan(
                        previousLineNumber,
                        trailingGreaterColumn,
                        closeRunLength,
                        previousCode)) {
                    continue;
                }

                if (genericEnclosingSyntaxSpansIntoLine(generic.node, currentLineNumber)) {
                    return true;
                }
            }

            return false;
        }

        private static boolean genericEnclosingSyntaxSpansIntoLine(Node genericNode,
                                                                   int currentLineNumber) {
            Node node = genericNode.getParent();

            while (node != null) {
                if (node.getBeginLine() <= genericNode.getBeginLine()
                        && node.getEndLine() >= currentLineNumber
                        && isContinuationOwner(node)) {
                    return true;
                }

                node = node.getParent();
            }

            return false;
        }

        private static boolean isContinuationOwner(Node node) {
            return isStatement(node)
                    || isTypeDeclaration(node)
                    || node instanceof ASTFieldDeclaration
                    || node instanceof ASTMethodDeclaration
                    || node instanceof ASTConstructorDeclaration
                    || node instanceof ASTEnumConstant
                    || node instanceof ASTAnnotation
                    || node instanceof ASTAssignmentExpression
                    || node instanceof ASTConditionalExpression
                    || node instanceof ASTInfixExpression
                    || node instanceof ASTUnaryExpression
                    || node instanceof ASTCastExpression
                    || node instanceof ASTMethodCall
                    || node instanceof ASTMethodReference
                    || node instanceof ASTConstructorCall
                    || node instanceof ASTExplicitConstructorInvocation
                    || node instanceof ASTClassType
                    || node instanceof ASTArrayType
                    || node instanceof ASTTypeExpression
                    || node instanceof ASTVariableDeclarator
                    || node instanceof ASTFormalParameter
                    || node instanceof ASTReceiverParameter
                    || node instanceof ASTRecordComponent
                    || node instanceof ASTExtendsList
                    || node instanceof ASTImplementsList
                    || node instanceof ASTPermitsList
                    || node instanceof ASTThrowsList;
        }

        private static void collectGenericAngleNodes(Node node, List<GenericAngleNode> out) {
            if (node instanceof ASTTypeArguments || node instanceof ASTTypeParameters) {
                if (hasUsefulLocation(node)) {
                    out.add(new GenericAngleNode(node));
                }
            }

            for (int i = 0; i < node.getNumChildren(); i++) {
                collectGenericAngleNodes(node.getChild(i), out);
            }
        }

        private static void collectAnonymousClassChainStartLines(Node node, Set<Integer> out) {
            if (node instanceof ASTAnonymousClassDeclaration) {
                ASTAnonymousClassDeclaration anonymousClass =
                        (ASTAnonymousClassDeclaration) node;
                var closingBrace = anonymousClass.getLastToken();
                var next = closingBrace.getNext();
                if (next != null && ".".equals(next.getImage())
                        && next.getReportLocation().getStartLine()
                        > closingBrace.getReportLocation().getStartLine()) {
                    out.add(next.getReportLocation().getStartLine());
                }
            }

            for (int i = 0; i < node.getNumChildren(); i++) {
                collectAnonymousClassChainStartLines(node.getChild(i), out);
            }
        }

        private static boolean hasUsefulLocation(Node node) {
            return node.getBeginLine() > 0
                    && node.getBeginColumn() > 0
                    && node.getEndLine() > 0
                    && node.getEndColumn() > 0;
        }
    }

    private static final class GenericAngleNode {

        private final Node node;
        private final int beginLine;
        private final int beginColumn;
        private final int endLine;
        private final int endColumn;

        private GenericAngleNode(Node node) {
            this.node = node;
            this.beginLine = node.getBeginLine();
            this.beginColumn = node.getBeginColumn();
            this.endLine = node.getEndLine();
            this.endColumn = node.getEndColumn();
        }

        private boolean closesAtTrailingGreaterThan(int line,
                                                    int trailingGreaterColumn,
                                                    int closeRunLength,
                                                    String codeLine) {
            if (endLine != line) {
                return false;
            }

            if (beginLine == line && beginColumn > trailingGreaterColumn) {
                return false;
            }

            int firstGreaterColumnInRun = trailingGreaterColumn - closeRunLength + 1;

            if (endColumn >= firstGreaterColumnInRun - 1
                    && endColumn <= trailingGreaterColumn + 1) {
                return true;
            }

            if (endColumn < firstGreaterColumnInRun) {
                return onlyWhitespaceBetweenColumns(
                        codeLine,
                        endColumn + 1,
                        firstGreaterColumnInRun - 1);
            }

            return false;
        }
    }

    private static int trailingGreaterThanRunLength(String line) {
        int i = line.length() - 1;

        while (i >= 0 && Character.isWhitespace(line.charAt(i))) {
            i--;
        }

        int count = 0;
        while (i >= 0 && line.charAt(i) == '>') {
            count++;
            i--;
        }

        return count;
    }

    private static boolean onlyWhitespaceBetweenColumns(String line,
                                                        int startColumnInclusive,
                                                        int endColumnInclusive) {
        if (endColumnInclusive < startColumnInclusive) {
            return true;
        }

        int startIndex = Math.max(0, startColumnInclusive - 1);
        int endIndex = Math.min(line.length() - 1, endColumnInclusive - 1);

        for (int i = startIndex; i <= endIndex; i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}
