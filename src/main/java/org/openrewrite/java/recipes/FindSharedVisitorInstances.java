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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Collections.newSetFromMap;

public class FindSharedVisitorInstances extends Recipe {

    private static final String TREE_VISITOR = "org.openrewrite.TreeVisitor";
    private static final String RECIPE = "org.openrewrite.Recipe";

    private static final List<String> STATELESS_VISITORS = Arrays.asList(
            "org.openrewrite.java.search.UsesType",
            "org.openrewrite.java.search.UsesJavaVersion"
    );

    private static final List<String> CONTAINERS = Arrays.asList(
            "java.lang.Iterable",
            "java.util.Map",
            "java.util.Optional"
    );

    private static final List<MethodMatcher> PRECONDITION_COMBINATORS = Arrays.asList(
            new MethodMatcher("org.openrewrite.Preconditions or(..)"),
            new MethodMatcher("org.openrewrite.Preconditions and(..)"),
            new MethodMatcher("org.openrewrite.Preconditions not(..)")
    );

    private static final MethodMatcher STREAM_MAP = new MethodMatcher("java.util.stream.Stream map(..)");
    private static final MethodMatcher STREAM_TO_ARRAY = new MethodMatcher("java.util.stream.Stream toArray(..)");

    @Getter
    final String displayName = "Find visitor instances shared across `getVisitor()` calls";

    @Getter
    final String description = "Find `static` fields, and instance fields of `Recipe` classes, holding `TreeVisitor` instances. " +
            "`getVisitor()` is called once per source file and source files may be visited concurrently, " +
            "so a shared visitor that walks the tree has its cursor, visit count and after-visit list corrupted by concurrent visits. " +
            "Construct such visitors inside `getVisitor()` instead. " +
            "Fields holding only visitors which keep no per-visit state, such as `UsesType` combined through `Preconditions.or/and/not`, are not reported.";

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
                            if (holdsVisitor(variable.getType(), newSetFromMap(new IdentityHashMap<>())) &&
                                    !onlyAssignedStatelessVisitors(variable, classDecl)) {
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

    private static boolean onlyAssignedStatelessVisitors(J.VariableDeclarations.NamedVariable variable, J.ClassDeclaration classDecl) {
        List<Expression> values = new ArrayList<>();
        if (variable.getInitializer() != null) {
            values.add(variable.getInitializer());
        }
        JavaType.Variable fieldType = variable.getVariableType();
        if (fieldType != null) {
            new JavaIsoVisitor<List<Expression>>() {
                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, List<Expression> assigned) {
                    JavaType.Variable target = assignment.getVariable() instanceof J.Identifier ?
                            ((J.Identifier) assignment.getVariable()).getFieldType() :
                            assignment.getVariable() instanceof J.FieldAccess ?
                                    ((J.FieldAccess) assignment.getVariable()).getName().getFieldType() :
                                    null;
                    if (target != null && target.getName().equals(fieldType.getName()) &&
                            TypeUtils.isOfType(target.getOwner(), fieldType.getOwner())) {
                        assigned.add(assignment.getAssignment());
                    }
                    return super.visitAssignment(assignment, assigned);
                }
            }.visit(classDecl.getBody(), values);
        }
        if (values.isEmpty()) {
            return false;
        }
        for (Expression value : values) {
            if (!isStatelessVisitor(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStatelessVisitor(Expression expression) {
        Expression e = expression.unwrap();
        if (e instanceof J.TypeCast) {
            return isStatelessVisitor(((J.TypeCast) e).getExpression());
        }
        if (e instanceof J.NewClass) {
            J.NewClass newClass = (J.NewClass) e;
            return newClass.getBody() == null && isStatelessVisitorType(newClass.getType());
        }
        if (e instanceof J.MethodInvocation) {
            J.MethodInvocation method = (J.MethodInvocation) e;
            if (STREAM_TO_ARRAY.matches(method) && method.getSelect() instanceof J.MethodInvocation) {
                J.MethodInvocation map = (J.MethodInvocation) method.getSelect();
                return STREAM_MAP.matches(map) && createsStatelessVisitor(map.getArguments().get(0));
            }
            for (MethodMatcher combinator : PRECONDITION_COMBINATORS) {
                if (combinator.matches(method)) {
                    return allStatelessVisitors(method.getArguments());
                }
            }
            return false;
        }
        if (e instanceof J.NewArray) {
            List<Expression> initializer = ((J.NewArray) e).getInitializer();
            return initializer != null && allStatelessVisitors(initializer);
        }
        return false;
    }

    private static boolean createsStatelessVisitor(Expression mapper) {
        if (mapper instanceof J.MemberReference) {
            J.MemberReference reference = (J.MemberReference) mapper;
            return "new".equals(reference.getReference().getSimpleName()) &&
                    isStatelessVisitorType(reference.getContaining().getType());
        }
        if (mapper instanceof J.Lambda) {
            J body = ((J.Lambda) mapper).getBody();
            if (body instanceof J.Block && ((J.Block) body).getStatements().size() == 1 &&
                    ((J.Block) body).getStatements().get(0) instanceof J.Return) {
                body = ((J.Return) ((J.Block) body).getStatements().get(0)).getExpression();
            }
            return body instanceof Expression && isStatelessVisitor((Expression) body);
        }
        return false;
    }

    private static boolean isStatelessVisitorType(@Nullable JavaType type) {
        for (String statelessVisitor : STATELESS_VISITORS) {
            if (TypeUtils.isOfClassType(type, statelessVisitor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allStatelessVisitors(List<Expression> expressions) {
        for (Expression expression : expressions) {
            if (!(expression instanceof J.Empty) && !isStatelessVisitor(expression)) {
                return false;
            }
        }
        return true;
    }
}
