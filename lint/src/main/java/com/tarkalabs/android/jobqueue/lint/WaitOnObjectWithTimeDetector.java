package com.birbit.android.jobqueue.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodInvocation;

import java.util.EnumSet;

/**
 * Detects sleep calls in tests.
 */
public class WaitOnObjectWithTimeDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "TIMED_WAIT",
            "Code should not use object locked waits.",
            "Code should not use timed waits directly. Instead, used the timer's utility method.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WaitOnObjectWithTimeDetector.class, EnumSet.of(Scope.JAVA_FILE))
    );

    @Override
    public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
        return new ForwardingAstVisitor() {
            @Override
            public boolean visitMethodInvocation(MethodInvocation node) {
                Expression operand = node.astOperand();
                if (node.astName().toString().equals("wait")
                        && !context.isSuppressedWithComment(node, ISSUE)) {
                    context.report(ISSUE, context.getLocation(node), "Don't wait on object. Use Timer's wait instead.");
                }
                return super.visitMethodInvocation(node);
            }
        };
    }
}
