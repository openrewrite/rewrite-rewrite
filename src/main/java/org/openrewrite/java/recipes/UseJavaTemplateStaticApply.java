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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class UseJavaTemplateStaticApply extends Recipe {

    private static final MethodMatcher BUILDER_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaTemplate builder(String)");
    private static final MethodMatcher BUILD_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaTemplate.Builder build()");
    private static final MethodMatcher APPLY_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaTemplate apply(org.openrewrite.Cursor, org.openrewrite.java.tree.JavaCoordinates, ..)");

    String displayName = "Use `JavaTemplate.apply()` static method";

    String description = "Replaces `JavaTemplate.builder(template).build().apply(cursor, coordinates, args...)` " +
            "with `JavaTemplate.apply(template, cursor, coordinates, args...)` when the builder chain has no " +
            "intermediate configuration methods.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(BUILDER_MATCHER),
                        new UsesMethod<>(BUILD_MATCHER),
                        new UsesMethod<>(APPLY_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Match .apply() on the result of .build()
                        if (!APPLY_MATCHER.matches(mi) ||
                            !(mi.getSelect() instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        J.MethodInvocation buildCall = (J.MethodInvocation) mi.getSelect();
                        if (!BUILD_MATCHER.matches(buildCall) ||
                            !(buildCall.getSelect() instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        // .build() must be called directly on .builder(String) with no intermediate methods
                        J.MethodInvocation builderCall = (J.MethodInvocation) buildCall.getSelect();
                        if (!BUILDER_MATCHER.matches(builderCall)) {
                            return mi;
                        }

                        // Collect all arguments: template string from builder + original apply args
                        List<Expression> allArgs = new ArrayList<>();
                        allArgs.add(builderCall.getArguments().get(0));
                        allArgs.addAll(mi.getArguments());

                        // Build template: JavaTemplate.apply(#{any()}, #{any()}, ...)
                        String args = String.join(", ", Collections.nCopies(allArgs.size(), "#{any()}"));
                        String template = "JavaTemplate.apply(" + args + ")";

                        J.MethodInvocation result = JavaTemplate.apply(template,
                                getCursor(), mi.getCoordinates().replace(),
                                allArgs.toArray());

                        // Copy type information from the original apply invocation
                        JavaType.Class javaTemplateType = (JavaType.Class) builderCall.getSelect().getType();
                        if (result.getSelect() instanceof J.Identifier) {
                            result = result.withSelect(
                                    ((J.Identifier) result.getSelect()).withType(javaTemplateType));
                        }
                        JavaType.Method originalMethodType = mi.getMethodType();
                        if (originalMethodType != null) {
                            List<String> paramNames = new ArrayList<>();
                            paramNames.add("template");
                            paramNames.addAll(originalMethodType.getParameterNames());

                            List<JavaType> paramTypes = new ArrayList<>();
                            paramTypes.add(JavaType.Primitive.String);
                            paramTypes.addAll(originalMethodType.getParameterTypes());

                            result = result.withMethodType(originalMethodType
                                    .withDeclaringType(javaTemplateType)
                                    .withParameterNames(paramNames)
                                    .withParameterTypes(paramTypes));
                            result = result.withName(result.getName()
                                    .withType(result.getMethodType()));
                        }
                        return result;
                    }
                });
    }
}
