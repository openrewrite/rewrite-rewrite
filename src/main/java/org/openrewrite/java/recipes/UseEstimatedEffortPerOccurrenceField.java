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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseEstimatedEffortPerOccurrenceField extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final MethodMatcher GET_ESTIMATED_EFFORT_MATCHER = new MethodMatcher(RECIPE + " getEstimatedEffortPerOccurrence()", true);

    String displayName = "Replace `getEstimatedEffortPerOccurrence()` method with field";
    String description = "Recipe classes that return a simple expression from `getEstimatedEffortPerOccurrence()` can use a Lombok annotated field instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new DeclaresType<>(RECIPE, true),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (!TypeUtils.isAssignableTo(RECIPE, classDecl.getType())) {
                            return classDecl;
                        }
                        boolean addGetterAnnotation = !service(AnnotationService.class).isAnnotatedWith(classDecl, "lombok.Value");
                        getCursor().putMessage("addGetterAnnotation", addGetterAnnotation);
                        return (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        if (GET_ESTIMATED_EFFORT_MATCHER.matches(method.getMethodType())) {
                            Expression expr = extractImmediateReturnExpression(method);
                            if (expr != null) {
                                boolean addGetterAnnotation = getCursor().getNearestMessage("addGetterAnnotation", false);
                                return createField(method, expr, addGetterAnnotation);
                            }
                        }
                        return method;
                    }

                    private J.VariableDeclarations createField(
                            J.MethodDeclaration method,
                            Expression initializer,
                            boolean addGetterAnnotation) {
                        if (addGetterAnnotation) {
                            maybeAddImport("lombok.Getter");
                            return JavaTemplate.builder("@Getter final Duration estimatedEffortPerOccurrence = #{any(java.time.Duration)}")
                                    .javaParser(JavaParser.fromJavaVersion().classpath("lombok"))
                                    .imports("lombok.Getter", "java.time.Duration")
                                    .build()
                                    .apply(getCursor(),
                                            method.getCoordinates().replace(),
                                            initializer);
                        }
                        return JavaTemplate.builder("Duration estimatedEffortPerOccurrence = #{any(java.time.Duration)}")
                                .imports("java.time.Duration")
                                .build()
                                .apply(getCursor(),
                                        method.getCoordinates().replace(),
                                        initializer);
                    }

                    private @Nullable Expression extractImmediateReturnExpression(J.MethodDeclaration method) {
                        if (method.getBody() == null) {
                            return null;
                        }
                        List<Statement> statements = method.getBody().getStatements();
                        if (statements.size() != 1) {
                            return null;
                        }
                        Statement stmt = statements.get(0);
                        if (!(stmt instanceof J.Return)) {
                            return null;
                        }
                        J.Return returnStmt = (J.Return) stmt;
                        return returnStmt.getExpression();
                    }
                }
        );
    }
}
