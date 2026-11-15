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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.internal.template.Substitutions;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

@EqualsAndHashCode(callSuper = false)
@Value
public class UseDataTableRowsByClass extends Recipe {

    private static final MethodMatcher GET_ROWS_BY_NAME = new MethodMatcher("org.openrewrite.RecipeRun getDataTableRows(java.lang.String, ..)");
    private static final MethodMatcher GET_ROWS_BY_CLASS = new MethodMatcher("org.openrewrite.RecipeRun getDataTableRows(java.lang.Class)");
    private static final MethodMatcher GET_ROWS_BY_CLASS_AND_GROUP = new MethodMatcher("org.openrewrite.RecipeRun getDataTableRows(java.lang.Class, java.lang.String)");
    private static final MethodMatcher CLASS_GET_NAME = new MethodMatcher("java.lang.Class getName()");
    // Leaves out nested classes, named with a `$`, whose class literal would need their outer class
    private static final Pattern TOP_LEVEL_CLASS_NAME = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}[\\p{javaJavaIdentifierPart}&&[^$]]*");

    String displayName = "Get data table rows by class";

    String description = "Replace calls to the deprecated `RecipeRun.getDataTableRows(String)` and `RecipeRun.getDataTableRows(String, String)` " +
            "with their type-safe `getDataTableRows(Class)` and `getDataTableRows(Class, String)` counterparts, " +
            "where the data table name is a class name literal or `DataTable.class.getName()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_ROWS_BY_NAME), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!GET_ROWS_BY_NAME.matches(mi)) {
                    return mi;
                }
                JavaType.Method byName = requireNonNull(mi.getMethodType());
                List<Expression> arguments = mi.getArguments();
                boolean defaultGroup = arguments.size() == 2 && J.Literal.isLiteralValue(arguments.get(1), null);
                JavaType.Method generic = overloadTakingClass(byName.getDeclaringType(),
                        defaultGroup || arguments.size() == 1 ? GET_ROWS_BY_CLASS : GET_ROWS_BY_CLASS_AND_GROUP);
                if (generic == null) {
                    return mi;
                }
                JavaType.FullyQualified classType = ((JavaType.Parameterized) generic.getParameterTypes().get(0)).getType();
                J.FieldAccess classLiteral = classLiteral(arguments.get(0), classType);
                if (classLiteral == null) {
                    return mi;
                }
                JavaType row = rowType(classLiteral.getTarget().getType(), byName.getReturnType());
                JavaType.Method byClass = row == null ? null : instantiate(generic, row);
                if (byClass == null) {
                    return mi;
                }

                doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(classLiteral));
                return mi
                        .withArguments(defaultGroup ?
                                singletonList(classLiteral) :
                                ListUtils.mapFirst(arguments, name -> classLiteral))
                        .withMethodType(byClass);
            }

            private J.@Nullable FieldAccess classLiteral(Expression name, JavaType.FullyQualified classType) {
                if (CLASS_GET_NAME.matches(name)) {
                    Expression select = ((J.MethodInvocation) name).getSelect();
                    if (select instanceof J.FieldAccess && "class".equals(((J.FieldAccess) select).getSimpleName())) {
                        return ((J.FieldAccess) select).withPrefix(name.getPrefix());
                    }
                } else if (name instanceof J.Literal && ((J.Literal) name).getValue() instanceof String) {
                    String fqn = (String) ((J.Literal) name).getValue();
                    if (TOP_LEVEL_CLASS_NAME.matcher(fqn).matches()) {
                        JavaType.FullyQualified dataTable = typeInUse(fqn);
                        JavaType.Parameterized classOfDataTable = new JavaType.Parameterized(null, classType, singletonList(dataTable));
                        J.FieldAccess qualifiedName = TypeTree.build(fqn);
                        return new J.FieldAccess(
                                randomId(),
                                name.getPrefix(),
                                name.getMarkers(),
                                qualifiedName.withType(dataTable).withName(qualifiedName.getName().withType(dataTable)),
                                JLeftPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", classOfDataTable, null)),
                                classOfDataTable);
                    }
                }
                return null;
            }

            private JavaType.FullyQualified typeInUse(String fqn) {
                for (JavaType type : getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getTypesInUse().getTypesInUse()) {
                    if (type instanceof JavaType.Class && fqn.equals(((JavaType.Class) type).getFullyQualifiedName())) {
                        return (JavaType.Class) type;
                    }
                }
                return JavaType.ShallowClass.build(fqn);
            }
        });
    }

    private static JavaType.@Nullable Method overloadTakingClass(JavaType.FullyQualified recipeRun, MethodMatcher overload) {
        for (JavaType.Method method : recipeRun.getMethods()) {
            if (overload.matches(method)) {
                return method;
            }
        }
        return null;
    }

    // Null when the rows go to a list of another type than the data table's, which the call by class wouldn't compile to
    private static @Nullable JavaType rowType(@Nullable JavaType dataTable, @Nullable JavaType rowsByName) {
        JavaType.Parameterized rows = TypeUtils.asParameterized(rowsByName);
        JavaType inferred = rows == null ? null : rows.getTypeParameters().get(0);
        JavaType.Parameterized dataTableOfRow = Substitutions.asSupertype(dataTable, "org.openrewrite.DataTable");
        if (dataTableOfRow == null) {
            return inferred;
        }
        JavaType declared = dataTableOfRow.getTypeParameters().get(0);
        return inferred == null || TypeUtils.isObject(inferred) || TypeUtils.isOfType(inferred, declared) ? declared : null;
    }

    // The parser gives the call the method type with the row type substituted for `E`
    private static JavaType.@Nullable Method instantiate(JavaType.Method generic, JavaType row) {
        Map<String, JavaType> rowForE = singletonMap(generic.getDeclaredFormalTypeNames().get(0), row);
        JavaType classOfDataTable = Substitutions.withArguments(generic.getParameterTypes().get(0), rowForE);
        JavaType rows = Substitutions.withArguments(requireNonNull(generic.getReturnType()), rowForE);
        if (classOfDataTable == null || rows == null) {
            return null;
        }
        return generic
                .withDeclaredFormalTypeNames(new String[0])
                .withParameterTypes(ListUtils.mapFirst(generic.getParameterTypes(), p -> classOfDataTable))
                .withReturnType(rows);
    }
}
