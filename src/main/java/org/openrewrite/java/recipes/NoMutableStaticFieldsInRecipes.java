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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

public class NoMutableStaticFieldsInRecipes extends Recipe {
    private static final String ASSIGNED_NAMES = "assignedNames";

    @Getter
    final String displayName = "Recipe classes should not have mutable `static` fields";

    @Getter
    final String description = "Add the `final` keyword to mutable static fields in Recipe classes, " +
            "unless they are assigned after their declaration, such as a lazily computed cache.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.openrewrite.Recipe", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        getCursor().putMessage(ASSIGNED_NAMES, assignedNames(cu));
                        return super.visitCompilationUnit(cu, ctx);
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (TypeUtils.isAssignableTo("org.openrewrite.Recipe", classDecl.getType())) {
                            return super.visitClassDeclaration(classDecl, ctx);
                        }
                        return classDecl;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        if (vd.hasModifier(J.Modifier.Type.Static) &&
                                !vd.hasModifier(J.Modifier.Type.Final) &&
                                isNeverReassigned(vd)) {
                            return vd.withModifiers(ListUtils.concat(vd.getModifiers(), new J.Modifier(
                                    Tree.randomId(),
                                    Space.SINGLE_SPACE,
                                    Markers.EMPTY,
                                    null,
                                    J.Modifier.Type.Final,
                                    emptyList()
                            )));
                        }
                        return vd;
                    }

                    private boolean isNeverReassigned(J.VariableDeclarations vd) {
                        Set<String> assigned = getCursor().getNearestMessage(ASSIGNED_NAMES, emptySet());
                        for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                            if (variable.getInitializer() == null || assigned.contains(variable.getSimpleName())) {
                                return false;
                            }
                        }
                        return true;
                    }
                }
        );
    }

    private static Set<String> assignedNames(J.CompilationUnit cu) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> names) {
                addName(assignment.getVariable(), names);
                return super.visitAssignment(assignment, names);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, Set<String> names) {
                addName(assignOp.getVariable(), names);
                return super.visitAssignmentOperation(assignOp, names);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, Set<String> names) {
                if (unary.getOperator().isModifying()) {
                    addName(unary.getExpression(), names);
                }
                return super.visitUnary(unary, names);
            }

            private void addName(Expression target, Set<String> names) {
                Expression unwrapped = target.unwrap();
                if (unwrapped instanceof J.Identifier) {
                    names.add(((J.Identifier) unwrapped).getSimpleName());
                } else if (unwrapped instanceof J.FieldAccess) {
                    names.add(((J.FieldAccess) unwrapped).getSimpleName());
                }
            }
        }.reduce(cu, new HashSet<>());
    }
}
