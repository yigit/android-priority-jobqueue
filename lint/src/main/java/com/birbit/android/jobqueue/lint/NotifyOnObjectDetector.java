package com.birbit.android.jobqueue.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodInvocation;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class NotifyOnObjectDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "NOTIFY_ON_OBJECT",
            "Code should not notify on objects directly",
            "Use Timer instead so that mock notifications can work as expected",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(NotifyOnObjectDetector.class, EnumSet.of(Scope.JAVA_FILE))
    );

    private static final Set<String> BAD_METHODS = new HashSet<String>();
    static  {
        BAD_METHODS.add("notify");
        BAD_METHODS.add("notifyAll");
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
        return new ForwardingAstVisitor() {

            @Override
            public boolean visitMethodInvocation(MethodInvocation node) {
                Expression operand = node.astOperand();
                String methodName = node.astName().toString();
                if (BAD_METHODS.contains(methodName) && !context.isSuppressedWithComment(node, ISSUE)) {
                    context.report(ISSUE, context.getLocation(node), "Don't call " + methodName + " directly. Use" +
                            " Timer instead.");
                }
                return super.visitMethodInvocation(node);
            }
        };
    }
}

