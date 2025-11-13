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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Collections.emptyList;

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
                        if (!cd.hasModifier(J.Modifier.Type.Public) &&
                                TypeUtils.isAssignableTo("org.openrewrite.Recipe", cd.getType())) {

                            // Change any existing protected or private modifier to public
                            List<J.Modifier> mapped = ListUtils.map(cd.getModifiers(), mod ->
                                    mod.getType() == J.Modifier.Type.Protected || mod.getType() == J.Modifier.Type.Private ?
                                            mod.withType(J.Modifier.Type.Public) : mod);
                            if (mapped != cd.getModifiers()) {
                                return cd.withModifiers(mapped);
                            }

                            // Create a new modifier list with public at the beginning
                            J.Modifier publicModifier = new J.Modifier(
                                    cd.getId(),
                                    cd.getPrefix(),
                                    cd.getMarkers(),
                                    null,
                                    J.Modifier.Type.Public,
                                    emptyList());

                            // Clear the class prefix since the modifier now has it
                            J.ClassDeclaration updated = cd.withPrefix(Space.SINGLE_SPACE)
                                    .withModifiers(ListUtils.concat(publicModifier, cd.getModifiers()));

                            // Auto-format to ensure proper spacing
                            cd = maybeAutoFormat(cd, updated, updated.getName(), ctx, getCursor().getParentTreeCursor());
                        }

                        return cd;
                    }
                }
        );
    }
}
