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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

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
                new UsesType<>("com.google.errorprone.refaster.annotation.*", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        if (FindAnnotations.find(m, "@com.google.errorprone.refaster.annotation.*Template").isEmpty()) {
                            return m;
                        }

                        // Check if method is void
                        if (m.getReturnTypeExpression() == null ||
                                m.getReturnTypeExpression().getType() != JavaType.Primitive.Void) {
                            return m;
                        }

                        // Check if body has exactly one expression
                        J.Block body = m.getBody();
                        if (body == null ||
                                body.getStatements().size() != 1 ||
                                !(body.getStatements().get(0) instanceof Expression)) {
                            return m;
                        }

                        Expression expression = (Expression) body.getStatements().get(0);

                        // If the expression has no type, we can't convert it to a return statement
                        JavaType expressionType = expression.getType();
                        if (expressionType == null) {
                            return m;
                        }

                        // Skip if it's a method call expression (like System.out.println)
                        if (expression instanceof J.MethodInvocation) {
                            J.MethodInvocation methodCall = (J.MethodInvocation) expression;
                            // If the method returns void, we shouldn't convert it to a return statement
                            if (methodCall.getMethodType() != null &&
                                    JavaType.Primitive.Void == methodCall.getMethodType().getReturnType()) {
                                return m;
                            }
                        }

                        // Update return type if we have an expression
                        TypeTree newReturnType = createReturnType(expressionType);
                        if (newReturnType == null) {
                            return m;
                        }

                        // Convert expression to return statement
                        return m
                                .withReturnTypeExpression(newReturnType.withPrefix(m.getReturnTypeExpression().getPrefix()))
                                .withBody(body.withStatements(singletonList(new J.Return(
                                        Tree.randomId(),
                                        expression.getPrefix(),
                                        expression.getMarkers(),
                                        expression.withPrefix(Space.SINGLE_SPACE)
                                ))));
                    }

                    private @Nullable TypeTree createReturnType(JavaType exprType) {
                        if (exprType instanceof JavaType.Primitive) {
                            // For primitives, create a J.Primitive
                            return new J.Primitive(
                                    Tree.randomId(),
                                    Space.EMPTY,
                                    org.openrewrite.marker.Markers.EMPTY,
                                    (JavaType.Primitive) exprType
                            );
                        }
                        if (exprType instanceof JavaType.FullyQualified && !TypeUtils.isObject(exprType)) {
                            // For object types, use TypeTree.build()
                            JavaType.FullyQualified fqType = (JavaType.FullyQualified) exprType;
                            return TypeTree.build(fqType.getFullyQualifiedName())
                                    .withType(exprType);
                        }
                        if (exprType instanceof JavaType.Array) {
                            // For arrays, handle the element type and add array dimensions
                            JavaType elemType = ((JavaType.Array) exprType).getElemType();
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
                                return TypeTree.build(primitiveKeyword + arrayBrackets)
                                        .withType(exprType);
                            }
                            if (elemType instanceof JavaType.FullyQualified) {
                                // For object arrays, use the class name with brackets
                                String arrayBrackets = "";
                                JavaType currentType = exprType;
                                while (currentType instanceof JavaType.Array) {
                                    arrayBrackets += "[]";
                                    currentType = ((JavaType.Array) currentType).getElemType();
                                }
                                return TypeTree.build(((JavaType.FullyQualified) elemType).getClassName() + arrayBrackets)
                                        .withType(exprType);
                            }
                        }
                        return null;
                    }
                }
        );
    }
}
