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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Comparator.comparing;

public class SelectRecipeExamples extends Recipe {

    private static final String DOCUMENT_EXAMPLE_ANNOTATION_FQN = "org.openrewrite.DocumentExample";
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher ISSUE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.openrewrite.Issue");
    private static final AnnotationMatcher DISABLED_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Disabled");
    private static final AnnotationMatcher NESTED_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Nested");
    private static final AnnotationMatcher DOCUMENT_EXAMPLE_ANNOTATION_MATCHER =
            new AnnotationMatcher("@" + DOCUMENT_EXAMPLE_ANNOTATION_FQN);

    private static final MethodMatcher REWRITE_RUN_METHOD_MATCHER_ALL =
            new MethodMatcher("org.openrewrite.test.RewriteTest rewriteRun(..)");

    private static final MethodMatcher REWRITE_RUN_METHOD_MATCHER_WITH_SPEC =
            new MethodMatcher("org.openrewrite.test.RewriteTest rewriteRun(java.util.function.Consumer, org.openrewrite.test.SourceSpecs[])");

    private static final String REWRITE_TEST_FQN = "org.openrewrite.test.RewriteTest";

    @Override
    public String getDisplayName() {
        return "Automatically select recipe examples from the unit test cases of a recipe";
    }

    @Override
    public String getDescription() {
        return "Add `@DocumentExample` to the first non-issue and not a disabled unit test of a recipe as an example, " +
                "if there are not any examples yet.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new UsesType<>(DOCUMENT_EXAMPLE_ANNOTATION_FQN, false)), new JavaIsoVisitor<ExecutionContext>() {
            private int selectedCount = 0;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                            ExecutionContext ctx) {
                if (classDecl.getImplements() != null && !classDecl.getImplements().isEmpty()) {
                    if (!TypeUtils.isOfClassType(classDecl.getImplements().get(0).getType(), REWRITE_TEST_FQN)) {
                        return classDecl;
                    }
                }
                selectedCount = 0;
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {
                if (selectedCount > 0) {
                    return method;
                }

                List<J.Annotation> annotations = service(AnnotationService.class).getAllAnnotations(getCursor());
                if (annotations.stream().noneMatch(TEST_ANNOTATION_MATCHER::matches) ||
                        annotations.stream().anyMatch(a ->
                                ISSUE_ANNOTATION_MATCHER.matches(a) ||
                                        DISABLED_ANNOTATION_MATCHER.matches(a) ||
                                        DOCUMENT_EXAMPLE_ANNOTATION_MATCHER.matches(a))) {
                    return method;
                }

                J.ClassDeclaration clazz = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).getValue();
                if (clazz.getLeadingAnnotations().stream().anyMatch(NESTED_ANNOTATION_MATCHER::matches)) {
                    return method;
                }

                // a good recipe example should have both a before and after changed within a rewriteRun spec
                boolean rewriteRunWithAnyChangedSource = new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean isGood) {
                        if (REWRITE_RUN_METHOD_MATCHER_ALL.matches(method)) {
                            isGood.set(containsAnyChangedSource(method));
                        }
                        return super.visitMethodInvocation(method, isGood);
                    }
                }.reduce(method, new AtomicBoolean()).get();
                if (!rewriteRunWithAnyChangedSource) {
                    return method;
                }

                maybeAddImport(DOCUMENT_EXAMPLE_ANNOTATION_FQN);

                selectedCount++;
                return JavaTemplate.builder("@DocumentExample")
                        .contextSensitive()
                        .imports(DOCUMENT_EXAMPLE_ANNOTATION_FQN)
                        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .build()
                        .apply(getCursor(), method.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }

            private boolean containsAnyChangedSource(J.MethodInvocation method) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean isGood) {
                        if (!isGood.get() &&
                                method.getArguments().size() > 1 &&
                                isStringLiteral(method.getArguments().get(0)) &&
                                isStringLiteral(method.getArguments().get(1))) {
                            isGood.set(true);
                        }
                        return super.visitMethodInvocation(method, isGood);
                    }
                }.reduce(method.getArguments(), new AtomicBoolean()).get();
            }
        });
    }

    private static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }

}
