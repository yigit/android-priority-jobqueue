package com.birbit.android.jobqueue.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;

import java.util.EnumSet;

/**
 * Detects sleep calls in tests.
 */
public class SleepDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "SLEEP_IN_CODE",
            "Tests should not use sleep.",
            "Tests should not use sleep. Instead, use mock timer",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(SleepDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES))
    );

    @Override
    public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
        return new ForwardingAstVisitor() {
            @Override
            public boolean visitMethodInvocation(MethodInvocation node) {
                Expression operand = node.astOperand();
                if (node.astName().toString().equals("sleep") && operand.toString().equals("Thread") && !context.isSuppressedWithComment(node, ISSUE)) {
                    context.report(ISSUE, node, context.getLocation(node), "Don't call sleep. Use MockTimer instead.");
                }
                return super.visitMethodInvocation(node);
            }
        };
    }
}
