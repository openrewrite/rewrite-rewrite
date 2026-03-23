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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveNlsRewriteAnnotations extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final AnnotationMatcher DISPLAY_NAME_MATCHER = new AnnotationMatcher("@org.openrewrite.NlsRewrite.DisplayName");
    private static final AnnotationMatcher DESCRIPTION_MATCHER = new AnnotationMatcher("@org.openrewrite.NlsRewrite.Description");

    String displayName = "Remove `@NlsRewrite` annotations from `Recipe` classes";
    String description = "Remove `@NlsRewrite.DisplayName` and `@NlsRewrite.Description` annotations, " +
            "but only from classes that extend `Recipe`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new DeclaresType<>(RECIPE, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (!TypeUtils.isAssignableTo(RECIPE, classDecl.getType()) ||
                                classDecl.getType() != null && "org.openrewrite".equals(classDecl.getType().getPackageName())) {
                            return classDecl;
                        }
                        maybeRemoveImport("org.openrewrite.NlsRewrite");
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        cd = (J.ClassDeclaration) new RemoveAnnotationVisitor(DISPLAY_NAME_MATCHER)
                                .visit(cd, ctx, getCursor().getParentOrThrow());
                        return (J.ClassDeclaration) new RemoveAnnotationVisitor(DESCRIPTION_MATCHER)
                                .visit(cd, ctx, getCursor().getParentOrThrow());
                    }
                }
        );
    }
}
