/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.disjoint;
import static java.util.Collections.singletonList;

public class UnconditionalMaybeImport extends Recipe {

    private static final MethodMatcher MAYBE_ADD_IMPORT_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaVisitor maybeAddImport(..)", true);
    private static final MethodMatcher MAYBE_REMOVE_IMPORT_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaVisitor maybeRemoveImport(..)", true);

    @Getter
    final String displayName = "Drop redundant conditions around `maybeAddImport` and `maybeRemoveImport`";

    @Getter
    final String description = "`maybeAddImport` only adds an import when the type is referenced, and `maybeRemoveImport` " +
            "only removes one when the type is no longer referenced, so wrapping these calls in an `if` statement is redundant. " +
            "Unwraps `if` statements without an `else` that contain only such calls, when the condition and arguments have " +
            "no side effects and the condition does not guard the arguments. Guards comparing trees by identity, such as " +
            "`if (cu != c)`, are kept, as are calls passing `onlyIfReferenced` as anything other than `true`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(MAYBE_ADD_IMPORT_MATCHER), new UsesMethod<>(MAYBE_REMOVE_IMPORT_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                            if (!(statement instanceof J.If) || ((J.If) statement).getElsePart() != null) {
                                return statement;
                            }
                            J.If iff = (J.If) statement;
                            Expression condition = iff.getIfCondition().getTree();
                            boolean braced = iff.getThenPart() instanceof J.Block;
                            List<Statement> guarded = braced ?
                                    ((J.Block) iff.getThenPart()).getStatements() :
                                    singletonList(iff.getThenPart());
                            if (guarded.isEmpty() || !isSideEffectFree(condition)) {
                                return statement;
                            }
                            Set<String> conditionIdentifiers = identifiers(condition);
                            for (Statement s : guarded) {
                                if (!(s instanceof J.MethodInvocation)) {
                                    return statement;
                                }
                                J.MethodInvocation mi = (J.MethodInvocation) s;
                                JavaType.Method methodType = mi.getMethodType();
                                if (methodType == null ||
                                    !MAYBE_ADD_IMPORT_MATCHER.matches(mi) && !MAYBE_REMOVE_IMPORT_MATCHER.matches(mi) ||
                                    mi.getSelect() != null && !isThis(mi.getSelect())) {
                                    return statement;
                                }
                                for (int i = 0; i < mi.getArguments().size(); i++) {
                                    Expression argument = mi.getArguments().get(i);
                                    if (!isSideEffectFree(argument) ||
                                        !disjoint(identifiers(argument), conditionIdentifiers) ||
                                        methodType.getParameterTypes().get(i) == JavaType.Primitive.Boolean &&
                                        !J.Literal.isLiteralValue(argument, true)) {
                                        return statement;
                                    }
                                }
                            }
                            boolean guardNeeded = new JavaIsoVisitor<AtomicBoolean>() {
                                @Override
                                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                                    return method;
                                }

                                @Override
                                public J.Binary visitBinary(J.Binary binary, AtomicBoolean found) {
                                    if ((binary.getOperator() == J.Binary.Type.Equal || binary.getOperator() == J.Binary.Type.NotEqual) &&
                                        (TypeUtils.isAssignableTo("org.openrewrite.Tree", binary.getLeft().getType()) ||
                                         TypeUtils.isAssignableTo("org.openrewrite.Tree", binary.getRight().getType()))) {
                                        found.set(true);
                                    }
                                    return super.visitBinary(binary, found);
                                }

                                @Override
                                public Space visitSpace(Space space, Space.Location loc, AtomicBoolean found) {
                                    if (!space.getComments().isEmpty()) {
                                        found.set(true);
                                    }
                                    return space;
                                }
                            }.reduce(iff, new AtomicBoolean()).get();
                            if (guardNeeded) {
                                return statement;
                            }
                            return ListUtils.map(guarded, (i, s) -> {
                                Statement unwrapped = braced ? ShiftFormat.indent(s, getCursor(), -1) : s;
                                return i == 0 ?
                                        unwrapped.withPrefix(unwrapped.getPrefix().withWhitespace(iff.getPrefix().getWhitespace())) :
                                        unwrapped;
                            });
                        }));
                    }

                    private boolean isSideEffectFree(J j) {
                        if (j instanceof J.Literal || j instanceof J.Identifier) {
                            return true;
                        } else if (j instanceof J.Parentheses) {
                            return isSideEffectFree(((J.Parentheses<?>) j).getTree());
                        } else if (j instanceof J.FieldAccess) {
                            return isSideEffectFree(((J.FieldAccess) j).getTarget());
                        } else if (j instanceof J.InstanceOf) {
                            return isSideEffectFree(((J.InstanceOf) j).getExpression());
                        } else if (j instanceof J.Unary) {
                            J.Unary unary = (J.Unary) j;
                            return !unary.getOperator().isModifying() && isSideEffectFree(unary.getExpression());
                        } else if (j instanceof J.Binary) {
                            J.Binary binary = (J.Binary) j;
                            return isSideEffectFree(binary.getLeft()) && isSideEffectFree(binary.getRight());
                        }
                        return false;
                    }

                    private Set<String> identifiers(J j) {
                        return new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                                if (!isThis(identifier)) {
                                    names.add(identifier.getSimpleName());
                                }
                                return identifier;
                            }
                        }.reduce(j, new HashSet<>());
                    }

                    private boolean isThis(Expression expression) {
                        return expression instanceof J.Identifier && "this".equals(((J.Identifier) expression).getSimpleName());
                    }
                }
        );
    }
}
