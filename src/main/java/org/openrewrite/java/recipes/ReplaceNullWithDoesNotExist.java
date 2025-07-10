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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>("org.openrewrite.*.Assertions *(..)"),
                new JavaIsoVisitor<ExecutionContext>() {
                    // Match any static method from Assertions classes
                    private final MethodMatcher assertionsMatcher = new MethodMatcher("org.openrewrite.*.Assertions *(..)");

                    private final JavaTemplate doesNotExistTemplate = JavaTemplate.builder("doesNotExist()")
                            .contextSensitive()
                            .staticImports("org.openrewrite.test.RewriteTest.doesNotExist")
                            .build();

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is a call to an Assertions method
                        if (!assertionsMatcher.matches(mi)) {
                            return mi;
                        }

                        // Check if the method is a static method from an Assertions class
                        JavaType.Method methodType = mi.getMethodType();
                        if (methodType == null || !methodType.getFlags().contains(Flag.Static) ||
                            !methodType.getDeclaringType().getFullyQualifiedName().matches("org\\.openrewrite\\.\\w+\\.Assertions")) {
                            return mi;
                        }

                        List<Expression> args = mi.getArguments();
                        if (args.isEmpty()) {
                            return mi;
                        }

                        boolean modified = false;

                        // Check first argument
                        if (isNullLiteral(args.get(0))) {
                            mi = mi.withArguments(replaceArgument(mi, 0, ctx));
                            modified = true;
                        }

                        // Check second argument if first wasn't null
                        if (!modified && args.size() >= 2 && isNullLiteral(args.get(1))) {
                            mi = mi.withArguments(replaceArgument(mi, 1, ctx));
                            modified = true;
                        }

                        if (modified) {
                            maybeAddImport("org.openrewrite.test.RewriteTest");
                        }

                        return mi;
                    }

                    private boolean isNullLiteral(Expression expr) {
                        return expr instanceof J.Literal && ((J.Literal) expr).getValue() == null;
                    }

                    private List<Expression> replaceArgument(J.MethodInvocation method, int index, ExecutionContext ctx) {
                        List<Expression> args = method.getArguments();
                        Expression nullArg = args.get(index);
                        Expression replacement = doesNotExistTemplate.apply(getCursor(), nullArg.getCoordinates().replace())
                                .withPrefix(nullArg.getPrefix());

                        return replaceAtIndex(args, index, replacement);
                    }

                    private List<Expression> replaceAtIndex(List<Expression> list, int index, Expression replacement) {
                        List<Expression> newList = new java.util.ArrayList<>(list);
                        newList.set(index, replacement);
                        return newList;
                    }
                }
        );
    }
}
