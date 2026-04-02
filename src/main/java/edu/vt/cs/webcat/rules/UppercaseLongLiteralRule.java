package edu.vt.cs.webcat.rules;

import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.java.ast.ASTNumericLiteral;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

public class UppercaseLongLiteralRule extends AbstractJavaRulechainRule {

    private static final PropertyDescriptor<String> MESSAGE_PROPERTY =
            PropertyFactory.stringProperty("message")
                    .desc("Custom message to display when the rule is violated")
                    .defaultValue("")
                    .build();


    public UppercaseLongLiteralRule() {
        super(ASTNumericLiteral.class);
        definePropertyDescriptor(MESSAGE_PROPERTY);
    }

    @Override
    public Object visit(ASTNumericLiteral node, Object data) {

        String customMessage = getProperty(MESSAGE_PROPERTY);


        if (node.isLongLiteral()) {
            Chars literal = node.getLiteralText();
            int len = literal.length();

            if (len > 0 && literal.charAt(len - 1) == 'l') {
                RuleContext ctx = asCtx(data);
                ctx.addViolationWithMessage(
                        node,
                        customMessage.isEmpty()
                                ? "Long literals should end with 'L' instead of 'l' to avoid confusion with the digit '1'."
                                : customMessage
                );
            }
        }

        return data;
    }
}