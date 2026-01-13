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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseDisplayNameAndDescriptionFields extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final MethodMatcher GET_DISPLAY_NAME_MATCHER = new MethodMatcher(RECIPE + " getDisplayName()", true);
    private static final MethodMatcher GET_DESCRIPTION_MATCHER = new MethodMatcher(RECIPE + " getDescription()", true);

    String displayName = "Replace `getDisplayName()` and `getDescription()` methods with fields";
    String description = "Recipe classes annotated with `@lombok.Value` that return a simple string literal " +
            "(or concatenation of string literals) from `getDisplayName()` or `getDescription()` can use fields instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new DeclaresType<>(RECIPE, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (!TypeUtils.isAssignableTo(RECIPE, cd.getType()) ||
                                !service(AnnotationService.class).isAnnotatedWith(cd, "lombok.Value")) {
                            return cd;
                        }

                        return cd.withBody(cd.getBody().withStatements(
                                ListUtils.map(cd.getBody().getStatements(), stmt -> {
                                    if (stmt instanceof J.MethodDeclaration) {
                                        J.MethodDeclaration method = (J.MethodDeclaration) stmt;
                                        if (GET_DISPLAY_NAME_MATCHER.matches(method.getMethodType())) {
                                            Expression expr = extractStringLiteralExpression(method);
                                            if (expr != null) {
                                                return createField("displayName", expr, method.getPrefix());
                                            }
                                        } else if (GET_DESCRIPTION_MATCHER.matches(method.getMethodType())) {
                                            Expression expr = extractStringLiteralExpression(method);
                                            if (expr != null) {
                                                return createField("description", expr, method.getPrefix());
                                            }
                                        }
                                    }
                                    return stmt;
                                })
                        ));
                    }

                    private J.VariableDeclarations createField(String fieldName, Expression initializer, Space prefix) {
                        JavaType.Primitive stringType = JavaType.Primitive.String;
                        return new J.VariableDeclarations(
                                Tree.randomId(),
                                prefix,
                                Markers.EMPTY,
                                emptyList(),
                                emptyList(),
                                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "String", stringType, null),
                                null,
                                singletonList(new JRightPadded<>(
                                        new J.VariableDeclarations.NamedVariable(
                                                Tree.randomId(),
                                                Space.SINGLE_SPACE,
                                                Markers.EMPTY,
                                                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), fieldName, stringType, null),
                                                emptyList(),
                                                new JLeftPadded<>(Space.SINGLE_SPACE, initializer.withPrefix(Space.SINGLE_SPACE), Markers.EMPTY),
                                                null
                                        ),
                                        Space.EMPTY,
                                        Markers.EMPTY
                                ))
                        );
                    }

                    private @Nullable Expression extractStringLiteralExpression(J.MethodDeclaration method) {
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
                        Expression expr = returnStmt.getExpression();
                        if (expr == null || !isStringLiteralOrConcatenation(expr)) {
                            return null;
                        }
                        return expr;
                    }

                    private boolean isStringLiteralOrConcatenation(Expression expr) {
                        if (expr instanceof J.Parentheses) {
                            expr = expr.unwrap();
                        }
                        if (expr instanceof J.Literal) {
                            return TypeUtils.isString(((J.Literal) expr).getType());
                        }
                        if (expr instanceof J.Binary) {
                            J.Binary binary = (J.Binary) expr;
                            if (binary.getOperator() == J.Binary.Type.Addition) {
                                return isStringLiteralOrConcatenation(binary.getLeft()) &&
                                        isStringLiteralOrConcatenation(binary.getRight());
                            }
                        }
                        return false;
                    }
                }
        );
    }
}
