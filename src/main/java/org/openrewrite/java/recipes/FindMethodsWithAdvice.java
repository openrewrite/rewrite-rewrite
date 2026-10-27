/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.marker.SearchResult;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindMethodsWithAdvice extends Recipe {

    String displayName = "Find method calls and say what to do instead";

    String description = "Finds method calls, constructor calls and method references matching a method pattern, " +
            "and marks each one with advice on what to do instead, so whoever reads the result knows the next step.";

    @Option(displayName = "Method pattern",
            description = MethodMatcher.METHOD_PATTERN_INVOCATIONS_DESCRIPTION,
            example = "org.openrewrite.ExecutionContext getMessages()")
    String methodPattern;

    @Option(displayName = "Match on overrides",
            description = "When enabled, find methods that are overrides of the method pattern.",
            required = false)
    @Nullable
    Boolean matchOverrides;

    @Option(displayName = "Advice",
            description = "What to do instead, shown on each match.",
            example = "Keep state across source files in a `ScanningRecipe` accumulator")
    String advice;

    @Override
    public Validated<Object> validate() {
        return super.validate().and(MethodMatcher.validate(methodPattern));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher matcher = new MethodMatcher(methodPattern, matchOverrides);
        return Preconditions.check(new UsesMethod<>(matcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                return flag(super.visitMethodInvocation(method, ctx));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                return flag(super.visitNewClass(newClass, ctx));
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                return flag(super.visitMemberReference(memberRef, ctx));
            }

            private <T extends MethodCall> T flag(T call) {
                return matcher.matches(call.getMethodType()) ? SearchResult.found(call, advice) : call;
            }
        });
    }
}
