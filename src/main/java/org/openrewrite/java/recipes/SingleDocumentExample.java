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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

public class SingleDocumentExample extends Recipe {
    private static final String DOCUMENT_EXAMPLE = "org.openrewrite.DocumentExample";
    private static final AnnotationMatcher DOCUMENT_EXAMPLE_ANNOTATION_MATCHER = new AnnotationMatcher("@" + DOCUMENT_EXAMPLE);

    @Override
    public String getDisplayName() {
        return "Single `@DocumentExample` per test class";
    }

    @Override
    public String getDescription() {
        return "Ensures that there is only one `@DocumentExample` annotation per test class, " +
                "as that looks best in the documentation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DOCUMENT_EXAMPLE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                AtomicBoolean foundDocumentExample = new AtomicBoolean(false);
                return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), st -> {
                            if (!(st instanceof J.MethodDeclaration) ||
                                    FindAnnotations.find(st, DOCUMENT_EXAMPLE).isEmpty()) {
                                return st;
                            }
                            if (!foundDocumentExample.get()) {
                                foundDocumentExample.set(true);
                                return st;
                            }
                            return new RemoveAnnotationVisitor(DOCUMENT_EXAMPLE_ANNOTATION_MATCHER)
                                    .visitMethodDeclaration((J.MethodDeclaration) st, ctx);
                        }
                )));
            }
        });
    }
}
