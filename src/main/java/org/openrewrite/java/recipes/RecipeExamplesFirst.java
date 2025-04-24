package org.openrewrite.java.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import static java.util.stream.Collectors.toList;

public class RecipeExamplesFirst extends Recipe {
    private static final String DOCUMENT_EXAMPLE_ANNOTATION_FQN = "org.openrewrite.DocumentExample";
    private static final AnnotationMatcher DOCUMENT_EXAMPLE_ANNOTATION_MATCHER =
            new AnnotationMatcher("@" + DOCUMENT_EXAMPLE_ANNOTATION_FQN);

    @Override
    public String getDisplayName() {
        return "Show `@DocumentExample`s first";
    }

    @Override
    public String getDescription() {
        return "Reorders `RewriteTest` methods to place `defaults` first, followed by any `@DocumentExample`s.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DOCUMENT_EXAMPLE_ANNOTATION_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                return cd.withBody(cd.getBody().withStatements(
                        cd.getBody().getStatements().stream()
                                .sorted((left, right) -> {
                                    if (left instanceof J.MethodDeclaration && right instanceof J.MethodDeclaration) {
                                        if ("defaults".equals(((J.MethodDeclaration) left).getSimpleName())) {
                                            return -1;
                                        } else if ("defaults".equals(((J.MethodDeclaration) right).getSimpleName())) {
                                            return 1;
                                        }

                                        boolean leftIsExample = ((J.MethodDeclaration) left).getLeadingAnnotations().stream()
                                                .anyMatch(DOCUMENT_EXAMPLE_ANNOTATION_MATCHER::matches);
                                        boolean rightIsExample = ((J.MethodDeclaration) right).getLeadingAnnotations().stream()
                                                .anyMatch(DOCUMENT_EXAMPLE_ANNOTATION_MATCHER::matches);
                                        if (leftIsExample && rightIsExample) {
                                            return 0;
                                        } else if (leftIsExample) {
                                            return -1;
                                        } else if (rightIsExample) {
                                            return 1;
                                        }
                                    }
                                    return 0;
                                })
                                .collect(toList())
                ));
            }
        });
    }
}
