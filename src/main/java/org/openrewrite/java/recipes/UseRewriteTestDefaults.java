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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Objects.requireNonNull;

public class UseRewriteTestDefaults extends Recipe {

    @Getter
    final String displayName = "Refactor RewriteTest to use defaults method";

    @Getter
    final String description = "When all `rewriteRun` methods in a test class use the same RecipeSpec configuration, " +
            "refactor to use the `defaults` method instead. When the class already has a `defaults` method, " +
            "remove the recipe that a test sets when `defaults` already sets that same recipe.";

    private static final Set<String> RECIPE_SETTERS = new HashSet<>(Arrays.asList(
            "recipe", "recipes", "recipeFromYaml", "recipeFromResource", "recipeFromResources"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.openrewrite.test.RewriteTest", false), new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher rewriteRunMatcher = new MethodMatcher("org.openrewrite.test.RewriteTest rewriteRun(..)");

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (!TypeUtils.isAssignableTo("org.openrewrite.test.RewriteTest", cd.getType())) {
                    return cd;
                }

                J.MethodDeclaration defaults = cd.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(md -> "defaults".equals(md.getSimpleName()))
                        .findFirst()
                        .orElse(null);
                if (defaults != null) {
                    J.MethodInvocation defaultRecipe = recipeSetByDefaults(defaults);
                    return defaultRecipe == null ? cd : removeRepeatedRecipe(cd, defaultRecipe, ctx);
                }

                List<RecipeSpecInfo> specInfos = collectRecipeSpecs(cd);
                if (!allSpecsAreIdentical(specInfos)) {
                    return cd;
                }
                cd = newlineBeforeFirstStatement(cd);
                cd = addDefaultsMethod(cd, specInfos.get(0));
                return removeSpecsFromRewriteRuns(cd, ctx);
            }

            private List<RecipeSpecInfo> collectRecipeSpecs(J.ClassDeclaration cd) {
                return new JavaIsoVisitor<List<RecipeSpecInfo>>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, List<RecipeSpecInfo> recipeSpecInfos) {
                        return classDecl; // Ignore nested classes
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, List<RecipeSpecInfo> recipeSpecInfos) {
                        return newClass; // Ignore nested classes
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<RecipeSpecInfo> specs) {
                        if (rewriteRunMatcher.matches(method)) {
                            if (!method.getArguments().isEmpty()) {
                                Expression firstArg = method.getArguments().get(0);
                                if (firstArg instanceof J.Lambda) {
                                    specs.add(new RecipeSpecInfo((J.Lambda) firstArg, null));
                                } else if (firstArg instanceof J.MemberReference) {
                                    specs.add(new RecipeSpecInfo(null, (J.MemberReference) firstArg));
                                } else {
                                    // This rewriteRun has no spec lambda/method ref as first arg
                                    specs.add(new RecipeSpecInfo(null, null));
                                }
                            } else {
                                // This rewriteRun has no arguments at all
                                specs.add(new RecipeSpecInfo(null, null));
                            }
                        }
                        return super.visitMethodInvocation(method, specs);
                    }
                }.reduce(cd.getBody(), new ArrayList<>());
            }

            private boolean allSpecsAreIdentical(List<RecipeSpecInfo> specs) {
                if (specs.size() < 2) {
                    return false; // At least two specs are needed before we extract defaults
                }

                RecipeSpecInfo first = specs.get(0);
                for (int i = 1; i < specs.size(); i++) {
                    if (!areSpecsIdentical(first, specs.get(i))) {
                        return false;
                    }
                }
                return true;
            }

            private boolean areSpecsIdentical(RecipeSpecInfo spec1, RecipeSpecInfo spec2) {
                if (spec1.lambda == null && spec2.lambda == null) {
                    return spec1.methodRef != null && SemanticallyEqual.areEqual(spec1.methodRef, spec2.methodRef);
                }
                if (spec1.methodRef == null && spec2.methodRef == null) {
                    return spec1.lambda != null && SemanticallyEqual.areEqual(spec1.lambda, spec2.lambda);
                }
                return false;
            }

            private J.ClassDeclaration newlineBeforeFirstStatement(J.ClassDeclaration cd) {
                return cd.withBody(cd.getBody().withStatements(ListUtils.mapFirst(cd.getBody().getStatements(),
                        first -> first.withPrefix(first.getPrefix().withWhitespace(
                                "\n\n" + first.getPrefix().getWhitespace().replaceFirst("^\\n+", ""))))));
            }

            private J.ClassDeclaration addDefaultsMethod(J.ClassDeclaration cd, RecipeSpecInfo specInfo) {
                if (specInfo.lambda != null) {
                    maybeAddImport("org.openrewrite.test.RecipeSpec", false);
                    return JavaTemplate.builder(
                                    "@Override\n" +
                                            "public void defaults(RecipeSpec spec) {\n" +
                                            "    #{any()}\n" +
                                            "}")
                            .imports("org.openrewrite.test.RecipeSpec")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build()
                            .apply(
                                    new Cursor(getCursor().getParentOrThrow(), cd),
                                    cd.getBody().getCoordinates().firstStatement(),
                                    specInfo.lambda.getBody());
                }
                if (specInfo.methodRef != null) {
                    maybeAddImport("org.openrewrite.test.RecipeSpec", false);
                    String simpleName = specInfo.methodRef.getReference().getSimpleName();
                    return JavaTemplate.builder(
                                    "@Override\n" +
                                            "public void defaults(RecipeSpec spec) {\n    " +
                                            simpleName + "(spec);\n" +
                                            "}")
                            .contextSensitive()
                            .imports("org.openrewrite.test.RecipeSpec")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build()
                            .apply(
                                    new Cursor(getCursor().getParentOrThrow(), cd),
                                    cd.getBody().getCoordinates().firstStatement());
                }
                return cd;
            }

            private J.ClassDeclaration removeSpecsFromRewriteRuns(J.ClassDeclaration cd, ExecutionContext ctx) {
                J.Block body = (J.Block) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        return classDecl; // Ignore nested classes
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        return newClass; // Ignore nested classes
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (rewriteRunMatcher.matches(mi)) {
                            return mi.withArguments(ListUtils.mapFirst(mi.getArguments(),
                                    firstArg -> firstArg instanceof J.Lambda || firstArg instanceof J.MemberReference ? null : firstArg));
                        }
                        return mi;
                    }
                }.visitNonNull(cd.getBody(), ctx);
                return cd.withBody(body);
            }

            private J.@Nullable MethodInvocation recipeSetByDefaults(J.MethodDeclaration defaults) {
                if (defaults.getBody() == null) {
                    return null;
                }
                List<J.MethodInvocation> recipeSetters = new ArrayList<>();
                for (Statement statement : defaults.getBody().getStatements()) {
                    for (Expression e = statement instanceof J.MethodInvocation ? (J.MethodInvocation) statement : null;
                         e instanceof J.MethodInvocation; e = ((J.MethodInvocation) e).getSelect()) {
                        if (isRecipeSetter((J.MethodInvocation) e)) {
                            recipeSetters.add((J.MethodInvocation) e);
                        }
                    }
                }
                return recipeSetters.size() == 1 ? recipeSetters.get(0) : null;
            }

            private boolean isRecipeSetter(J.MethodInvocation method) {
                return RECIPE_SETTERS.contains(method.getSimpleName()) &&
                       method.getMethodType() != null &&
                       TypeUtils.isOfClassType(method.getMethodType().getDeclaringType(), "org.openrewrite.test.RecipeSpec");
            }

            private boolean sameRecipe(J.MethodInvocation setter, J.MethodInvocation defaultRecipe) {
                if (!setter.getSimpleName().equals(defaultRecipe.getSimpleName()) ||
                    setter.getArguments().size() != defaultRecipe.getArguments().size()) {
                    return false;
                }
                for (int i = 0; i < setter.getArguments().size(); i++) {
                    if (!SemanticallyEqual.areEqual(setter.getArguments().get(i), defaultRecipe.getArguments().get(i))) {
                        return false;
                    }
                }
                return true;
            }

            private J.ClassDeclaration removeRepeatedRecipe(J.ClassDeclaration cd, J.MethodInvocation defaultRecipe, ExecutionContext ctx) {
                J.Block body = (J.Block) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        return classDecl; // Ignore nested classes
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        return newClass; // Ignore nested classes
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!rewriteRunMatcher.matches(mi) || mi.getArguments().size() < 2 ||
                            !(mi.getArguments().get(0) instanceof J.Lambda)) {
                            return mi;
                        }
                        J.Lambda lambda = (J.Lambda) mi.getArguments().get(0);
                        J withoutRecipe = lambdaBodyWithoutRecipe(lambda.getBody());
                        if (withoutRecipe == lambda.getBody()) {
                            return mi;
                        }
                        if (withoutRecipe == null) {
                            return mi.withArguments(ListUtils.mapFirst(mi.getArguments(), firstArg -> null));
                        }
                        return mi.withArguments(ListUtils.mapFirst(mi.getArguments(), firstArg -> lambda.withBody(withoutRecipe)));
                    }

                    private @Nullable J lambdaBodyWithoutRecipe(J lambdaBody) {
                        if (lambdaBody instanceof J.Block) {
                            J.Block block = (J.Block) lambdaBody;
                            J.Block newBlock = block.withStatements(ListUtils.map(block.getStatements(), statement -> {
                                if (statement instanceof J.MethodInvocation) {
                                    Expression call = chainWithoutRecipe((J.MethodInvocation) statement);
                                    return call instanceof J.MethodInvocation ? (J.MethodInvocation) call : null;
                                }
                                return statement;
                            }));
                            return newBlock.getStatements().isEmpty() ? null : newBlock;
                        }
                        if (lambdaBody instanceof J.MethodInvocation) {
                            Expression call = chainWithoutRecipe((J.MethodInvocation) lambdaBody);
                            return call instanceof J.MethodInvocation ? call : null;
                        }
                        return lambdaBody;
                    }

                    private Expression chainWithoutRecipe(Expression expression) {
                        if (!(expression instanceof J.MethodInvocation) || ((J.MethodInvocation) expression).getSelect() == null) {
                            return expression;
                        }
                        J.MethodInvocation mi = (J.MethodInvocation) expression;
                        Expression select = chainWithoutRecipe(requireNonNull(mi.getSelect()));
                        if (isRecipeSetter(mi) && sameRecipe(mi, defaultRecipe)) {
                            return select.withPrefix(mi.getPrefix());
                        }
                        if (select == mi.getSelect()) {
                            return mi;
                        }
                        JRightPadded<Expression> paddedSelect = requireNonNull(mi.getPadding().getSelect()).withElement(select);
                        return mi.getPadding().withSelect(select instanceof J.MethodInvocation ? paddedSelect : paddedSelect.withAfter(Space.EMPTY));
                    }
                }.visitNonNull(cd.getBody(), ctx);
                return cd.withBody(body);
            }

            @Value
            class RecipeSpecInfo {
                J.@Nullable Lambda lambda;
                J.@Nullable MemberReference methodRef;
            }
        });
    }
}
