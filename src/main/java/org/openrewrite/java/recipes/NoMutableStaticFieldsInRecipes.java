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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class NoMutableStaticFieldsInRecipes extends Recipe {
    @Getter
    final String displayName = "Recipe classes should not have mutable `static` fields";

    @Getter
    final String description = "Add the `final` keyword to mutable static fields in Recipe classes.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.openrewrite.Recipe", true),
                new JavaIsoVisitor<ExecutionContext>() {
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
                                !vd.hasModifier(J.Modifier.Type.Final)) {
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
                }
        );
    }
}
