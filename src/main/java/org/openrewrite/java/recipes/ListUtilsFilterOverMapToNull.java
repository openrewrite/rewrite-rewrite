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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class ListUtilsFilterOverMapToNull extends Recipe {

    private static final int MAX_LINE_LENGTH = 120;

    private static final MethodMatcher MAP_MATCHER = new MethodMatcher("org.openrewrite.internal.ListUtils map(java.util.List, ..)");

    @Getter
    final String displayName = "Use `ListUtils.filter` instead of mapping elements to `null`";

    @Getter
    final String description = "Replaces `ListUtils.map(list, e -> condition ? null : e)` and the equivalent block lambdas " +
            "with `ListUtils.filter(list, e -> !condition)`, which states the intent of removing elements directly.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MAP_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                JavaType.Method mapType = mi.getMethodType();
                if (!MAP_MATCHER.matches(mi) || mapType == null || !(mi.getArguments().get(1) instanceof J.Lambda)) {
                    return mi;
                }
                J.Lambda lambda = (J.Lambda) mi.getArguments().get(1);
                JavaType.Parameterized listType = TypeUtils.asParameterized(mapType.getReturnType());
                List<J> parameters = lambda.getParameters().getParameters();
                if (listType == null || parameters.size() != 1 || !(parameters.get(0) instanceof J.VariableDeclarations)) {
                    return mi;
                }
                String element = ((J.VariableDeclarations) parameters.get(0)).getVariables().get(0).getSimpleName();

                Expression condition;
                Expression whenTrue;
                Expression whenFalse;
                J body = lambda.getBody();
                if (body instanceof J.Block && ((J.Block) body).getStatements().size() == 1 &&
                        ((J.Block) body).getStatements().get(0) instanceof J.Return) {
                    body = ((J.Return) ((J.Block) body).getStatements().get(0)).getExpression();
                }
                if (body instanceof J.Ternary) {
                    J.Ternary ternary = (J.Ternary) body;
                    condition = ternary.getCondition();
                    whenTrue = ternary.getTruePart();
                    whenFalse = ternary.getFalsePart();
                } else if (body instanceof J.Block) {
                    List<Statement> statements = ((J.Block) body).getStatements();
                    if (statements.isEmpty() || !(statements.get(0) instanceof J.If)) {
                        return mi;
                    }
                    J.If iff = (J.If) statements.get(0);
                    condition = iff.getIfCondition().getTree();
                    whenTrue = returned(iff.getThenPart());
                    if (statements.size() == 1 && iff.getElsePart() != null) {
                        whenFalse = returned(iff.getElsePart().getBody());
                    } else if (statements.size() == 2 && iff.getElsePart() == null) {
                        whenFalse = returned(statements.get(1));
                    } else {
                        return mi;
                    }
                } else {
                    return mi;
                }

                Expression keptCondition = condition;
                List<Comment> droppedComments = new JavaIsoVisitor<List<Comment>>() {
                    @Override
                    public @Nullable J visit(@Nullable Tree tree, List<Comment> comments) {
                        return tree == keptCondition ? (J) tree : super.visit(tree, comments);
                    }

                    @Override
                    public Space visitSpace(Space space, Space.Location loc, List<Comment> comments) {
                        comments.addAll(ListUtils.filter(space.getComments(), comment -> !(comment instanceof TextComment &&
                                ((TextComment) comment).getText().trim().startsWith("noinspection"))));
                        return space;
                    }
                }.reduce(lambda.getBody(), new ArrayList<>());
                if (!droppedComments.isEmpty()) {
                    return mi;
                }

                condition = condition.unwrap();
                Expression predicate;
                if (isElement(whenTrue, element) && J.Literal.isLiteralValue(whenFalse, null)) {
                    predicate = condition;
                } else if (!J.Literal.isLiteralValue(whenTrue, null) || !isElement(whenFalse, element)) {
                    return mi;
                } else if (condition instanceof J.Unary && ((J.Unary) condition).getOperator() == J.Unary.Type.Not) {
                    predicate = ((J.Unary) condition).getExpression().unwrap();
                } else if (condition instanceof J.Binary && ((J.Binary) condition).getOperator() == J.Binary.Type.Equal) {
                    predicate = ((J.Binary) condition).withOperator(J.Binary.Type.NotEqual);
                } else if (condition instanceof J.Binary && ((J.Binary) condition).getOperator() == J.Binary.Type.NotEqual) {
                    predicate = ((J.Binary) condition).withOperator(J.Binary.Type.Equal);
                } else {
                    Expression operand = condition.withPrefix(Space.EMPTY);
                    if (!(operand instanceof J.MethodInvocation || operand instanceof J.Identifier ||
                            operand instanceof J.FieldAccess || operand instanceof J.Parentheses)) {
                        operand = new J.Parentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(operand));
                    }
                    predicate = new J.Unary(randomId(), Space.EMPTY, Markers.EMPTY,
                            JLeftPadded.build(J.Unary.Type.Not), operand, JavaType.Primitive.Boolean);
                }

                JavaType.Parameterized predicateType = new JavaType.Parameterized(null,
                        JavaType.ShallowClass.build("java.util.function.Predicate"),
                        singletonList(listType.getTypeParameters().get(0)));
                JavaType.Method filterType = mapType
                        .withName("filter")
                        .withParameterTypes(asList(mapType.getParameterTypes().get(0), predicateType));
                if (mi.getSelect() == null) {
                    maybeRemoveImport("org.openrewrite.internal.ListUtils.map");
                    maybeAddImport("org.openrewrite.internal.ListUtils", "filter", false);
                }
                J.Lambda filterLambda = lambda
                        .withBody(predicate.withPrefix(lambda.getBody().getPrefix()))
                        .withType(predicateType);
                J.MethodInvocation filter = mi
                        .withName(mi.getName().withSimpleName("filter").withType(filterType))
                        .withMethodType(filterType)
                        .withArguments(asList(mi.getArguments().get(0), filterLambda));
                if (lambda.getBody() instanceof J.Block && exceedsMaxLineLength(getCursor(), filter, filterLambda)) {
                    J.Lambda wrapped = filterLambda.withBody(predicate.withPrefix(Space.format("\n")));
                    return filter.withArguments(asList(mi.getArguments().get(0),
                            autoFormat(wrapped, ctx, new Cursor(getCursor().getParentOrThrow(), filter))));
                }
                return filter;
            }
        });
    }

    private static boolean exceedsMaxLineLength(Cursor cursor, J.MethodInvocation filter, J.Lambda filterLambda) {
        J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (cu == null) {
            return false;
        }
        J.MethodInvocation original = cursor.getValue();
        PrintOutputCapture<AtomicInteger> out = new PrintOutputCapture<>(new AtomicInteger(-1));
        new JavaPrinter<AtomicInteger>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, PrintOutputCapture<AtomicInteger> p) {
                return super.visitMethodInvocation(method == original ? filter : method, p);
            }

            @Override
            public J visitLambda(J.Lambda lambda, PrintOutputCapture<AtomicInteger> p) {
                J l = super.visitLambda(lambda, p);
                if (lambda == filterLambda) {
                    p.getContext().set(p.getOut().length());
                }
                return l;
            }
        }.visit(cu, out);
        String printed = out.getOut();
        int lambdaEnd = out.getContext().get();
        if (lambdaEnd < 0) {
            return false;
        }
        int lineEnd = printed.indexOf('\n', lambdaEnd);
        int lineStart = printed.lastIndexOf('\n', lambdaEnd - 1) + 1;
        return (lineEnd < 0 ? printed.length() : lineEnd) - lineStart > MAX_LINE_LENGTH;
    }

    private static @Nullable Expression returned(Statement statement) {
        if (statement instanceof J.Block && ((J.Block) statement).getStatements().size() == 1) {
            return returned(((J.Block) statement).getStatements().get(0));
        }
        return statement instanceof J.Return ? ((J.Return) statement).getExpression() : null;
    }

    private static boolean isElement(@Nullable Expression expression, String element) {
        return expression instanceof J.Identifier && element.equals(((J.Identifier) expression).getSimpleName());
    }
}
