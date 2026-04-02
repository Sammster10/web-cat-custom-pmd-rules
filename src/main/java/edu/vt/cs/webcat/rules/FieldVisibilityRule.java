package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.JModifier;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A PMD rule that enforces visibility modifiers on fields and restricts
 * public fields unless they are both {@code static} and {@code final}.
 *
 * <h3>Checks performed</h3>
 * <ul>
 *   <li><b>Missing visibility modifier</b> &ndash; flags any field that
 *       has package-private (default) access, i.e. no explicit
 *       {@code public}, {@code protected}, or {@code private} keyword.</li>
 *   <li><b>Public non-constant field</b> &ndash; flags any {@code public}
 *       field that is not also {@code static final}.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code allowProtected} &ndash; when {@code true}, protected
 *       fields are allowed; when {@code false}, they are flagged as well.
 *       Defaults to {@code true}.</li>
 *   <li>{@code missingModifierMessage} &ndash; custom violation message
 *       for fields missing a visibility modifier.</li>
 *   <li>{@code publicFieldMessage} &ndash; custom violation message for
 *       public non-constant fields.</li>
 *   <li>{@code protectedFieldMessage} &ndash; custom violation message
 *       for protected fields (only used when {@code allowProtected}
 *       is {@code false}).</li>
 * </ul>
 */
public class FieldVisibilityRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<Boolean> ALLOW_PROTECTED =
            PropertyFactory.booleanProperty("allowProtected")
                    .desc("Whether to allow protected fields")
                    .defaultValue(true)
                    .build();

    private static final PropertyDescriptor<String> MISSING_MODIFIER_MESSAGE =
            PropertyFactory.stringProperty("missingModifierMessage")
                    .desc("Message for a field missing a visibility modifier. Placeholders: {0}=name")
                    .defaultValue("Field ''{0}'' is missing a visibility modifier.")
                    .build();

    private static final PropertyDescriptor<String> PUBLIC_FIELD_MESSAGE =
            PropertyFactory.stringProperty("publicFieldMessage")
                    .desc("Message for a public non-constant field. Placeholders: {0}=name")
                    .defaultValue("Field ''{0}'' is public but not static final. Only public static final fields are allowed.")
                    .build();

    private static final PropertyDescriptor<String> PROTECTED_FIELD_MESSAGE =
            PropertyFactory.stringProperty("protectedFieldMessage")
                    .desc("Message for a protected field when allowProtected is false. Placeholders: {0}=name")
                    .defaultValue("Field ''{0}'' should be private instead of protected.")
                    .build();

    public FieldVisibilityRule() {
        super(ASTFieldDeclaration.class);
        definePropertyDescriptor(ALLOW_PROTECTED);
        definePropertyDescriptor(MISSING_MODIFIER_MESSAGE);
        definePropertyDescriptor(PUBLIC_FIELD_MESSAGE);
        definePropertyDescriptor(PROTECTED_FIELD_MESSAGE);
    }

    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {
        RuleContext ctx = asCtx(data);

        boolean isPublic = node.hasModifiers(JModifier.PUBLIC);
        boolean isProtected = node.hasModifiers(JModifier.PROTECTED);
        boolean isPrivate = node.hasModifiers(JModifier.PRIVATE);
        boolean isStatic = node.hasModifiers(JModifier.STATIC);
        boolean isFinal = node.hasModifiers(JModifier.FINAL);

        boolean hasExplicitVisibility = isPublic || isProtected || isPrivate;

        for (ASTVariableId varId : node.getVarIds()) {
            String name = varId.getName();

            if (!hasExplicitVisibility) {
                String message = java.text.MessageFormat.format(
                        getProperty(MISSING_MODIFIER_MESSAGE), name);
                ctx.addViolationWithMessage(varId, message);
            } else if (isPublic && !(isStatic && isFinal)) {
                String message = java.text.MessageFormat.format(
                        getProperty(PUBLIC_FIELD_MESSAGE), name);
                ctx.addViolationWithMessage(varId, message);
            } else if (isProtected && !getProperty(ALLOW_PROTECTED)) {
                String message = java.text.MessageFormat.format(
                        getProperty(PROTECTED_FIELD_MESSAGE), name);
                ctx.addViolationWithMessage(varId, message);
            }
        }

        return data;
    }
}

