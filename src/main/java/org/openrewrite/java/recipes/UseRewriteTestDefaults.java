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
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class UseRewriteTestDefaults extends Recipe {

    @Override
    public String getDisplayName() {
        return "Refactor RewriteTest to use defaults method";
    }

    @Override
    public String getDescription() {
        return "When all `rewriteRun` methods in a test class use the same RecipeSpec configuration, " +
                "refactor to use the `defaults` method instead.";
    }

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

                boolean hasDefaultsMethod = cd.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(md -> "defaults".equals(md.getSimpleName()));
                if (hasDefaultsMethod) {
                    return cd;
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
                        first -> first.withPrefix(first.getPrefix().withWhitespace("\n" + first.getPrefix().getWhitespace())))));
            }

            private J.ClassDeclaration addDefaultsMethod(J.ClassDeclaration cd, RecipeSpecInfo specInfo) {
                if (specInfo.lambda != null) {
                    maybeAddImport("org.openrewrite.test.RecipeSpec", false);
                    return JavaTemplate.builder(
                                    "@Override\n" +
                                            "public void defaults(RecipeSpec spec) {\n" +
                                            "    #{any()}\n" +
                                            "}")
                            .contextSensitive()
                            .imports("org.openrewrite.test.RecipeSpec")
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build()
                            .apply(
                                    new Cursor(getCursor(), cd),
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
                                    new Cursor(getCursor(), cd),
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

            @Value
            class RecipeSpecInfo {
                J.@Nullable Lambda lambda;
                J.@Nullable MemberReference methodRef;
            }
        });
    }
}
