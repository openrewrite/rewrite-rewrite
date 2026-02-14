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

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class UseVisitWithParentCursor extends Recipe {

    @Getter
    final String displayName = "Use `visit` with parent cursor when calling from another visitor";

    @Getter
    final String description = "When calling another visitor from within a visitor, use the generic " +
            "`visit(tree, ctx, getCursor().getParentTreeCursor())` method instead of a specific " +
            "`visit*` method like `visitMethodInvocation`. The specific visit methods bypass the " +
            "visitor lifecycle, including cursor setup, pre/post visit hooks, and observer notifications.";

    private static final TypeMatcher TREE_VISITOR_MATCHER =
            new TypeMatcher("org.openrewrite.TreeVisitor", true);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.openrewrite.TreeVisitor", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    private static final String IN_TREE_VISITOR = "IN_TREE_VISITOR";

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (TypeUtils.isAssignableTo("org.openrewrite.TreeVisitor", classDecl.getType())) {
                            getCursor().putMessage(IN_TREE_VISITOR, true);
                        }
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (newClass.getBody() != null &&
                                TypeUtils.isAssignableTo("org.openrewrite.TreeVisitor", newClass.getType())) {
                            getCursor().putMessage(IN_TREE_VISITOR, true);
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Method name must match visit[A-Z]* (not just "visit" or "visitNonNull")
                        String methodName = mi.getSimpleName();
                        if (!methodName.startsWith("visit") || methodName.length() <= 5 ||
                                !Character.isUpperCase(methodName.charAt(5)) ||
                                "visitNonNull".equals(methodName)) {
                            return mi;
                        }

                        // Must have exactly 2 arguments
                        if (mi.getArguments().size() != 2) {
                            return mi;
                        }

                        // Must be declared on a TreeVisitor subclass
                        if (mi.getMethodType() == null ||
                                !TREE_VISITOR_MATCHER.matches(mi.getMethodType().getDeclaringType())) {
                            return mi;
                        }

                        // Exclude super.visitXxx() and this.visitXxx() - normal delegation
                        Expression select = mi.getSelect();
                        if (select == null) {
                            return mi; // Unqualified call = implicit this
                        }
                        if (select instanceof J.Identifier) {
                            String name = ((J.Identifier) select).getSimpleName();
                            if ("super".equals(name) || "this".equals(name)) {
                                return mi;
                            }
                        }

                        // Must be inside a TreeVisitor so getCursor() is available
                        if (getCursor().getNearestMessage(IN_TREE_VISITOR) == null) {
                            return mi;
                        }

                        // Replace visitXxx(tree, ctx) with visit(tree, ctx, getCursor().getParentTreeCursor())
                        J.MethodInvocation result = JavaTemplate.builder(
                                        "#{any()}.visit(#{any()}, #{any()}, getCursor().getParentTreeCursor())")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpath("rewrite-core", "rewrite-java"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(),
                                        mi.getSelect(), mi.getArguments().get(0), mi.getArguments().get(1));

                        // Fix up method type when the template can't resolve it
                        // (e.g. when the select is a locally-defined visitor class)
                        JavaType.Method originalMt = mi.getMethodType();
                        if (originalMt != null) {
                            JavaType.FullyQualified cursorType = JavaType.ShallowClass.build("org.openrewrite.Cursor");
                            JavaType.Method visitMt = originalMt
                                    .withName("visit")
                                    .withParameterNames(ListUtils.concat(originalMt.getParameterNames(), "parent"))
                                    .withParameterTypes(ListUtils.concat(originalMt.getParameterTypes(), cursorType));
                            result = result.withMethodType(visitMt)
                                    .withName(result.getName().withType(visitMt));
                        }
                        return result;
                    }
                }
        );
    }
}
