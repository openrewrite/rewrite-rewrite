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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class UseCollectionsSingletonForSingleTag extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final MethodMatcher GET_TAGS_MATCHER = new MethodMatcher(RECIPE + " getTags()", true);
    private static final MethodMatcher NEW_HASH_SET_MATCHER = new MethodMatcher("java.util.HashSet <constructor>(java.util.Collection)");
    private static final MethodMatcher NEW_LINKED_HASH_SET_MATCHER = new MethodMatcher("java.util.LinkedHashSet <constructor>(java.util.Collection)");
    private static final MethodMatcher ARRAYS_AS_LIST_MATCHER = new MethodMatcher("java.util.Arrays asList(..)");
    private static final MethodMatcher SINGLETON_LIST_MATCHER = new MethodMatcher("java.util.Collections singletonList(..)");
    private static final MethodMatcher UNMODIFIABLE_SET_MATCHER = new MethodMatcher("java.util.Collections unmodifiableSet(..)");

    String displayName = "Use `Collections.singleton(..)` for a single recipe tag";

    String description = "Replace `new HashSet<>(Arrays.asList(tag))` and similar copies of a one-element list, optionally wrapped in `Collections.unmodifiableSet(..)`, with `Collections.singleton(tag)` for recipe tags. " +
                         "Tags are only read, so the immutable singleton set is safe on Java 8.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new DeclaresType<>(RECIPE, true),
                        Preconditions.or(
                                new UsesMethod<>(NEW_HASH_SET_MATCHER),
                                new UsesMethod<>(NEW_LINKED_HASH_SET_MATCHER)),
                        Preconditions.or(
                                new UsesMethod<>(ARRAYS_AS_LIST_MATCHER),
                                new UsesMethod<>(SINGLETON_LIST_MATCHER))),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J j = super.visitNewClass(newClass, ctx);
                        if (!(j instanceof J.NewClass)) {
                            return j;
                        }
                        Expression tag = singleTag(j);
                        if (tag == null || !isRecipeTags(getCursor().getParentTreeCursor())) {
                            return j;
                        }
                        return toSingleton((J.NewClass) j, tag);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation) || !UNMODIFIABLE_SET_MATCHER.matches((J.MethodInvocation) j)) {
                            return j;
                        }
                        J.MethodInvocation m = (J.MethodInvocation) j;
                        Expression tag = singleTag(m.getArguments().get(0));
                        if (tag == null || !isRecipeTags(getCursor().getParentTreeCursor())) {
                            return m;
                        }
                        maybeRemoveImport("java.util.Collections.unmodifiableSet");
                        return toSingleton(m, tag);
                    }

                    private J toSingleton(Expression copy, Expression tag) {
                        maybeAddImport("java.util.Collections", "singleton");
                        maybeRemoveImport("java.util.Collections");
                        maybeRemoveImport("java.util.HashSet");
                        maybeRemoveImport("java.util.LinkedHashSet");
                        maybeRemoveImport("java.util.Arrays");
                        maybeRemoveImport("java.util.Collections.singletonList");
                        return JavaTemplate.builder("singleton(#{any(java.lang.String)})")
                                .staticImports("java.util.Collections.singleton")
                                .build()
                                .apply(getCursor(), copy.getCoordinates().replace(), tag);
                    }

                    private @Nullable Expression singleTag(J copy) {
                        if (!(copy instanceof J.NewClass) ||
                            ((J.NewClass) copy).getBody() != null ||
                            !(NEW_HASH_SET_MATCHER.matches((J.NewClass) copy) || NEW_LINKED_HASH_SET_MATCHER.matches((J.NewClass) copy))) {
                            return null;
                        }
                        Expression list = ((J.NewClass) copy).getArguments().get(0);
                        if (!(ARRAYS_AS_LIST_MATCHER.matches(list) || SINGLETON_LIST_MATCHER.matches(list)) ||
                            ((J.MethodInvocation) list).getArguments().size() != 1) {
                            return null;
                        }
                        Expression element = ((J.MethodInvocation) list).getArguments().get(0);
                        if (element instanceof J.Empty || element.getType() instanceof JavaType.Array) {
                            return null;
                        }
                        return element;
                    }

                    private boolean isRecipeTags(Cursor parent) {
                        Object value = parent.getValue();
                        if (value instanceof J.Return) {
                            J.MethodDeclaration method = parent.firstEnclosing(J.MethodDeclaration.class);
                            return method != null && GET_TAGS_MATCHER.matches(method.getMethodType());
                        }
                        if (value instanceof J.VariableDeclarations.NamedVariable &&
                            "tags".equals(((J.VariableDeclarations.NamedVariable) value).getSimpleName())) {
                            Object classBodyOwner = parent.getParentTreeCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                            return classBodyOwner instanceof J.ClassDeclaration &&
                                   TypeUtils.isAssignableTo(RECIPE, ((J.ClassDeclaration) classBodyOwner).getType());
                        }
                        return false;
                    }
                }
        );
    }
}
