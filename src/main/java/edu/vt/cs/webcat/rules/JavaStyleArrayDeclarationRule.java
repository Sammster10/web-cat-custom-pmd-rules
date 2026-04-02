package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A PMD rule that enforces Java-style array declarations over C-style declarations.
 *
 * <p>Java-style places the brackets on the type ({@code int[] x}), while C-style
 * places them on the variable name ({@code int x[]}). This rule flags C-style
 * declarations in both variable declarations and method return types.
 *
 * <h3>Variables and parameters</h3>
 * <p>Flags any variable, field, or parameter declaration where the array brackets
 * appear after the identifier (e.g. {@code int x[]}, {@code String args[]}).
 *
 * <h3>Method return types</h3>
 * <p>Flags any method declaration where array brackets appear after the formal
 * parameter list (e.g. {@code int getValues()[]}).
 */
public class JavaStyleArrayDeclarationRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> VARIABLE_MESSAGE =
            PropertyFactory.stringProperty("variableMessage")
                    .desc("Message for C-style array on a variable. Placeholders: {0}=variable name")
                    .defaultValue("Array brackets should be on the type, not the variable: {0}")
                    .build();

    private static final PropertyDescriptor<String> METHOD_MESSAGE =
            PropertyFactory.stringProperty("methodMessage")
                    .desc("Message for C-style array on a method return type. Placeholders: {0}=method name")
                    .defaultValue("Array brackets should be on the return type, not after the parameter list: {0}")
                    .build();

    public JavaStyleArrayDeclarationRule() {
        super(ASTVariableId.class, ASTMethodDeclaration.class);
        definePropertyDescriptor(VARIABLE_MESSAGE);
        definePropertyDescriptor(METHOD_MESSAGE);
    }

    @Override
    public Object visit(ASTVariableId node, Object data) {
        if (node.getExtraDimensions() != null) {
            RuleContext ctx = asCtx(data);
            String message = java.text.MessageFormat.format(
                    getProperty(VARIABLE_MESSAGE), node.getName());
            ctx.addViolationWithMessage(node, message);
        }
        return data;
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        if (node.getExtraDimensions() != null) {
            RuleContext ctx = asCtx(data);
            String message = java.text.MessageFormat.format(
                    getProperty(METHOD_MESSAGE), node.getName());
            ctx.addViolationWithMessage(node, message);
        }
        return data;
    }
}
