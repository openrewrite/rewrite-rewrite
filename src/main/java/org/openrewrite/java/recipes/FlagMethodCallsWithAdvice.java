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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.marker.SearchResult;

import java.util.Map;

@RequiredArgsConstructor
class FlagMethodCallsWithAdvice extends JavaIsoVisitor<ExecutionContext> {

    private final Map<MethodMatcher, String> adviceByMatcher;

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
        String advice = adviceFor(call);
        return advice == null ? call : SearchResult.found(call, advice);
    }

    private @Nullable String adviceFor(MethodCall call) {
        for (Map.Entry<MethodMatcher, String> entry : adviceByMatcher.entrySet()) {
            if (entry.getKey().matches(call.getMethodType())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
