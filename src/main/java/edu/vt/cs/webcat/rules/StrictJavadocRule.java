package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JFormalParamSymbol;
import net.sourceforge.pmd.lang.java.types.JTypeVar;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * A PMD rule that enforces strict Javadoc documentation standards on all non-private
 * Java declarations. Uses the rule chain mechanism to visit declarations individually.
 *
 * <h3>Type declarations (classes, enums, interfaces, annotations, records)</h3>
 * <ul>
 *   <li>Must have a Javadoc comment</li>
 *   <li>Must declare exactly one non-empty {@code @author} tag</li>
 *   <li>Must declare exactly one non-empty {@code @version} tag</li>
 *   <li>Must declare {@code @param} for each generic type parameter (e.g. {@code @param <T>})</li>
 *   <li>Must not contain {@code @return}, {@code @throws}, or {@code @exception} tags</li>
 *   <li>Duplicate or unknown type parameter {@code @param} tags are reported</li>
 * </ul>
 *
 * <h3>Methods and constructors (including compact record constructors)</h3>
 * <ul>
 *   <li>Must have a Javadoc comment</li>
 *   <li>Must declare {@code @param} for every formal parameter</li>
 *   <li>Must declare {@code @param} for every generic type parameter</li>
 *   <li>Non-void methods must declare exactly one {@code @return} tag</li>
 *   <li>Void methods and constructors must not declare {@code @return}</li>
 *   <li>Duplicate, unknown, or malformed {@code @param} tags are reported</li>
 *   <li>Duplicate {@code @return} tags are reported</li>
 * </ul>
 *
 * <h3>Override methods</h3>
 * <p>If a method overrides a superclass method or implements an interface method
 * (detected via {@code @Override} annotation),
 * Javadoc is not required. If the only Javadoc content is {@code {@inheritDoc}},
 * the method is also skipped. However, if a Javadoc comment is present with
 * content beyond {@code {@inheritDoc}}, all normal validation rules apply.</p>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>Non-private fields must have a Javadoc comment</li>
 * </ul>
 *
 * <h3>Visibility</h3>
 * Only declarations with package-private, protected, or public visibility are checked.
 * Private declarations are ignored entirely.
 *
 * <h3>Configurable messages</h3>
 * Every violation message is exposed as a configurable {@code String} property using
 * {@link java.lang.String#format(String, Object...)} placeholders. Override any property
 * in your ruleset XML to customize the reported message.
 */
public class StrictJavadocRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MISSING_JAVADOC_MESSAGE =
            PropertyFactory.stringProperty("missingJavadocMessage")
                    .desc("Message when Javadoc is missing. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("{0} {1} must have Javadoc.")
                    .build();

    private static final PropertyDescriptor<String> MALFORMED_PARAM_MESSAGE =
            PropertyFactory.stringProperty("malformedParamMessage")
                    .desc("Message for malformed @param tag. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has a malformed @param tag with no parameter name.")
                    .build();

    private static final PropertyDescriptor<String> DUPLICATE_PARAM_MESSAGE =
            PropertyFactory.stringProperty("duplicateParamMessage")
                    .desc("Message for duplicate @param tag. Placeholders: {0}=kind, {1}=name, {2}=param")
                    .defaultValue("Javadoc for {0} {1} has duplicate @param for parameter {2}.")
                    .build();

    private static final PropertyDescriptor<String> UNKNOWN_PARAM_MESSAGE =
            PropertyFactory.stringProperty("unknownParamMessage")
                    .desc("Message for unknown @param tag. Placeholders: {0}=kind, {1}=name, {2}=param")
                    .defaultValue("Javadoc for {0} {1} has @param for unknown parameter {2}.")
                    .build();

    private static final PropertyDescriptor<String> DUPLICATE_TYPE_PARAM_MESSAGE =
            PropertyFactory.stringProperty("duplicateTypeParamMessage")
                    .desc("Message for duplicate type @param. Placeholders: {0}=kind, {1}=name, {2}=type param")
                    .defaultValue("Javadoc for {0} {1} has duplicate @param for type parameter {2}.")
                    .build();

    private static final PropertyDescriptor<String> UNKNOWN_TYPE_PARAM_MESSAGE =
            PropertyFactory.stringProperty("unknownTypeParamMessage")
                    .desc("Message for unknown type @param. Placeholders: {0}=kind, {1}=name, {2}=type param")
                    .defaultValue("Javadoc for {0} {1} has @param for unknown type parameter {2}.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_PARAM_MESSAGE =
            PropertyFactory.stringProperty("missingParamMessage")
                    .desc("Message for missing @param. Placeholders: {0}=kind, {1}=name, {2}=param")
                    .defaultValue("Javadoc for {0} {1} is missing @param for {2}.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_TYPE_PARAM_MESSAGE =
            PropertyFactory.stringProperty("missingTypeParamMessage")
                    .desc("Message for missing type @param. Placeholders: {0}=kind, {1}=name, {2}=type param")
                    .defaultValue("Javadoc for {0} {1} is missing @param for type parameter {2}.")
                    .build();

    private static final PropertyDescriptor<String> DUPLICATE_RETURN_MESSAGE =
            PropertyFactory.stringProperty("duplicateReturnMessage")
                    .desc("Message for duplicate @return. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has duplicate @return tags.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_RETURN_MESSAGE =
            PropertyFactory.stringProperty("missingReturnMessage")
                    .desc("Message for missing @return. Placeholders: {0}=name")
                    .defaultValue("Javadoc for non-void method {0} must declare @return.")
                    .build();

    private static final PropertyDescriptor<String> FORBIDDEN_RETURN_MESSAGE =
            PropertyFactory.stringProperty("forbiddenReturnMessage")
                    .desc("Message for forbidden @return. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} must not declare @return.")
                    .build();

    private static final PropertyDescriptor<String> MALFORMED_TYPE_PARAM_MESSAGE =
            PropertyFactory.stringProperty("malformedTypeParamMessage")
                    .desc("Message for malformed type @param. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has a malformed @param tag with no target.")
                    .build();

    private static final PropertyDescriptor<String> INVALID_TYPE_PARAM_TARGET_MESSAGE =
            PropertyFactory.stringProperty("invalidTypeParamTargetMessage")
                    .desc("Message for invalid @param target on type. Placeholders: {0}=kind, {1}=name, {2}=target")
                    .defaultValue("Javadoc for {0} {1} has invalid @param target {2}.")
                    .build();

    private static final PropertyDescriptor<String> UNUSED_TAG_ON_TYPE_MESSAGE =
            PropertyFactory.stringProperty("unusedTagOnTypeMessage")
                    .desc("Message for unused tag on type. Placeholders: {0}=kind, {1}=name, {2}=tag name")
                    .defaultValue("Javadoc for {0} {1} contains unused @{2} tag.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_AUTHOR_MESSAGE =
            PropertyFactory.stringProperty("missingAuthorMessage")
                    .desc("Message for missing @author. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} must declare @author.")
                    .build();

    private static final PropertyDescriptor<String> DUPLICATE_AUTHOR_MESSAGE =
            PropertyFactory.stringProperty("duplicateAuthorMessage")
                    .desc("Message for duplicate @author. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has duplicate @author tags.")
                    .build();

    private static final PropertyDescriptor<String> EMPTY_AUTHOR_MESSAGE =
            PropertyFactory.stringProperty("emptyAuthorMessage")
                    .desc("Message for empty @author. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has an empty @author tag.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_VERSION_MESSAGE =
            PropertyFactory.stringProperty("missingVersionMessage")
                    .desc("Message for missing @version. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} must declare @version.")
                    .build();

    private static final PropertyDescriptor<String> DUPLICATE_VERSION_MESSAGE =
            PropertyFactory.stringProperty("duplicateVersionMessage")
                    .desc("Message for duplicate @version. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has duplicate @version tags.")
                    .build();

    private static final PropertyDescriptor<String> EMPTY_VERSION_MESSAGE =
            PropertyFactory.stringProperty("emptyVersionMessage")
                    .desc("Message for empty @version. Placeholders: {0}=kind, {1}=name")
                    .defaultValue("Javadoc for {0} {1} has an empty @version tag.")
                    .build();

    private static final PropertyDescriptor<String> MISSING_FIELD_JAVADOC_MESSAGE =
            PropertyFactory.stringProperty("missingFieldJavadocMessage")
                    .desc("Message when field Javadoc is missing. Placeholders: {0}=field names")
                    .defaultValue("Non-private field(s) {0} must have Javadoc.")
                    .build();

    private static final PropertyDescriptor<List<String>> ALLOWED_DUPLICATE_TAGS =
            PropertyFactory.stringListProperty("allowedDuplicateTags")
                    .desc("Tag names that are allowed to appear more than once (e.g. author, version, param, return).")
                    .defaultValue(Collections.emptyList())
                    .build();

    public StrictJavadocRule() {
        super(
                ASTMethodDeclaration.class,
                ASTConstructorDeclaration.class,
                ASTCompactConstructorDeclaration.class,
                ASTFieldDeclaration.class,
                ASTClassDeclaration.class,
                ASTEnumDeclaration.class,
                ASTAnnotationTypeDeclaration.class,
                ASTRecordDeclaration.class
        );

        definePropertyDescriptor(MISSING_JAVADOC_MESSAGE);
        definePropertyDescriptor(MALFORMED_PARAM_MESSAGE);
        definePropertyDescriptor(DUPLICATE_PARAM_MESSAGE);
        definePropertyDescriptor(UNKNOWN_PARAM_MESSAGE);
        definePropertyDescriptor(DUPLICATE_TYPE_PARAM_MESSAGE);
        definePropertyDescriptor(UNKNOWN_TYPE_PARAM_MESSAGE);
        definePropertyDescriptor(MISSING_PARAM_MESSAGE);
        definePropertyDescriptor(MISSING_TYPE_PARAM_MESSAGE);
        definePropertyDescriptor(DUPLICATE_RETURN_MESSAGE);
        definePropertyDescriptor(MISSING_RETURN_MESSAGE);
        definePropertyDescriptor(FORBIDDEN_RETURN_MESSAGE);
        definePropertyDescriptor(MALFORMED_TYPE_PARAM_MESSAGE);
        definePropertyDescriptor(INVALID_TYPE_PARAM_TARGET_MESSAGE);
        definePropertyDescriptor(UNUSED_TAG_ON_TYPE_MESSAGE);
        definePropertyDescriptor(MISSING_AUTHOR_MESSAGE);
        definePropertyDescriptor(DUPLICATE_AUTHOR_MESSAGE);
        definePropertyDescriptor(EMPTY_AUTHOR_MESSAGE);
        definePropertyDescriptor(MISSING_VERSION_MESSAGE);
        definePropertyDescriptor(DUPLICATE_VERSION_MESSAGE);
        definePropertyDescriptor(EMPTY_VERSION_MESSAGE);
        definePropertyDescriptor(MISSING_FIELD_JAVADOC_MESSAGE);
        definePropertyDescriptor(ALLOWED_DUPLICATE_TAGS);
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        JavadocComment comment = node.getJavadocComment();

        if (comment == null && node.isOverridden()) {
            return data;
        }

        if (comment != null && isInheritDocOnly(comment)) {
            return data;
        }

        validateExecutable(
                node,
                data,
                "method",
                node.getName(),
                getExecutableParameterNames(node),
                collectTypeParameterNames(node.getSymbol().getTypeParameters()),
                !node.isVoid()
        );
        return data;
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        validateExecutable(
                node,
                data,
                "constructor",
                node.getName(),
                getExecutableParameterNames(node),
                collectTypeParameterNames(node.getSymbol().getTypeParameters()),
                false
        );
        return data;
    }

    @Override
    public Object visit(ASTCompactConstructorDeclaration node, Object data) {
        validateCompactConstructor(node, data);
        return data;
    }

    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {
        if (!isPackageOrMoreVisible(node)) {
            return data;
        }

        if (node.getJavadocComment() == null) {
            addViolation(
                    data,
                    node,
                    getProperty(MISSING_FIELD_JAVADOC_MESSAGE),
                    quoted(formatFieldNames(node))
            );
        }

        return data;
    }

    @Override
    public Object visit(ASTClassDeclaration node, Object data) {
        validateType(node, data);
        return data;
    }

    @Override
    public Object visit(ASTEnumDeclaration node, Object data) {
        validateType(node, data);
        return data;
    }

    @Override
    public Object visit(ASTAnnotationTypeDeclaration node, Object data) {
        validateType(node, data);
        return data;
    }

    @Override
    public Object visit(ASTRecordDeclaration node, Object data) {
        validateType(node, data);
        return data;
    }

    private void validateCompactConstructor(ASTCompactConstructorDeclaration node, Object data) {
        if (!isPackageOrMoreVisible(node)) {
            return;
        }

        List<String> parameterNames = new ArrayList<>();
        for (JFormalParamSymbol parameter : node.getSymbol().getFormalParameters()) {
            parameterNames.add(parameter.getSimpleName());
        }

        validateExecutableLike(
                node,
                data,
                "constructor",
                node.getEnclosingType().getSimpleName(),
                parameterNames,
                collectTypeParameterNames(node.getSymbol().getTypeParameters()),
                false
        );
    }

    private void validateExecutable(
            ASTExecutableDeclaration node,
            Object data,
            String kind,
            String name,
            List<String> parameterNames,
            List<String> typeParameterNames,
            boolean requiresReturnTag
    ) {
        if (!isPackageOrMoreVisible(node)) {
            return;
        }

        validateExecutableLike(
                node,
                data,
                kind,
                name,
                parameterNames,
                typeParameterNames,
                requiresReturnTag
        );
    }

    private void validateExecutableLike(
            ModifierOwner node,
            Object data,
            String kind,
            String name,
            List<String> parameterNames,
            List<String> typeParameterNames,
            boolean requiresReturnTag
    ) {
        JavadocComment comment = ((JavadocCommentOwner) node).getJavadocComment();
        if (comment == null) {
            addViolation(
                    data,
                    node,
                    getProperty(MISSING_JAVADOC_MESSAGE),
                    capitalize(kind),
                    quoted(name)
            );
            return;
        }

        List<JavadocTag> tags = parseJavadoc(comment);

        Set<String> actualParameters = new HashSet<>(parameterNames);
        Set<String> actualTypeParameters = new HashSet<>(typeParameterNames);
        Set<String> seenParameters = new HashSet<>();
        Set<String> seenTypeParameters = new HashSet<>();
        int returnTagCount = 0;

        for (JavadocTag tag : tags) {
            if ("param".equals(tag.getName())) {
                validateParamTag(node, data, kind, name, tag,
                        actualParameters, seenParameters,
                        actualTypeParameters, seenTypeParameters);
            } else if ("return".equals(tag.getName())) {
                returnTagCount++;
            }
        }

        for (String parameterName : parameterNames) {
            if (!seenParameters.contains(parameterName)) {
                addViolation(
                        data,
                        node,
                        getProperty(MISSING_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(parameterName)
                );
            }
        }

        for (String typeParameterName : typeParameterNames) {
            if (!seenTypeParameters.contains(typeParameterName)) {
                addViolation(
                        data,
                        node,
                        getProperty(MISSING_TYPE_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(typeParameterName)
                );
            }
        }

        validateReturnTag(node, data, kind, name, returnTagCount, requiresReturnTag);
    }

    private void validateParamTag(
            ModifierOwner node,
            Object data,
            String kind,
            String name,
            JavadocTag tag,
            Set<String> actualParameters,
            Set<String> seenParameters,
            Set<String> actualTypeParameters,
            Set<String> seenTypeParameters
    ) {
        if (tag.getSubject() == null || tag.getSubject().isBlank()) {
            addViolation(
                    data,
                    node,
                    getProperty(MALFORMED_PARAM_MESSAGE),
                    kind,
                    quoted(name)
            );
            return;
        }

        if (isTypeParameterTag(tag.getSubject())) {
            if (!seenTypeParameters.add(tag.getSubject()) && !isDuplicateAllowed("param")) {
                addViolation(
                        data,
                        node,
                        getProperty(DUPLICATE_TYPE_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(tag.getSubject())
                );
            } else if (!actualTypeParameters.contains(tag.getSubject())) {
                addViolation(
                        data,
                        node,
                        getProperty(UNKNOWN_TYPE_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(tag.getSubject())
                );
            }
        } else {
            if (!seenParameters.add(tag.getSubject()) && !isDuplicateAllowed("param")) {
                addViolation(
                        data,
                        node,
                        getProperty(DUPLICATE_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(tag.getSubject())
                );
            } else if (!actualParameters.contains(tag.getSubject())) {
                addViolation(
                        data,
                        node,
                        getProperty(UNKNOWN_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(tag.getSubject())
                );
            }
        }
    }

    private void validateReturnTag(
            ModifierOwner node,
            Object data,
            String kind,
            String name,
            int returnTagCount,
            boolean requiresReturnTag
    ) {
        if (returnTagCount > 1 && !isDuplicateAllowed("return")) {
            addViolation(
                    data,
                    node,
                    getProperty(DUPLICATE_RETURN_MESSAGE),
                    kind,
                    quoted(name)
            );
        }

        if (requiresReturnTag) {
            if (returnTagCount == 0) {
                addViolation(
                        data,
                        node,
                        getProperty(MISSING_RETURN_MESSAGE),
                        quoted(name)
                );
            }
        } else if (returnTagCount > 0) {
            addViolation(
                    data,
                    node,
                    getProperty(FORBIDDEN_RETURN_MESSAGE),
                    kind,
                    quoted(name)
            );
        }
    }

    private void validateType(ASTTypeDeclaration node, Object data) {
        if (!isPackageOrMoreVisible(node)) {
            return;
        }

        JavadocComment comment = node.getJavadocComment();
        String kind = resolveTypeKind(node);
        String name = node.getSimpleName();

        if (comment == null) {
            addViolation(
                    data,
                    node,
                    getProperty(MISSING_JAVADOC_MESSAGE),
                    capitalize(kind),
                    quoted(name)
            );
            return;
        }

        List<JavadocTag> tags = parseJavadoc(comment);
        List<String> typeParameterNames = collectTypeParameterNames(node.getSymbol().getTypeParameters());
        Set<String> actualTypeParameters = new HashSet<>(typeParameterNames);
        Set<String> seenTypeParameters = new HashSet<>();

        int authorCount = 0;
        boolean hasNonEmptyAuthor = false;

        int versionCount = 0;
        boolean hasNonEmptyVersion = false;

        for (JavadocTag tag : tags) {
            switch (tag.getName()) {
                case "author": {
                    authorCount++;
                    if (tag.getText() != null && !tag.getText().isBlank()) {
                        hasNonEmptyAuthor = true;
                    }
                    break;
                }
                case "version": {
                    versionCount++;
                    if (tag.getText() != null && !tag.getText().isBlank()) {
                        hasNonEmptyVersion = true;
                    }
                    break;
                }
                case "param": {
                    validateTypeParamTag(
                            node, data, kind, name, tag, actualTypeParameters, seenTypeParameters
                    );
                    break;
                }
                case "return":
                case "throws":
                case "exception": {
                    addViolation(
                            data,
                            node,
                            getProperty(UNUSED_TAG_ON_TYPE_MESSAGE),
                            kind,
                            quoted(name),
                            tag.getName()
                    );
                    break;
                }
            }
        }

        for (String typeParameterName : typeParameterNames) {
            if (!seenTypeParameters.contains(typeParameterName)) {
                addViolation(
                        data,
                        node,
                        getProperty(MISSING_TYPE_PARAM_MESSAGE),
                        kind,
                        quoted(name),
                        quoted(typeParameterName)
                );
            }
        }

        validateAuthorTag(node, data, kind, name, authorCount, hasNonEmptyAuthor);
        validateVersionTag(node, data, kind, name, versionCount, hasNonEmptyVersion);
    }

    private void validateTypeParamTag(
            ASTTypeDeclaration node,
            Object data,
            String kind,
            String name,
            JavadocTag tag,
            Set<String> actualTypeParameters,
            Set<String> seenTypeParameters
    ) {
        if (tag.getSubject() == null || tag.getSubject().isBlank()) {
            addViolation(
                    data,
                    node,
                    getProperty(MALFORMED_TYPE_PARAM_MESSAGE),
                    kind,
                    quoted(name)
            );
        } else if (!isTypeParameterTag(tag.getSubject())) {
            addViolation(
                    data,
                    node,
                    getProperty(INVALID_TYPE_PARAM_TARGET_MESSAGE),
                    kind,
                    quoted(name),
                    quoted(tag.getSubject())
            );
        } else if (!seenTypeParameters.add(tag.getSubject()) && !isDuplicateAllowed("param")) {
            addViolation(
                    data,
                    node,
                    getProperty(DUPLICATE_TYPE_PARAM_MESSAGE),
                    kind,
                    quoted(name),
                    quoted(tag.getSubject())
            );
        } else if (!actualTypeParameters.contains(tag.getSubject())) {
            addViolation(
                    data,
                    node,
                    getProperty(UNKNOWN_TYPE_PARAM_MESSAGE),
                    kind,
                    quoted(name),
                    quoted(tag.getSubject())
            );
        }
    }

    private void validateAuthorTag(
            ASTTypeDeclaration node,
            Object data,
            String kind,
            String name,
            int authorCount,
            boolean hasNonEmptyAuthor
    ) {
        if (authorCount == 0) {
            addViolation(
                    data,
                    node,
                    getProperty(MISSING_AUTHOR_MESSAGE),
                    kind,
                    quoted(name)
            );
        } else {
            if (authorCount > 1 && !isDuplicateAllowed("author")) {
                addViolation(
                        data,
                        node,
                        getProperty(DUPLICATE_AUTHOR_MESSAGE),
                        kind,
                        quoted(name)
                );
            }
            if (!hasNonEmptyAuthor) {
                addViolation(
                        data,
                        node,
                        getProperty(EMPTY_AUTHOR_MESSAGE),
                        kind,
                        quoted(name)
                );
            }
        }
    }

    private void validateVersionTag(
            ASTTypeDeclaration node,
            Object data,
            String kind,
            String name,
            int versionCount,
            boolean hasNonEmptyVersion
    ) {
        if (versionCount == 0) {
            addViolation(
                    data,
                    node,
                    getProperty(MISSING_VERSION_MESSAGE),
                    kind,
                    quoted(name)
            );
        } else {
            if (versionCount > 1 && !isDuplicateAllowed("version")) {
                addViolation(
                        data,
                        node,
                        getProperty(DUPLICATE_VERSION_MESSAGE),
                        kind,
                        quoted(name)
                );
            }
            if (!hasNonEmptyVersion) {
                addViolation(
                        data,
                        node,
                        getProperty(EMPTY_VERSION_MESSAGE),
                        kind,
                        quoted(name)
                );
            }
        }
    }

    private boolean isPackageOrMoreVisible(ModifierOwner node) {
        ModifierOwner.Visibility visibility = node.getEffectiveVisibility();
        return visibility == ModifierOwner.Visibility.V_PUBLIC
                || visibility == ModifierOwner.Visibility.V_PROTECTED
                || visibility == ModifierOwner.Visibility.V_PACKAGE;
    }

    private List<String> getExecutableParameterNames(ASTExecutableDeclaration node) {
        List<String> names = new ArrayList<>();
        ASTFormalParameters formalParameters = node.getFormalParameters();

        for (int index = 0; index < formalParameters.getNumChildren(); index++) {
            Object child = formalParameters.getChild(index);
            if (child instanceof ASTFormalParameter) {
                ASTFormalParameter parameter = (ASTFormalParameter) child;
                names.add(parameter.getVarId().getName());
            }
        }

        return names;
    }

    private List<String> collectTypeParameterNames(List<JTypeVar> typeVariables) {
        List<String> names = new ArrayList<>();
        for (JTypeVar typeVariable : typeVariables) {
            names.add(String.format("<%s>", typeVariable.getName()));
        }
        return names;
    }

    private String formatFieldNames(ASTFieldDeclaration node) {
        StringBuilder builder = new StringBuilder();
        for (ASTVariableId variableId : node) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(variableId.getName());
        }
        return builder.toString();
    }

    private String resolveTypeKind(ASTTypeDeclaration node) {
        if (node.isAnnotation()) {
            return "annotation";
        } else if (node.isEnum()) {
            return "enum";
        } else if (node.isRecord()) {
            return "record";
        } else if (node.isRegularInterface()) {
            return "interface";
        } else {
            return "class";
        }
    }

    private List<JavadocTag> parseJavadoc(JavadocComment comment) {
        List<JavadocTag> tags = new ArrayList<>();
        String rawText = comment.getText().toString();

        BufferedReader reader = new BufferedReader(new StringReader(rawText));
        TagAccumulator currentTag = null;
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                String cleaned = cleanJavadocLine(line);
                if (cleaned.isEmpty()) {
                    continue;
                }

                if (cleaned.charAt(0) == '@') {
                    if (currentTag != null) {
                        tags.add(currentTag.build());
                    }
                    currentTag = parseTagLine(cleaned);
                } else if (currentTag != null) {
                    currentTag.appendText(cleaned);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected failure while reading Javadoc text", exception);
        }

        if (currentTag != null) {
            tags.add(currentTag.build());
        }

        return tags;
    }

    private TagAccumulator parseTagLine(String line) {
        int index = 1;
        while (index < line.length() && Character.isLetterOrDigit(line.charAt(index))) {
            index++;
        }

        String tagName = line.substring(1, index);
        String remainder = line.substring(index).stripLeading();

        String subject = null;
        String text = remainder;

        if ("param".equals(tagName) || "throws".equals(tagName) || "exception".equals(tagName)) {
            int whitespaceIndex = findFirstWhitespaceIndex(remainder);
            if (whitespaceIndex < 0) {
                subject = remainder;
                text = "";
            } else {
                subject = remainder.substring(0, whitespaceIndex);
                text = remainder.substring(whitespaceIndex).stripLeading();
            }
        }

        return new TagAccumulator(tagName, subject, text);
    }

    private String cleanJavadocLine(String line) {
        String stripped = line.stripLeading();

        if (stripped.startsWith("/**")) {
            stripped = stripped.substring(3);
        } else if (stripped.startsWith("/*")) {
            stripped = stripped.substring(2);
        }

        stripped = stripped.stripLeading();

        if (stripped.startsWith("*/")) {
            return "";
        }

        if (stripped.startsWith("*")) {
            stripped = stripped.substring(1).stripLeading();
        }

        if (stripped.endsWith("*/")) {
            stripped = stripped.substring(0, stripped.length() - 2).stripTrailing();
        }

        return stripped;
    }

    private int findFirstWhitespaceIndex(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isTypeParameterTag(String subject) {
        return subject != null
                && subject.length() >= 3
                && subject.charAt(0) == '<'
                && subject.charAt(subject.length() - 1) == '>';
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String quoted(String text) {
        return String.format("'%s'", text);
    }

    private boolean isDuplicateAllowed(String tagName) {
        return getProperty(ALLOWED_DUPLICATE_TAGS).contains(tagName);
    }


    private boolean isInheritDocOnly(JavadocComment comment) {
        Iterable<Chars> lines = comment.getFilteredLines();
        boolean isOnlyInheritDoc = false;
        for (Chars line : lines) {
            String trimmed = line.toString().trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("{@inheritDoc}".equals(trimmed)) {
                isOnlyInheritDoc = true;
            } else {
                return false;
            }
        }

        return isOnlyInheritDoc;
    }

    private void addViolation(Object data, Object node, String message, Object... args) {
        String formatted = java.text.MessageFormat.format(message, args);
        asCtx(data).addViolationWithMessage((net.sourceforge.pmd.lang.ast.Node) node, formatted);
    }

    private static class JavadocTag {
        private final String name;
        private final String subject;
        private final String text;

        private JavadocTag(String name, String subject, String text) {
            this.name = name;
            this.subject = subject;
            this.text = text;
        }

        public String getName() {
            return name;
        }

        public String getSubject() {
            return subject;
        }

        public String getText() {
            return text;
        }
    }

    private static final class TagAccumulator {
        private final String name;
        private final String subject;
        private final StringBuilder text;

        private TagAccumulator(String name, String subject, String initialText) {
            this.name = name;
            this.subject = subject;
            this.text = new StringBuilder();
            if (initialText != null && !initialText.isEmpty()) {
                this.text.append(initialText);
            }
        }

        private void appendText(String additionalText) {
            if (additionalText == null || additionalText.isEmpty()) {
                return;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(additionalText);
        }

        private JavadocTag build() {
            return new JavadocTag(name, subject, text.toString());
        }
    }
}

