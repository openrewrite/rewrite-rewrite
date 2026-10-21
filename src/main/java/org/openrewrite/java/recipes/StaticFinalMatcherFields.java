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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class StaticFinalMatcherFields extends Recipe {

    private static final List<String> MATCHER_TYPES = asList(
            "org.openrewrite.java.MethodMatcher",
            "org.openrewrite.java.AnnotationMatcher",
            "org.openrewrite.java.TypeMatcher");

    private static final List<AnnotationMatcher> LOMBOK_ACCESSOR_ANNOTATIONS = asList(
            new AnnotationMatcher("@lombok.Value"),
            new AnnotationMatcher("@lombok.Data"),
            new AnnotationMatcher("@lombok.Getter"),
            new AnnotationMatcher("@lombok.Setter"));

    @Getter
    final String displayName = "Make matcher fields `static final` constants";

    @Getter
    final String description = "Instance fields of recipes and visitors holding a `MethodMatcher`, `AnnotationMatcher` or " +
            "`TypeMatcher` built only from constants are recreated, and their pattern parsed again, for every instance. " +
            "Make such `private final` fields `private static final` constants with an upper snake case name.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.openrewrite.java.*Matcher", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        JavaType.FullyQualified classType = cd.getType();
                        if (classType == null || cd.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                                !(getCursor().getParentTreeCursor().getValue() instanceof JavaSourceFile || cd.hasModifier(J.Modifier.Type.Static)) ||
                                !(TypeUtils.isAssignableTo("org.openrewrite.Recipe", classType) ||
                                        TypeUtils.isAssignableTo("org.openrewrite.TreeVisitor", classType)) ||
                                LOMBOK_ACCESSOR_ANNOTATIONS.stream().anyMatch(m -> service(AnnotationService.class).matches(getCursor(), m))) {
                            return cd;
                        }

                        Set<String> usedNames = new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                                names.add(identifier.getSimpleName());
                                return identifier;
                            }
                        }.reduce(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), new HashSet<>());
                        Set<String> initializedStaticFields = new HashSet<>();
                        return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                            if (!(statement instanceof J.VariableDeclarations)) {
                                return statement;
                            }
                            J.VariableDeclarations field = (J.VariableDeclarations) statement;
                            if (field.hasModifier(J.Modifier.Type.Static)) {
                                field.getVariables().forEach(v -> initializedStaticFields.add(v.getSimpleName()));
                                return field;
                            }
                            J.VariableDeclarations.NamedVariable variable = field.getVariables().get(0);
                            Expression initializer = variable.getInitializer();
                            String constantName = variable.getSimpleName()
                                    .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                                    .toUpperCase();
                            if (field.getVariables().size() != 1 || !field.getLeadingAnnotations().isEmpty() ||
                                    !field.hasModifier(J.Modifier.Type.Private) || !field.hasModifier(J.Modifier.Type.Final) ||
                                    !field.getModifiers().stream().allMatch(m -> m.getType() == J.Modifier.Type.Private ||
                                            m.getType() == J.Modifier.Type.Final || m.getType() == J.Modifier.Type.Transient) ||
                                    MATCHER_TYPES.stream().noneMatch(t -> TypeUtils.isOfClassType(field.getType(), t)) ||
                                    !(initializer instanceof J.NewClass) || ((J.NewClass) initializer).getBody() != null ||
                                    !((J.NewClass) initializer).getArguments().stream().allMatch(arg ->
                                            isConstant(arg, classType, initializedStaticFields)) ||
                                    !constantName.equals(variable.getSimpleName()) && usedNames.contains(constantName)) {
                                return field;
                            }

                            initializedStaticFields.add(constantName);
                            if (!constantName.equals(variable.getSimpleName())) {
                                doAfterVisit(new RenameVariable<>(variable, constantName));
                            }
                            J.Modifier first = field.getModifiers().get(0);
                            return field.withModifiers(asList(
                                    new J.Modifier(randomId(), first.getPrefix(), Markers.EMPTY, null, J.Modifier.Type.Private, emptyList()),
                                    new J.Modifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, null, J.Modifier.Type.Static, emptyList()),
                                    new J.Modifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList())));
                        })));
                    }
                });
    }

    private static boolean isConstant(@Nullable Expression expression, JavaType.FullyQualified classType, Set<String> initializedStaticFields) {
        if (expression instanceof J.Literal || expression instanceof J.Empty) {
            return true;
        }
        if (expression instanceof J.Parentheses) {
            J tree = ((J.Parentheses<?>) expression).getTree();
            return tree instanceof Expression && isConstant((Expression) tree, classType, initializedStaticFields);
        }
        if (expression instanceof J.Binary) {
            J.Binary binary = (J.Binary) expression;
            return isConstant(binary.getLeft(), classType, initializedStaticFields) &&
                    isConstant(binary.getRight(), classType, initializedStaticFields);
        }
        JavaType.Variable field = null;
        if (expression instanceof J.Identifier) {
            field = ((J.Identifier) expression).getFieldType();
        } else if (expression instanceof J.FieldAccess) {
            Expression target = ((J.FieldAccess) expression).getTarget();
            boolean typeReference = target instanceof J.Identifier ? ((J.Identifier) target).getFieldType() == null :
                    target instanceof J.FieldAccess && ((J.FieldAccess) target).getName().getFieldType() == null;
            field = typeReference ? ((J.FieldAccess) expression).getName().getFieldType() : null;
        }
        return field != null && field.hasFlags(Flag.Static) &&
                (!TypeUtils.isOfType(field.getOwner(), classType) || initializedStaticFields.contains(field.getName()));
    }
}
