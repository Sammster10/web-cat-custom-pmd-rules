package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.ast.NodeStream;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.HashSet;
import java.util.Set;

/**
 * A PMD rule that prevents parameters and local variables from sharing
 * the same name as a field declared in the enclosing class.
 *
 * <p>Shadowing a field with a local variable or parameter is a common
 * source of confusion and bugs. This rule flags any parameter or local
 * variable whose name matches a field in the immediately enclosing type.
 *
 * <h3>Exclusions (configurable)</h3>
 * <ul>
 *   <li><b>Constructors</b> &ndash; excluded by default because
 *       constructor parameters commonly mirror field names.</li>
 *   <li><b>Setter methods</b> &ndash; excluded by default; a method
 *       is considered a setter if its name starts with {@code set},
 *       is longer than three characters, has exactly one formal
 *       parameter, and returns {@code void}.</li>
 *   <li><b>Abstract methods</b> &ndash; excluded by default since
 *       they have no body and parameters are purely declarative.</li>
 * </ul>
 */
public class HiddenFieldRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<Boolean> CHECK_CONSTRUCTORS =
            PropertyFactory.booleanProperty("checkConstructors")
                    .desc("Whether to check constructor parameters for field shadowing")
                    .defaultValue(false)
                    .build();

    private static final PropertyDescriptor<Boolean> CHECK_SETTERS =
            PropertyFactory.booleanProperty("checkSetters")
                    .desc("Whether to check setter method parameters for field shadowing")
                    .defaultValue(false)
                    .build();

    private static final PropertyDescriptor<Boolean> CHECK_ABSTRACT_METHODS =
            PropertyFactory.booleanProperty("checkAbstractMethods")
                    .desc("Whether to check abstract method parameters for field shadowing")
                    .defaultValue(false)
                    .build();

    private static final PropertyDescriptor<String> PARAMETER_MESSAGE =
            PropertyFactory.stringProperty("parameterMessage")
                    .desc("Message for a parameter that shadows a field. Placeholders: {0}=name")
                    .defaultValue("Parameter ''{0}'' has the same name as a field in the enclosing class.")
                    .build();

    private static final PropertyDescriptor<String> LOCAL_VARIABLE_MESSAGE =
            PropertyFactory.stringProperty("localVariableMessage")
                    .desc("Message for a local variable that shadows a field. Placeholders: {0}=name")
                    .defaultValue("Local variable ''{0}'' has the same name as a field in the enclosing class.")
                    .build();

    public HiddenFieldRule() {
        super(ASTMethodDeclaration.class, ASTConstructorDeclaration.class);
        definePropertyDescriptor(CHECK_CONSTRUCTORS);
        definePropertyDescriptor(CHECK_SETTERS);
        definePropertyDescriptor(CHECK_ABSTRACT_METHODS);
        definePropertyDescriptor(PARAMETER_MESSAGE);
        definePropertyDescriptor(LOCAL_VARIABLE_MESSAGE);
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        if (!getProperty(CHECK_ABSTRACT_METHODS) && node.isAbstract()) {
            return data;
        }
        if (!getProperty(CHECK_SETTERS) && isSetter(node)) {
            return data;
        }
        checkExecutable(node, data);
        return data;
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        if (!getProperty(CHECK_CONSTRUCTORS)) {
            return data;
        }
        checkExecutable(node, data);
        return data;
    }

    private void checkExecutable(ASTExecutableDeclaration node, Object data) {
        ASTTypeDeclaration enclosingType = node.getEnclosingType();
        if (enclosingType == null) {
            return;
        }

        Set<String> fieldNames = collectFieldNames(enclosingType);
        if (fieldNames.isEmpty()) {
            return;
        }

        RuleContext ctx = asCtx(data);
        checkParameters(node, fieldNames, ctx);
        checkLocalVariables(node, fieldNames, ctx);
    }

    private Set<String> collectFieldNames(ASTTypeDeclaration type) {
        Set<String> names = new HashSet<>();
        NodeStream<ASTFieldDeclaration> fields =
                type.getDeclarations(ASTFieldDeclaration.class);
        for (ASTFieldDeclaration field : fields) {
            for (ASTVariableId varId : field.getVarIds()) {
                names.add(varId.getName());
            }
        }
        return names;
    }

    private void checkParameters(ASTExecutableDeclaration node,
                                 Set<String> fieldNames,
                                 RuleContext ctx) {
        for (ASTFormalParameter param : node.getFormalParameters()) {
            ASTVariableId varId = param.getVarId();
            if (fieldNames.contains(varId.getName())) {
                String message = java.text.MessageFormat.format(
                        getProperty(PARAMETER_MESSAGE), varId.getName());
                ctx.addViolationWithMessage(varId, message);
            }
        }
    }

    private void checkLocalVariables(ASTExecutableDeclaration node,
                                     Set<String> fieldNames,
                                     RuleContext ctx) {
        if (node.getBody() == null) {
            return;
        }
        for (ASTLocalVariableDeclaration localVar :
                node.getBody().descendants(ASTLocalVariableDeclaration.class)) {
            for (ASTVariableId varId : localVar.getVarIds()) {
                if (fieldNames.contains(varId.getName())) {
                    String message = java.text.MessageFormat.format(
                            getProperty(LOCAL_VARIABLE_MESSAGE), varId.getName());
                    ctx.addViolationWithMessage(varId, message);
                }
            }
        }
    }

    private boolean isSetter(ASTMethodDeclaration method) {
        String name = method.getName();
        return name.length() > 3
                && name.startsWith("set")
                && Character.isUpperCase(name.charAt(3))
                && method.getArity() == 1
                && method.isVoid();
    }
}

