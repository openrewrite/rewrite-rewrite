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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;

import static java.util.Collections.singletonList;

public class RefasterTemplateReturn extends Recipe {

    @Override
    public String getDisplayName() {
        return "Ensure Refaster templates return correct types";
    }

    @Override
    public String getDescription() {
        return "Ensures that methods annotated with `@BeforeTemplate` or `@AfterTemplate` have a return type " +
                "matching the only expression in the method body, where applicable, instead of `void`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("com.google.errorprone.refaster.annotation.BeforeTemplate", false),
                        new UsesType<>("com.google.errorprone.refaster.annotation.AfterTemplate", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        // Check if method has @BeforeTemplate or @AfterTemplate annotation
                        boolean hasTemplateAnnotation = m.getLeadingAnnotations().stream()
                                .anyMatch(ann -> {
                                    String simpleName = ann.getSimpleName();
                                    return "BeforeTemplate".equals(simpleName) || "AfterTemplate".equals(simpleName);
                                });

                        if (!hasTemplateAnnotation) {
                            return m;
                        }

                        // Check if method is void
                        JavaType.Primitive voidType = JavaType.Primitive.Void;
                        if (m.getReturnTypeExpression() == null ||
                                !voidType.equals(m.getReturnTypeExpression().getType())) {
                            return m;
                        }

                        // Check if body has exactly one statement
                        if (m.getBody() == null) {
                            return m;
                        }

                        List<Statement> statements = m.getBody().getStatements();
                        if (statements.size() != 1) {
                            return m;
                        }

                        Statement statement = statements.get(0);
                        Expression expressionToReturn = null;
                        boolean needsReturnStatement = false;

                        // Check if the statement is a return statement with an expression
                        if (statement instanceof J.Return) {
                            J.Return returnStatement = (J.Return) statement;
                            expressionToReturn = returnStatement.getExpression();
                        }
                        // Check if the statement is an expression (method call, assignment, etc.)
                        else if (statement instanceof Expression) {
                            expressionToReturn = (Expression) statement;
                            needsReturnStatement = true;
                        }

                        // Update return type if we have an expression
                        if (expressionToReturn != null && expressionToReturn.getType() != null) {
                            JavaType exprType = expressionToReturn.getType();
                            TypeTree newReturnType = null;

                            if (exprType instanceof JavaType.Primitive) {
                                // For primitives, create a J.Primitive
                                newReturnType = new J.Primitive(
                                        java.util.UUID.randomUUID(),
                                        org.openrewrite.java.tree.Space.EMPTY,
                                        org.openrewrite.marker.Markers.EMPTY,
                                        (JavaType.Primitive) exprType
                                );
                            } else if (exprType instanceof JavaType.FullyQualified) {
                                // For object types, use TypeTree.build()
                                JavaType.FullyQualified fqType = (JavaType.FullyQualified) exprType;
                                newReturnType = TypeTree.build(fqType.getFullyQualifiedName())
                                        .withType(exprType);
                            } else if (exprType instanceof JavaType.Array) {
                                // For arrays, handle the element type and add array dimensions
                                JavaType.Array arrayType = (JavaType.Array) exprType;
                                JavaType elemType = arrayType.getElemType();

                                if (elemType instanceof JavaType.Primitive) {
                                    // For primitive arrays, create the type name with brackets
                                    String primitiveKeyword = ((JavaType.Primitive) elemType).getKeyword();
                                    String arrayBrackets = "";
                                    JavaType currentType = exprType;
                                    while (currentType instanceof JavaType.Array) {
                                        arrayBrackets += "[]";
                                        currentType = ((JavaType.Array) currentType).getElemType();
                                    }

                                    // Build a type tree for primitive array (e.g., "int[]")
                                    newReturnType = TypeTree.build(primitiveKeyword + arrayBrackets)
                                            .withType(exprType);
                                } else if (elemType instanceof JavaType.FullyQualified) {
                                    // For object arrays, use the class name with brackets
                                    String arrayBrackets = "";
                                    JavaType currentType = exprType;
                                    while (currentType instanceof JavaType.Array) {
                                        arrayBrackets += "[]";
                                        currentType = ((JavaType.Array) currentType).getElemType();
                                    }

                                    newReturnType = TypeTree.build(((JavaType.FullyQualified) elemType).getClassName() + arrayBrackets)
                                            .withType(exprType);
                                }
                            }

                            if (newReturnType != null) {
                                // Preserve prefix whitespace from the original void return type
                                newReturnType = newReturnType.withPrefix(m.getReturnTypeExpression().getPrefix());
                                m = m.withReturnTypeExpression(newReturnType);

                                // Convert expression statement to return statement if needed
                                if (needsReturnStatement) {
                                    J.Return newReturn = new J.Return(
                                            java.util.UUID.randomUUID(),
                                            statement.getPrefix(),
                                            statement.getMarkers(),
                                            expressionToReturn.withPrefix(org.openrewrite.java.tree.Space.SINGLE_SPACE)
                                    );

                                    m = m.withBody(m.getBody().withStatements(singletonList(newReturn)));
                                }
                            }
                        }

                        return m;
                    }
                }
        );
    }
}
