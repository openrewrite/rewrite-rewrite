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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

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
                if (!TypeUtils.isAssignableTo("org.openrewrite.test.RewriteTest", classDecl.getType())) {
                    return cd;
                }

                boolean hasDefaultsMethod = classDecl.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(md -> "defaults".equals(md.getSimpleName()));
                if (hasDefaultsMethod) {
                    return classDecl;
                }

                List<RecipeSpecInfo> specInfos = collectRecipeSpecs(classDecl);
                if (specInfos.isEmpty() || !allSpecsAreIdentical(specInfos)) {
                    return classDecl;
                }

                RecipeSpecInfo commonSpec = specInfos.get(0);
                if (commonSpec.lambda == null && commonSpec.methodRef == null) {
                    return classDecl;
                }

                cd = addDefaultsMethod(classDecl, commonSpec, ctx);
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
                    return specs.size() == 1;
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
                // Both have no spec
                if (spec1.lambda == null && spec1.methodRef == null &&
                        spec2.lambda == null && spec2.methodRef == null) {
                    return true;
                }
                // Both have lambdas
                if (spec1.lambda != null && spec2.lambda != null) {
                    return areLambdasIdentical(spec1.lambda, spec2.lambda);
                }
                // Both have method refs
                if (spec1.methodRef != null && spec2.methodRef != null) {
                    return areMethodRefsIdentical(spec1.methodRef, spec2.methodRef);
                }
                // One has spec, other doesn't, or different types
                return false;
            }

            private boolean areLambdasIdentical(J.Lambda lambda1, J.Lambda lambda2) {
                if (lambda1.getBody() instanceof J.Block && lambda2.getBody() instanceof J.Block) {
                    J.Block block1 = (J.Block) lambda1.getBody();
                    J.Block block2 = (J.Block) lambda2.getBody();

                    if (block1.getStatements().size() != block2.getStatements().size()) {
                        return false;
                    }

                    for (int i = 0; i < block1.getStatements().size(); i++) {
                        Statement stmt1 = block1.getStatements().get(i);
                        Statement stmt2 = block2.getStatements().get(i);

                        String print1 = stmt1.print(getCursor()).trim();
                        String print2 = stmt2.print(getCursor()).trim();

                        if (!normalizeStatement(print1).equals(normalizeStatement(print2))) {
                            return false;
                        }
                    }
                    return true;
                }
                if (lambda1.getBody() instanceof Expression && lambda2.getBody() instanceof Expression) {
                    String print1 = lambda1.getBody().print(getCursor()).trim();
                    String print2 = lambda2.getBody().print(getCursor()).trim();
                    return normalizeStatement(print1).equals(normalizeStatement(print2));
                }
                return false;
            }

            private boolean areMethodRefsIdentical(J.MemberReference ref1, J.MemberReference ref2) {
                String methodName1 = ref1.getReference().getSimpleName();
                String methodName2 = ref2.getReference().getSimpleName();
                return methodName1.equals(methodName2);
            }

            private String normalizeStatement(String stmt) {
                return stmt.replaceAll("\\s+", " ").trim();
            }

            private J.ClassDeclaration addDefaultsMethod(J.ClassDeclaration cd, RecipeSpecInfo specInfo, ExecutionContext ctx) {
                String methodBody;
                if (specInfo.lambda != null) {
                    methodBody = extractLambdaBody(specInfo.lambda);
                } else if (specInfo.methodRef != null) {
                    methodBody = specInfo.methodRef.getReference().getSimpleName() + "(spec);";
                } else {
                    return cd;
                }

                // Build a JavaTemplate for the defaults method
                JavaTemplate template = JavaTemplate.builder(
                                "@Override\n" +
                                        "public void defaults(RecipeSpec spec) {\n" +
                                        "    " + methodBody + "\n" +
                                        "}")
                        .contextSensitive()
                        .imports("org.openrewrite.test.RecipeSpec")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpath(JavaParser.runtimeClasspath()))
                        .build();

                // Apply the template to add the defaults method
                J.ClassDeclaration updated = template.apply(
                        new Cursor(getCursor(), cd),
                        cd.getBody().getCoordinates().firstStatement()
                );

                // Fix the formatting by adjusting the defaults method prefix
                if (updated != cd) {
                    List<Statement> statements = new ArrayList<>();
                    for (Statement stmt : updated.getBody().getStatements()) {
                        if (stmt instanceof J.MethodDeclaration &&
                                "defaults".equals(((J.MethodDeclaration) stmt).getSimpleName())) {
                            // Ensure proper newline before @Override
                            statements.add(stmt.withPrefix(Space.format("\n    ")));
                        } else {
                            statements.add(stmt);
                        }
                    }
                    cd = updated.withBody(updated.getBody().withStatements(statements));
                } else {
                    cd = updated;
                }

                // Ensure proper formatting of the first test method after defaults
                List<Statement> statements = new ArrayList<>();
                boolean foundDefaults = false;
                for (Statement stmt : cd.getBody().getStatements()) {
                    if (!foundDefaults && stmt instanceof J.MethodDeclaration &&
                            "defaults".equals(((J.MethodDeclaration) stmt).getSimpleName())) {
                        foundDefaults = true;
                        statements.add(stmt);
                    } else if (foundDefaults && stmt instanceof J.MethodDeclaration) {
                        // Add blank line before first test method after defaults
                        statements.add(stmt.withPrefix(Space.format("\n\n    ")));
                        foundDefaults = false; // Reset flag so we don't modify other methods
                    } else {
                        statements.add(stmt);
                    }
                }

                return cd.withBody(cd.getBody().withStatements(statements));
            }

            private String extractLambdaBody(J.Lambda lambda) {
                if (lambda.getBody() instanceof J.Block) {
                    J.Block block = (J.Block) lambda.getBody();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < block.getStatements().size(); i++) {
                        Statement stmt = block.getStatements().get(i);
                        String stmtStr = stmt.print(getCursor());

                        // Process each line to adjust indentation
                        String[] lines = stmtStr.split("\n");
                        for (int j = 0; j < lines.length; j++) {
                            String line = lines[j];

                            if (j == 0) {
                                // First line - trim completely
                                line = line.trim();

                                if (stmt instanceof J.Return) {
                                    // Remove "return" keyword for returns
                                    if (line.startsWith("return ")) {
                                        line = line.substring(7);
                                    }
                                    if (line.endsWith(";")) {
                                        line = line.substring(0, line.length() - 1);
                                    }
                                }
                            } else {
                                // Continuation lines - align properly under the chained method calls
                                line = line.trim();
                                if (!line.isEmpty()) {
                                    // Use alignment that matches the expected format
                                    line = "        " + line;  // This will be placed inside method body, JavaTemplate adds 8 more
                                }
                            }

                            sb.append(line);
                            if (j < lines.length - 1) {
                                sb.append("\n");
                            }
                        }

                        if (!stmtStr.trim().endsWith(";")) {
                            sb.append(";");
                        }

                        if (i < block.getStatements().size() - 1) {
                            sb.append("\n        ");
                        }
                    }
                    return sb.toString();
                }
                if (lambda.getBody() instanceof Expression) {
                    String bodyStr = lambda.getBody().print(getCursor()).trim();
                    if (!bodyStr.endsWith(";")) {
                        bodyStr += ";";
                    }
                    return bodyStr;
                }
                return "";
            }

            private J.ClassDeclaration removeSpecsFromRewriteRuns(J.ClassDeclaration cd, ExecutionContext ctx) {
                return (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (rewriteRunMatcher.matches(mi) && !mi.getArguments().isEmpty()) {
                            Expression firstArg = mi.getArguments().get(0);
                            if (firstArg instanceof J.Lambda || firstArg instanceof J.MemberReference) {
                                List<Expression> newArgs = new ArrayList<>(mi.getArguments());
                                newArgs.remove(0);
                                return mi.withArguments(newArgs);
                            }
                        }

                        return mi;
                    }
                }.visitNonNull(cd, ctx);
            }

            @Value
            class RecipeSpecInfo {
                J.@Nullable Lambda lambda;
                J.@Nullable MemberReference methodRef;
            }
        });
    }
}
