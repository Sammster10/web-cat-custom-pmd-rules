package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A PMD rule that flags classes defining a covariant {@code equals} method
 * without also overriding {@link Object#equals(Object)}.
 *
 * <p>A covariant {@code equals} is any method named {@code equals} that
 * accepts a single parameter whose type is <em>not</em> {@code Object}.
 * Such a method does not override {@link Object#equals(Object)} and can
 * lead to subtle bugs with collections, assertions, and other code that
 * relies on the standard equals contract.
 *
 * <p>The rule only reports a violation when a class defines a covariant
 * {@code equals} <b>and</b> does not also provide a proper
 * {@code equals(Object)} override.
 */
public class CovariantEqualsRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE =
            PropertyFactory.stringProperty("violationMessage")
                    .desc("Message for a covariant equals without an equals(Object) override. Placeholders: {0}=class name, {1}=parameter type")
                    .defaultValue("Class ''{0}'' defines equals({1}) but does not override equals(Object).")
                    .build();

    public CovariantEqualsRule() {
        super(ASTClassDeclaration.class, ASTEnumDeclaration.class, ASTRecordDeclaration.class);
        definePropertyDescriptor(MESSAGE);
    }

    @Override
    public Object visit(ASTClassDeclaration node, Object data) {
        return checkType(node, data);
    }

    @Override
    public Object visit(ASTEnumDeclaration node, Object data) {
        return checkType(node, data);
    }

    @Override
    public Object visit(ASTRecordDeclaration node, Object data) {
        return checkType(node, data);
    }

    private Object checkType(ASTTypeDeclaration node, Object data) {
        RuleContext ctx = asCtx(data);

        ASTMethodDeclaration properEquals = null;
        ASTMethodDeclaration covariantEquals = null;
        String covariantParamType = null;

        for (ASTMethodDeclaration method : node.getDeclarations(ASTMethodDeclaration.class)) {
            if (!"equals".equals(method.getName())) {
                continue;
            }
            ASTFormalParameters params = method.getFormalParameters();
            if (params.size() != 1) {
                continue;
            }

            ASTFormalParameter param = params.toList().get(0);
            ASTType paramType = param.getTypeNode();
            String typeName = paramType.getText().toString();

            if ("Object".equals(typeName) || "java.lang.Object".equals(typeName)) {
                properEquals = method;
            } else if (covariantEquals == null) {
                covariantEquals = method;
                covariantParamType = typeName;
            }
        }

        if (covariantEquals != null && properEquals == null) {
            String message = java.text.MessageFormat.format(
                    getProperty(MESSAGE), node.getSimpleName(), covariantParamType);
            ctx.addViolationWithMessage(covariantEquals, message);
        }

        return data;
    }
}

