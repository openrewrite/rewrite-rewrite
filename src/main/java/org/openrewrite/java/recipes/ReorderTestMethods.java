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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

import static java.util.stream.Collectors.toList;

public class ReorderTestMethods extends Recipe {
    private static final String DOCUMENT_EXAMPLE_ANNOTATION_FQN = "org.openrewrite.DocumentExample";
    private static final AnnotationMatcher DOCUMENT_EXAMPLE_ANNOTATION_MATCHER =
            new AnnotationMatcher("@" + DOCUMENT_EXAMPLE_ANNOTATION_FQN);
    private static final AnnotationMatcher BEFORE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Before*");
    private static final AnnotationMatcher AFTER_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.After*");

    @Override
    public String getDisplayName() {
        return "Show `@DocumentExample`s first";
    }

    @Override
    public String getDescription() {
        return "Reorders `RewriteTest` methods to place `defaults` first, followed by any `@DocumentExample`s.";
    }

    private static final Comparator<J.MethodDeclaration> methodDeclarationComparator = Comparator
            .<J.MethodDeclaration, Boolean>comparing(md -> md.getLeadingAnnotations().stream().anyMatch(BEFORE_ANNOTATION_MATCHER::matches))
            .thenComparing(md -> md.getLeadingAnnotations().stream().anyMatch(AFTER_ANNOTATION_MATCHER::matches))
            .thenComparing(md -> "defaults".equals(md.getSimpleName()))
            .thenComparing(md -> md.getLeadingAnnotations().stream().anyMatch(DOCUMENT_EXAMPLE_ANNOTATION_MATCHER::matches))
            .reversed();

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DOCUMENT_EXAMPLE_ANNOTATION_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                return cd.withBody(cd.getBody().withStatements(
                        cd.getBody().getStatements().stream().sorted((left, right) -> {
                            // Do not change order of helper methods, as those are often at top/bottom.
                            // This also locks into place methods before and after helper methods.
                            return left instanceof J.MethodDeclaration && right instanceof J.MethodDeclaration ?
                                    isHelperMethod((J.MethodDeclaration) left) || isHelperMethod((J.MethodDeclaration) right) ?
                                            0 : methodDeclarationComparator.compare((J.MethodDeclaration) left, (J.MethodDeclaration) right) : 0;
                        }).collect(toList())));
            }

            boolean isHelperMethod(J.MethodDeclaration md) {
                return md.hasModifier(J.Modifier.Type.Static) || md.hasModifier(J.Modifier.Type.Private);
            }
        });
    }
}
