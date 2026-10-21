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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;

public class UseVisitorReduce extends Recipe {

    private static final MethodMatcher VISIT_MATCHER =
            new MethodMatcher("org.openrewrite.TreeVisitor visit(org.openrewrite.Tree, ..)", true);
    private static final MethodMatcher VISIT_NON_NULL_MATCHER =
            new MethodMatcher("org.openrewrite.TreeVisitor visitNonNull(org.openrewrite.Tree, ..)", true);
    private static final MethodMatcher REDUCE_MATCHER =
            new MethodMatcher("org.openrewrite.TreeVisitor reduce(..)", true);

    @Getter
    final String displayName = "Use `TreeVisitor#reduce` instead of a captured accumulator";

    @Getter
    final String description = "Replaces an accumulator that is declared, passed to `TreeVisitor#visit` and only read " +
            "afterwards with a call to `TreeVisitor#reduce`, which returns the accumulator it was given. " +
            "When the accumulator is read only once, right after the visit, the `reduce` call is inlined at that read.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(VISIT_MATCHER),
                        new UsesMethod<>(VISIT_NON_NULL_MATCHER),
                        new UsesMethod<>(REDUCE_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        List<Statement> statements = b.getStatements();
                        for (int i = 0; i + 2 < statements.size(); i++) {
                            if (!(statements.get(i) instanceof J.VariableDeclarations) ||
                                    !(statements.get(i + 1) instanceof J.MethodInvocation)) {
                                continue;
                            }
                            J.VariableDeclarations declaration = (J.VariableDeclarations) statements.get(i);
                            J.MethodInvocation visit = (J.MethodInvocation) statements.get(i + 1);
                            JavaType.Method visitType = visit.getMethodType();
                            if (declaration.getVariables().size() != 1 || visitType == null ||
                                    !visit.getComments().isEmpty() ||
                                    visit.getSelect() instanceof J.Identifier && "super".equals(((J.Identifier) visit.getSelect()).getSimpleName()) ||
                                    !(VISIT_MATCHER.matches(visit) || VISIT_NON_NULL_MATCHER.matches(visit) || REDUCE_MATCHER.matches(visit))) {
                                continue;
                            }

                            J.VariableDeclarations.NamedVariable accumulator = declaration.getVariables().get(0);
                            JavaType.Variable accumulatorType = accumulator.getVariableType();
                            Expression initializer = accumulator.getInitializer();
                            if (accumulatorType == null ||
                                    TypeUtils.isAssignableTo("org.openrewrite.ExecutionContext", accumulatorType.getType()) ||
                                    hasAnnotation(declaration.getTypeExpression()) ||
                                    !(initializer instanceof J.Literal || initializer instanceof J.NewClass &&
                                            ((J.NewClass) initializer).getEnclosing() == null &&
                                            ((J.NewClass) initializer).getBody() == null &&
                                            ((J.NewClass) initializer).getArguments().stream().allMatch(arg ->
                                                    arg instanceof J.Empty || arg instanceof J.Literal || arg instanceof J.Identifier)) ||
                                    !(visit.getArguments().get(1) instanceof J.Identifier) ||
                                    !accumulatorType.equals(((J.Identifier) visit.getArguments().get(1)).getFieldType()) ||
                                    !TypeUtils.isOfType(visitType.getParameterTypes().get(1), accumulatorType.getType()) ||
                                    references(singletonList(visit), accumulatorType).size() != 1) {
                                continue;
                            }

                            List<J.Identifier> laterReferences = references(statements.subList(i + 2, statements.size()), accumulatorType);
                            if (laterReferences.isEmpty()) {
                                continue;
                            }

                            JavaType.Method reduceType = REDUCE_MATCHER.matches(visit) ? visitType :
                                    visitType.withName("reduce").withReturnType(visitType.getParameterTypes().get(1));
                            J.MethodInvocation renamed = visit
                                    .withName(visit.getName().withSimpleName("reduce").withType(reduceType))
                                    .withMethodType(reduceType);
                            JContainer<Expression> arguments = renamed.getPadding().getArguments();
                            J.MethodInvocation reduce = renamed.getPadding().withArguments(arguments.getPadding().withElements(
                                    ListUtils.map(arguments.getPadding().getElements(), (index, arg) -> index == 1 ?
                                            arg.withElement(initializer.withPrefix(arg.getElement().getPrefix())) : arg)));

                            int declarationIndex = i;
                            Statement next = statements.get(i + 2);
                            J.Identifier onlyReference = laterReferences.get(0);
                            boolean anonymousVisitor = visit.getSelect() instanceof J.NewClass &&
                                    ((J.NewClass) visit.getSelect()).getBody() != null;
                            if (laterReferences.size() == 1 && next.getComments().isEmpty() &&
                                    firstEvaluated(next, !anonymousVisitor) == onlyReference) {
                                Statement inlined = ((Statement) new JavaVisitor<ExecutionContext>() {
                                    @Override
                                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                                        return identifier == onlyReference ?
                                                reindent(reduce, visit.getPrefix(), identifier.getPrefix()) : identifier;
                                    }
                                }.visitNonNull(next, ctx)).withPrefix(declaration.getPrefix());
                                statements = ListUtils.map(statements, (index, s) ->
                                        index == declarationIndex || index == declarationIndex + 1 ? null :
                                                index == declarationIndex + 2 ? inlined : s);
                            } else {
                                J.VariableDeclarations reduced = declaration.withVariables(singletonList(
                                        accumulator.withInitializer(reindent(reduce, visit.getPrefix(), initializer.getPrefix()))));
                                statements = ListUtils.map(statements, (index, s) ->
                                        index == declarationIndex ? reduced : index == declarationIndex + 1 ? null : s);
                            }
                        }
                        return b.withStatements(statements);
                    }
                });
    }

    private static List<J.Identifier> references(Iterable<? extends Tree> trees, JavaType.Variable variable) {
        return new JavaIsoVisitor<List<J.Identifier>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, List<J.Identifier> references) {
                if (variable.equals(identifier.getFieldType())) {
                    references.add(identifier);
                }
                return identifier;
            }
        }.reduce(trees, new ArrayList<>());
    }

    private static boolean hasAnnotation(@Nullable TypeTree typeExpression) {
        return typeExpression != null && new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean found) {
                found.set(true);
                return annotation;
            }
        }.reduce(typeExpression, new AtomicBoolean()).get();
    }

    // Continuation lines follow `reduce` when it starts a deeper line than the visit statement did,
    // as when the declaration wraps after its `=`
    private static J.MethodInvocation reindent(J.MethodInvocation reduce, Space visitPrefix, Space prefix) {
        String from = indentation(visitPrefix.getWhitespace());
        String to = indentation(prefix.getWhitespace());
        if (!prefix.getWhitespace().contains("\n") || to.equals(from) || !to.startsWith(from)) {
            return reduce.withPrefix(prefix);
        }
        return new JavaIsoVisitor<Integer>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, Integer p) {
                Space s = super.visitSpace(space, loc, p);
                return s.withWhitespace(shift(s.getWhitespace()))
                        .withComments(ListUtils.map(s.getComments(), c -> c.withSuffix(shift(c.getSuffix()))));
            }

            private String shift(String whitespace) {
                int lineStart = whitespace.lastIndexOf('\n') + 1;
                return lineStart > 0 && whitespace.startsWith(from, lineStart) ?
                        whitespace.substring(0, lineStart) + to + whitespace.substring(lineStart + from.length()) :
                        whitespace;
            }
        }.visitNonNull(reduce.withPrefix(Space.EMPTY), 0).withPrefix(prefix);
    }

    private static String indentation(String whitespace) {
        return whitespace.substring(whitespace.lastIndexOf('\n') + 1);
    }

    private static @Nullable J firstEvaluated(@Nullable J j, boolean intoConditions) {
        if (j instanceof J.Return) {
            return firstEvaluated(((J.Return) j).getExpression(), intoConditions);
        }
        if (j instanceof J.If) {
            return intoConditions ? firstEvaluated(((J.If) j).getIfCondition().getTree(), true) : null;
        }
        if (j instanceof J.VariableDeclarations) {
            List<J.VariableDeclarations.NamedVariable> variables = ((J.VariableDeclarations) j).getVariables();
            return variables.size() == 1 ? firstEvaluated(variables.get(0).getInitializer(), intoConditions) : null;
        }
        if (j instanceof J.MethodInvocation) {
            return firstEvaluated(((J.MethodInvocation) j).getSelect(), intoConditions);
        }
        if (j instanceof J.FieldAccess) {
            return firstEvaluated(((J.FieldAccess) j).getTarget(), intoConditions);
        }
        if (j instanceof J.Parentheses) {
            return firstEvaluated(((J.Parentheses<?>) j).getTree(), intoConditions);
        }
        if (j instanceof J.TypeCast) {
            return firstEvaluated(((J.TypeCast) j).getExpression(), intoConditions);
        }
        if (j instanceof J.InstanceOf) {
            return firstEvaluated(((J.InstanceOf) j).getExpression(), intoConditions);
        }
        if (j instanceof J.Binary) {
            return firstEvaluated(((J.Binary) j).getLeft(), intoConditions);
        }
        if (j instanceof J.Ternary) {
            return intoConditions ? firstEvaluated(((J.Ternary) j).getCondition(), true) : null;
        }
        if (j instanceof J.Unary && !((J.Unary) j).getOperator().isModifying()) {
            return firstEvaluated(((J.Unary) j).getExpression(), intoConditions);
        }
        return j;
    }
}
