/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

public class ReplaceNullWithDoesNotExist extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace null with RewriteTest.doesNotExist()";
    }

    @Override
    public String getDescription() {
        return "Replace the first or second `null` argument in OpenRewrite Assertions class methods with `RewriteTest.doesNotExist()`.";
    }

    // Match any static method from Assertions classes
    private static final MethodMatcher ASSERTIONS_MATCHER = new MethodMatcher("org.openrewrite.*.Assertions *(String, String, ..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(ASSERTIONS_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is a call to an Assertions method
                        if (!ASSERTIONS_MATCHER.matches(mi)) {
                            return mi;
                        }

                        // Check if the method is a static method from an Assertions class
                        JavaType.Method methodType = mi.getMethodType();
                        if (methodType == null ||
                                !methodType.getFlags().contains(Flag.Static) ||
                                !methodType.getDeclaringType().getFullyQualifiedName().matches("org\\.openrewrite\\.\\w+\\.Assertions")) {
                            return mi;
                        }

                        List<Expression> oldArgs = method.getArguments();
                        List<Expression> newArgs = ListUtils.map(oldArgs, (index, arg) ->
                                J.Literal.isLiteralValue(arg, null) && (index == 0 || index == 1) ? replaceArgument(arg) : arg);
                        if (oldArgs != newArgs) {
                            maybeAddImport("org.openrewrite.test.RewriteTest", "doesNotExist", false);
                            return mi.withArguments(newArgs);
                        }
                        return mi;
                    }

                    private J.MethodInvocation replaceArgument(Expression nullArg) {
                        return JavaTemplate.builder("doesNotExist()")
                                .javaParser(JavaParser.fromJavaVersion().classpath("rewrite-test"))
                                .staticImports("org.openrewrite.test.RewriteTest.doesNotExist")
                                .build()
                                .apply(new Cursor(getCursor(), nullArg), nullArg.getCoordinates().replace())
                                .withPrefix(nullArg.getPrefix());
                    }
                }
        );
    }
}
