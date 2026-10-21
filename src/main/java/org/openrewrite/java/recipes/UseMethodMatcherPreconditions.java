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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class UseMethodMatcherPreconditions extends Recipe {

    private static final MethodMatcher USES_METHOD_PATTERN =
            new MethodMatcher("org.openrewrite.java.search.UsesMethod <constructor>(String, ..)");
    private static final MethodMatcher DECLARES_METHOD_PATTERN =
            new MethodMatcher("org.openrewrite.java.search.DeclaresMethod <constructor>(String, ..)");
    private static final MethodMatcher METHOD_MATCHER_PATTERN =
            new MethodMatcher("org.openrewrite.java.MethodMatcher <constructor>(String, ..)");
    private static final MethodMatcher FIND_METHODS =
            new MethodMatcher("org.openrewrite.java.search.FindMethods <constructor>(String, Boolean)");
    private static final MethodMatcher FIND_METHOD_DECLARATION =
            new MethodMatcher("org.openrewrite.java.search.FindMethodDeclaration <constructor>(String, Boolean)");
    private static final MethodMatcher GET_VISITOR =
            new MethodMatcher("org.openrewrite.Recipe getVisitor()", true);
    private static final MethodMatcher PRECONDITIONS_CHECK =
            new MethodMatcher("org.openrewrite.Preconditions check(..)");
    private static final MethodMatcher PRECONDITIONS_COMBINATOR =
            new MethodMatcher("org.openrewrite.Preconditions *(..)");

    @Getter
    final String displayName = "Use `UsesMethod` and `DeclaresMethod` preconditions with `MethodMatcher`s";

    @Getter
    final String description = "Pass an existing `static final` `MethodMatcher` field to `UsesMethod` and `DeclaresMethod` " +
            "instead of repeating its method pattern and `matchOverrides` flag. Also replace " +
            "`new FindMethods(..).getVisitor()` and `new FindMethodDeclaration(..).getVisitor()` preconditions " +
            "with `UsesMethod` and `DeclaresMethod`, which only consult the types in use of a source file.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.openrewrite.java.search.UsesMethod", false),
                        new UsesType<>("org.openrewrite.java.search.DeclaresMethod", false),
                        new UsesType<>("org.openrewrite.java.search.FindMethods", false),
                        new UsesType<>("org.openrewrite.java.search.FindMethodDeclaration", false)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = method;
                        if (PRECONDITIONS_CHECK.matches(m)) {
                            m = m.withArguments(ListUtils.mapFirst(m.getArguments(), arg -> searchVisitor(arg, ctx)));
                        } else if (PRECONDITIONS_COMBINATOR.matches(m)) {
                            m = m.withArguments(ListUtils.map(m.getArguments(), arg -> searchVisitor(arg, ctx)));
                        }
                        return super.visitMethodInvocation(m, ctx);
                    }

                    private Expression searchVisitor(Expression arg, ExecutionContext ctx) {
                        if (!GET_VISITOR.matches(arg) || !(((J.MethodInvocation) arg).getSelect() instanceof J.NewClass)) {
                            return arg;
                        }
                        J.NewClass recipe = (J.NewClass) ((J.MethodInvocation) arg).getSelect();
                        String visitor;
                        if (FIND_METHODS.matches(recipe)) {
                            maybeRemoveImport("org.openrewrite.java.search.FindMethods");
                            visitor = "UsesMethod";
                        } else if (FIND_METHOD_DECLARATION.matches(recipe)) {
                            maybeRemoveImport("org.openrewrite.java.search.FindMethodDeclaration");
                            visitor = "DeclaresMethod";
                        } else {
                            return arg;
                        }
                        maybeAddImport("org.openrewrite.java.search." + visitor);
                        Expression matchOverrides = recipe.getArguments().get(1);
                        String matchOverridesType = matchOverrides.getType() == JavaType.Primitive.Boolean ? "boolean" : "java.lang.Boolean";
                        return JavaTemplate.builder("new " + visitor + "<>(#{any(java.lang.String)}, #{any(" + matchOverridesType + ")})")
                                .imports("org.openrewrite.java.search." + visitor)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "rewrite-core", "rewrite-java"))
                                .build()
                                .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(),
                                        recipe.getArguments().get(0), matchOverrides);
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                        if (!USES_METHOD_PATTERN.matches(n) && !DECLARES_METHOD_PATTERN.matches(n)) {
                            return n;
                        }
                        String pattern = pattern(n.getArguments().get(0));
                        Boolean matchOverrides = matchOverrides(n.getArguments());
                        if (pattern == null || matchOverrides == null) {
                            return n;
                        }
                        for (J.VariableDeclarations.NamedVariable field : staticFinalFields()) {
                            if (METHOD_MATCHER_PATTERN.matches(field.getInitializer())) {
                                List<Expression> matcherArgs = ((J.NewClass) field.getInitializer()).getArguments();
                                if (pattern.equals(pattern(matcherArgs.get(0))) && matchOverrides.equals(matchOverrides(matcherArgs))) {
                                    J.Identifier matcher = field.getName().withId(randomId()).withPrefix(n.getArguments().get(0).getPrefix());
                                    JavaType.Method constructor = n.getConstructorType();
                                    return n.withArguments(singletonList(matcher))
                                            .withConstructorType(constructor == null ? null : constructor
                                                    .withParameterNames(singletonList("methodMatcher"))
                                                    .withParameterTypes(singletonList(matcher.getType())));
                                }
                            }
                        }
                        return n;
                    }

                    private @Nullable String pattern(Expression expression) {
                        if (expression instanceof J.Literal) {
                            Object value = ((J.Literal) expression).getValue();
                            return value instanceof String ? (String) value : null;
                        }
                        JavaType.Variable constant = expression instanceof J.Identifier ? ((J.Identifier) expression).getFieldType() : null;
                        if (constant == null) {
                            return null;
                        }
                        for (J.VariableDeclarations.NamedVariable field : staticFinalFields()) {
                            if (field.getSimpleName().equals(constant.getName()) &&
                                    field.getVariableType() != null &&
                                    TypeUtils.isOfType(field.getVariableType().getOwner(), constant.getOwner()) &&
                                    field.getInitializer() instanceof J.Literal) {
                                return pattern(field.getInitializer());
                            }
                        }
                        return null;
                    }

                    private @Nullable Boolean matchOverrides(List<Expression> args) {
                        if (args.size() == 1) {
                            return false;
                        }
                        return args.get(1) instanceof J.Literal ? Boolean.TRUE.equals(((J.Literal) args.get(1)).getValue()) : null;
                    }

                    private List<J.VariableDeclarations.NamedVariable> staticFinalFields() {
                        return getCursor().getPathAsStream()
                                .filter(J.ClassDeclaration.class::isInstance)
                                .flatMap(classDecl -> ((J.ClassDeclaration) classDecl).getBody().getStatements().stream())
                                .filter(J.VariableDeclarations.class::isInstance)
                                .map(J.VariableDeclarations.class::cast)
                                .filter(field -> field.hasModifier(J.Modifier.Type.Static) && field.hasModifier(J.Modifier.Type.Final))
                                .flatMap(field -> field.getVariables().stream())
                                .collect(toList());
                    }
                }
        );
    }
}
