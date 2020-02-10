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

public class SystemTimeDetector extends Detector implements Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(
            "DIRECT_TIME_ACCESS",
            "Code should not access time directly",
            "Code should not access System time. Use Timer instead.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(SystemTimeDetector.class, EnumSet.of(Scope.JAVA_FILE))
    );

    private static final Set<String> BAD_METHODS = new HashSet<String>();
    static  {
        BAD_METHODS.add("nanoTime");
        BAD_METHODS.add("currentTimeMillis");
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
        return new ForwardingAstVisitor() {

            @Override
            public boolean visitMethodInvocation(MethodInvocation node) {
                Expression operand = node.astOperand();
                String methodName = node.astName().toString();
                if (BAD_METHODS.contains(methodName) && operand.toString().equals("System") && !context.isSuppressedWithComment(node, ISSUE)) {
                    context.report(ISSUE, context.getLocation(node), "Don't call " + methodName + " on system. Use" +
                            " Timer instead.");
                }
                return super.visitMethodInvocation(node);
            }
        };
    }
}

