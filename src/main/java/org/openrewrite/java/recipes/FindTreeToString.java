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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.List;

public class FindTreeToString extends Recipe {

    private static final String TREE = "org.openrewrite.Tree";
    private static final String MESSAGE = "LST element printed by `toString()`";

    private static final MethodMatcher TO_STRING = new MethodMatcher("*..* toString()");

    private static final List<MethodMatcher> CONVERTING_METHODS = Arrays.asList(
            new MethodMatcher("java.lang.String valueOf(java.lang.Object)"),
            new MethodMatcher("java.lang.String format(..)"),
            new MethodMatcher("java.lang.String formatted(..)"),
            new MethodMatcher("java.util.Objects toString(..)"),
            new MethodMatcher("java.lang.StringBuilder append(java.lang.Object)"),
            new MethodMatcher("java.lang.StringBuffer append(java.lang.Object)")
    );

    @Getter
    final String displayName = "Find LST elements converted to strings";

    @Getter
    final String description = "Find `toString()`, `String.valueOf(..)`, `Objects.toString(..)`, `String.format(..)`, " +
            "`StringBuilder.append(..)` and string concatenation on `org.openrewrite.Tree` subtypes outside of tests. " +
            "`toString()` prints the element without a cursor, so `J` elements print as Java also in Kotlin, Groovy or C# sources, " +
            "and other trees print whatever their class happens to implement. " +
            "Searching the printed text also matches comments and string literals, and misses differently spelled forms. " +
            "Check the LST and its types instead, or call `print(Cursor)` when the source text itself is needed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(TREE, true),
                        new IsLikelyNotTest().getVisitor()
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (TO_STRING.matches(m) && m.getSelect() != null && isTree(m.getSelect())) {
                            return SearchResult.found(m, MESSAGE);
                        }
                        for (MethodMatcher matcher : CONVERTING_METHODS) {
                            if (matcher.matches(m)) {
                                return m.withArguments(ListUtils.map(m.getArguments(), FindTreeToString::markTree));
                            }
                        }
                        return m;
                    }

                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                        J.MemberReference m = super.visitMemberReference(memberRef, ctx);
                        if ("toString".equals(m.getReference().getSimpleName()) && isTree(m.getContaining())) {
                            return SearchResult.found(m, MESSAGE);
                        }
                        return m;
                    }

                    @Override
                    public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                        J.Binary b = super.visitBinary(binary, ctx);
                        if (b.getOperator() == J.Binary.Type.Addition && TypeUtils.isString(b.getType())) {
                            return b.withLeft(markTree(b.getLeft())).withRight(markTree(b.getRight()));
                        }
                        return b;
                    }

                    @Override
                    public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                        J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, ctx);
                        if (a.getOperator() == J.AssignmentOperation.Type.Addition && TypeUtils.isString(a.getVariable().getType())) {
                            return a.withAssignment(markTree(a.getAssignment()));
                        }
                        return a;
                    }
                }
        );
    }

    private static Expression markTree(Expression expression) {
        return isTree(expression) ? SearchResult.found(expression, MESSAGE) : expression;
    }

    private static boolean isTree(Expression expression) {
        return TypeUtils.isAssignableTo(TREE, expression.getType());
    }
}
