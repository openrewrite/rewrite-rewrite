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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeClassesShouldBePublic extends Recipe {
    @Override
    public String getDisplayName() {
        return "Recipe classes should be public";
    }

    @Override
    public String getDescription() {
        return "Ensures that classes extending Recipe are declared as public for proper visibility and accessibility.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.openrewrite.Recipe", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        // Check if this class extends Recipe and is not already public
                        if (TypeUtils.isAssignableTo("org.openrewrite.Recipe", cd.getType()) &&
                            cd.getKind() != J.ClassDeclaration.Kind.Type.Interface &&
                            cd.getModifiers().stream().noneMatch(mod -> mod.getType() == J.Modifier.Type.Public)) {

                            // Create a new modifier list with public at the beginning
                            List<J.Modifier> newModifiers = new ArrayList<>(cd.getModifiers());
                            newModifiers.add(0, new J.Modifier(
                                    cd.getId(),
                                    cd.getPrefix(),
                                    cd.getMarkers(),
                                    null,
                                    J.Modifier.Type.Public,
                                    Collections.emptyList()
                            ));

                            // Update the class declaration
                            J.ClassDeclaration updated = cd.withModifiers(newModifiers);

                            // Clear the class prefix since the modifier now has it
                            updated = updated.withPrefix(updated.getPrefix().withWhitespace(" "));

                            // Auto-format to ensure proper spacing
                            cd = maybeAutoFormat(cd, updated, updated.getName(), ctx, getCursor().getParentTreeCursor());
                        }

                        return cd;
                    }
                }
        );
    }
}
