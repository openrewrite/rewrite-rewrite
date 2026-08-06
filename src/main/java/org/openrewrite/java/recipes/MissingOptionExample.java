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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

public class MissingOptionExample extends Recipe {

    private static final String ORG_OPENREWRITE_OPTION = "org.openrewrite.Option";
    private static final String TODO_COMMENT = " TODO Provide a usage example for the docs";

    @Getter
    final String displayName = "Find missing `@Option` `example` values";

    @Getter
    final String description = "Find `@Option` annotations that are missing `example` values for documentation, " +
                               "and add a TODO comment.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ORG_OPENREWRITE_OPTION, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                        return maybeAddTodoComment(mv, mv.getLeadingAnnotations(), mv.getTypeExpression());
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        return maybeAddTodoComment(md, md.getLeadingAnnotations(), md.getReturnTypeExpression());
                    }
                });
    }

    private static <J2 extends J> J2 maybeAddTodoComment(J2 j, List<J.Annotation> leadingAnnotations, @Nullable TypeTree typeExpression) {
        if (leadingAnnotations.stream().noneMatch(MissingOptionExample::lacksExample) || hasTodoComment(j.getPrefix())) {
            return j;
        }
        // Skip boolean and non-String primitive options, as examples there are trivial
        if (typeExpression != null && isTrivialType(typeExpression.getType())) {
            return j;
        }
        return j.withPrefix(addTodoComment(j.getPrefix()));
    }

    private static boolean isTrivialType(@Nullable JavaType type) {
        return !TypeUtils.isString(type) &&
               (type instanceof JavaType.Primitive ||
                type instanceof JavaType.FullyQualified &&
                "java.lang".equals(((JavaType.FullyQualified) type).getPackageName()));
    }

    private static boolean lacksExample(J.Annotation annotation) {
        if (!TypeUtils.isOfClassType(annotation.getType(), ORG_OPENREWRITE_OPTION) || annotation.getArguments() == null) {
            return false;
        }
        // Skip if there is already an example value, or valid options
        return annotation.getArguments().stream().noneMatch(exp -> {
            if (exp instanceof J.Assignment) {
                Expression variable = ((J.Assignment) exp).getVariable();
                if (variable instanceof J.Identifier) {
                    String simpleName = ((J.Identifier) variable).getSimpleName();
                    return "example".equals(simpleName) || "valid".equals(simpleName);
                }
            }
            return false;
        });
    }

    private static boolean hasTodoComment(Space prefix) {
        return prefix.getComments().stream().anyMatch(comment ->
                comment instanceof TextComment && TODO_COMMENT.equals(((TextComment) comment).getText()));
    }

    private static Space addTodoComment(Space prefix) {
        List<Comment> comments = prefix.getComments();
        String priorWhitespace = comments.isEmpty() ?
                prefix.getWhitespace() :
                comments.get(comments.size() - 1).getSuffix();
        int lastNewline = priorWhitespace.lastIndexOf('\n');
        String suffix = lastNewline == -1 ? '\n' + priorWhitespace : priorWhitespace.substring(lastNewline);
        return prefix.withComments(ListUtils.concat(comments, new TextComment(false, TODO_COMMENT, suffix, Markers.EMPTY)));
    }
}
