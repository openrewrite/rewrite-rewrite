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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.List;

public class FindTreeToString extends Recipe {

    private static final String TREE = "org.openrewrite.Tree";
    private static final String MESSAGE = "LST converted to text. For matching, use node accessors " +
            "(`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; " +
            "for expression comparison, consider `SemanticallyEqual`. " +
            "Reuse trees directly or through `JavaTemplate` parameters. " +
            "If source text is required, use `print(Cursor)` on each tree.";

    private static final String JS_MESSAGE = "LST converted to text. For matching, use node properties " +
            "(`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. " +
            "Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.";

    private static final MethodMatcher TO_STRING = new MethodMatcher("*..* toString()");

    private static final List<MethodMatcher> CONVERTING_METHODS = Arrays.asList(
            new MethodMatcher("java.lang.String valueOf(java.lang.Object)"),
            new MethodMatcher("java.lang.String format(..)"),
            new MethodMatcher("java.lang.String formatted(..)"),
            new MethodMatcher("java.util.Objects toString(..)"),
            new MethodMatcher("java.lang.StringBuilder append(java.lang.Object)"),
            new MethodMatcher("java.lang.StringBuffer append(java.lang.Object)"),
            new MethodMatcher("java.lang.StringBuilder insert(int, java.lang.Object)"),
            new MethodMatcher("java.lang.StringBuffer insert(int, java.lang.Object)"),
            new MethodMatcher("java.io.PrintStream print*(..)"),
            new MethodMatcher("java.io.PrintStream format(..)"),
            new MethodMatcher("java.io.PrintWriter print*(..)"),
            new MethodMatcher("java.io.PrintWriter format(..)"),
            new MethodMatcher("java.text.MessageFormat format(..)")
    );

    private static final List<MethodMatcher> CONVERTING_REFERENCES = Arrays.asList(
            new MethodMatcher("java.lang.Object toString()"),
            new MethodMatcher("java.lang.String valueOf(java.lang.Object)"),
            new MethodMatcher("java.util.Objects toString(java.lang.Object)")
    );

    @Getter
    final String displayName = "Find LST elements converted to strings";

    @Getter
    final String description = "Find `toString()`, `String.valueOf(..)`, `Objects.toString(..)`, `String.format(..)`, " +
            "`StringBuilder.append(..)`, `PrintStream.println(..)`, string concatenation and method references such as `Object::toString` " +
            "on `org.openrewrite.Tree` subtypes, and on collections of them, outside of tests. " +
            "Also finds `toString()`, `String(..)`, string concatenation and template interpolation of " +
            "`@openrewrite/rewrite` trees in JavaScript and TypeScript recipes. " +
            "`toString()` prints the element without a cursor, so `J` elements print as Java also in Kotlin, Groovy or C# sources, " +
            "and other trees print whatever their class happens to implement. " +
            "Searching the printed text also matches comments and string literals, and misses differently spelled forms. " +
            "Check the LST and its types instead, or call `print(Cursor)` when the source text itself is needed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JS.CompilationUnit) {
                    return javaScriptVisitor().visit(tree, ctx, getCursor());
                }
                return javaVisitor().visit(tree, ctx, getCursor());
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> javaVisitor() {
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
                        if ("toString".equals(m.getReference().getSimpleName()) && isTree(m.getContaining()) ||
                            convertsFunctionInput(m)) {
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

    private TreeVisitor<?, ExecutionContext> javaScriptVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                String path = cu.getSourcePath().toString().replace('\\', '/');
                if (path.matches("(?:.*/)?(?:test|tests|__tests__)/.*") ||
                    path.matches(".*\\.(?:test|spec)\\.[cm]?[jt]sx?")) {
                    return cu;
                }
                return super.visitJsCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if ("toString".equals(m.getSimpleName()) && m.getSelect() != null &&
                    (m.getArguments().isEmpty() || m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Empty) &&
                    isJavaScriptTree(m.getSelect().getType())) {
                    return SearchResult.found(m, JS_MESSAGE);
                }
                if (m.getSelect() == null && "String".equals(m.getSimpleName()) &&
                    m.getMethodType() != null && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "String")) {
                    return m.withArguments(ListUtils.map(m.getArguments(), FindTreeToString::markJavaScriptTree));
                }
                return m;
            }

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = super.visitBinary(binary, ctx);
                if (b.getOperator() == J.Binary.Type.Addition && TypeUtils.isString(b.getType())) {
                    return b.withLeft(markJavaScriptTree(b.getLeft())).withRight(markJavaScriptTree(b.getRight()));
                }
                return b;
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, ctx);
                if (a.getOperator() == J.AssignmentOperation.Type.Addition && TypeUtils.isString(a.getVariable().getType())) {
                    return a.withAssignment(markJavaScriptTree(a.getAssignment()));
                }
                return a;
            }

            @Override
            public JS.TemplateExpression visitTemplateExpression(JS.TemplateExpression template, ExecutionContext ctx) {
                JS.TemplateExpression t = super.visitTemplateExpression(template, ctx);
                if (getCursor().getParentOrThrow().getValue() instanceof JS.TaggedTemplateExpression) {
                    return t;
                }
                return t.withSpans(ListUtils.map(t.getSpans(), span -> span.getExpression() instanceof Expression ?
                        span.withExpression(markJavaScriptTree((Expression) span.getExpression())) : span));
            }
        };
    }

    private static Expression markJavaScriptTree(Expression expression) {
        return isJavaScriptTree(expression.getType()) ? SearchResult.found(expression, JS_MESSAGE) : expression;
    }

    private static boolean isJavaScriptTree(@Nullable JavaType type) {
        if (TypeUtils.isAssignableTo("@openrewrite/rewrite.Tree", type)) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return isJavaScriptTree(((JavaType.Array) type).getElemType());
        }
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized != null && (TypeUtils.isOfClassType(parameterized, "Array") ||
                                      TypeUtils.isOfClassType(parameterized, "ReadonlyArray"))) {
            return parameterized.getTypeParameters().stream().anyMatch(FindTreeToString::isJavaScriptTree);
        }
        return false;
    }

    private static Expression markTree(Expression expression) {
        return isTree(expression) ? SearchResult.found(expression, MESSAGE) : expression;
    }

    private static boolean convertsFunctionInput(J.MemberReference memberRef) {
        if (CONVERTING_REFERENCES.stream().noneMatch(matcher -> matcher.matches(memberRef.getMethodType()))) {
            return false;
        }
        JavaType.Parameterized function = TypeUtils.asParameterized(memberRef.getType());
        return function != null && !function.getTypeParameters().isEmpty() &&
               isTreeOrTrees(function.getTypeParameters().get(0));
    }

    private static boolean isTree(Expression expression) {
        return isTreeOrTrees(expression.getType());
    }

    private static boolean isTreeOrTrees(@Nullable JavaType type) {
        if (TypeUtils.isAssignableTo(TREE, type)) {
            return true;
        }
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized != null && (TypeUtils.isAssignableTo("java.util.Collection", parameterized) ||
                                      TypeUtils.isAssignableTo("java.util.Map", parameterized) ||
                                      TypeUtils.isAssignableTo("java.util.Optional", parameterized))) {
            for (JavaType typeParameter : parameterized.getTypeParameters()) {
                if (isTreeOrTrees(typeParameter)) {
                    return true;
                }
            }
        }
        return false;
    }
}
