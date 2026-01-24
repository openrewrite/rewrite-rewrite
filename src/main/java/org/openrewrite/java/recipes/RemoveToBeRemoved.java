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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.LocalDate;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveToBeRemoved extends Recipe {

    String displayName = "Remove elements annotated with `@ToBeRemoved` past their date";

    String description = "Removes class, method and variable declarations annotated with " +
            "`org.openrewrite.internal.ToBeRemoved` whose `after` date has passed. " +
            "This does not remove invocations or references to such methods or variables. " +
            "Those must be handled separately, e.g. with `org.openrewrite.java.InlineMethodCalls`.";

    private static final String TO_BE_REMOVED = "org.openrewrite.internal.ToBeRemoved";
    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@" + TO_BE_REMOVED);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(TO_BE_REMOVED, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.@Nullable ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        return isPastRemovalDate(classDecl.getLeadingAnnotations()) ?
                                null : super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        return isPastRemovalDate(multiVariable.getLeadingAnnotations()) ?
                                null : super.visitVariableDeclarations(multiVariable, ctx);
                    }

                    @Override
                    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        return isPastRemovalDate(method.getLeadingAnnotations()) ?
                                null : super.visitMethodDeclaration(method, ctx);
                    }

                    private boolean isPastRemovalDate(List<J.Annotation> annotations) {
                        for (J.Annotation annotation : annotations) {
                            if (ANNOTATION_MATCHER.matches(annotation)) {
                                LocalDate afterDate = getAfterDate(annotation);
                                if (afterDate != null && !LocalDate.now().isBefore(afterDate)) {
                                    maybeRemoveImport(TO_BE_REMOVED);
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    private @Nullable LocalDate getAfterDate(J.Annotation annotation) {
                        if (annotation.getArguments() == null) {
                            return null;
                        }
                        for (Expression arg : annotation.getArguments()) {
                            if (arg instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) arg;
                                if (assignment.getVariable() instanceof J.Identifier &&
                                        "after".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                                        assignment.getAssignment() instanceof J.Literal) {
                                    String value = (String) ((J.Literal) assignment.getAssignment()).getValue();
                                    if (value != null) {
                                        return LocalDate.parse(value);
                                    }
                                }
                            }
                        }
                        return null;
                    }
                }
        );
    }
}
