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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static java.util.Collections.newSetFromMap;

public class FindSharedVisitorInstances extends Recipe {

    private static final String TREE_VISITOR = "org.openrewrite.TreeVisitor";
    private static final String RECIPE = "org.openrewrite.Recipe";

    private static final List<String> CONTAINERS = Arrays.asList(
            "java.lang.Iterable",
            "java.util.Map",
            "java.util.Optional"
    );

    @Getter
    final String displayName = "Find visitor instances shared across `getVisitor()` calls";

    @Getter
    final String description = "Find `static` fields, and instance fields of `Recipe` classes, holding `TreeVisitor` instances. " +
            "`getVisitor()` is called once per source file and source files may be visited concurrently, " +
            "so a shared visitor that walks the tree has its cursor, visit count and after-visit list corrupted by concurrent visits. " +
            "Construct visitors inside `getVisitor()` instead, including preconditions such as `UsesType` combined through `Preconditions.or()`, " +
            "which happen to be safe to share today but only because of implementation details of these visitors.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(TREE_VISITOR, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        Object maybeClass = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                        if (!(maybeClass instanceof J.ClassDeclaration)) {
                            return vd;
                        }
                        J.ClassDeclaration classDecl = (J.ClassDeclaration) maybeClass;
                        boolean isStatic = vd.hasModifier(J.Modifier.Type.Static) ||
                                classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface;
                        if (!isStatic && !TypeUtils.isAssignableTo(RECIPE, classDecl.getType())) {
                            return vd;
                        }
                        return vd.withVariables(ListUtils.map(vd.getVariables(), variable -> {
                            if (holdsVisitor(variable.getType(), newSetFromMap(new IdentityHashMap<>()))) {
                                return SearchResult.found(variable, "Visitor instance shared across concurrent visits");
                            }
                            return variable;
                        }));
                    }
                }
        );
    }

    private static boolean holdsVisitor(@Nullable JavaType type, Set<JavaType> seen) {
        if (type == null || !seen.add(type)) {
            return false;
        }
        if (type instanceof JavaType.Array) {
            return holdsVisitor(((JavaType.Array) type).getElemType(), seen);
        }
        if (type instanceof JavaType.GenericTypeVariable) {
            for (JavaType bound : ((JavaType.GenericTypeVariable) type).getBounds()) {
                if (holdsVisitor(bound, seen)) {
                    return true;
                }
            }
            return false;
        }
        if (TypeUtils.isAssignableTo(TREE_VISITOR, type)) {
            return true;
        }
        if (type instanceof JavaType.Parameterized && isContainer(type)) {
            for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                if (holdsVisitor(typeParameter, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isContainer(JavaType type) {
        for (String container : CONTAINERS) {
            if (TypeUtils.isAssignableTo(container, type)) {
                return true;
            }
        }
        return false;
    }
}
