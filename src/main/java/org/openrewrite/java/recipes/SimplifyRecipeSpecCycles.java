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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

public class SimplifyRecipeSpecCycles extends Recipe {

    private static final String RECIPE_SPEC = "org.openrewrite.test.RecipeSpec";
    private static final MethodMatcher CYCLES = new MethodMatcher(RECIPE_SPEC + " cycles(int)");
    private static final MethodMatcher EXPECTED_CYCLES = new MethodMatcher(RECIPE_SPEC + " expectedCyclesThatMakeChanges(int)");
    private static final MethodMatcher REWRITE_RUN = new MethodMatcher("org.openrewrite.test.RewriteTest rewriteRun(..)");

    @Getter
    final String displayName = "Remove `RecipeSpec.cycles(int)` calls that restate the default";

    @Getter
    final String description = "Removes a `cycles(int)` call on a `RecipeSpec` whose value equals the number of cycles " +
            "`rewriteRun` runs without it, which is one more than `expectedCyclesThatMakeChanges(int)` and at least two. " +
            "A cycle count pinned to the expected count is left alone, since removing it adds the cycle that verifies " +
            "the recipe settled.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(CYCLES), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                // A superclass may set cycles in a defaults method this class cannot see
                if (cd.getExtends() != null || !TypeUtils.isAssignableTo("org.openrewrite.test.RewriteTest", cd.getType())) {
                    return cd;
                }

                Scopes scopes = new Scopes();
                new Collector().visit(cd.getBody(), scopes, new Cursor(getCursor().getParentOrThrow(), cd));
                Set<UUID> redundant = scopes.redundantCycles();
                if (redundant.isEmpty()) {
                    return cd;
                }

                J.Block body = (J.Block) new Remover(redundant).visitNonNull(cd.getBody(), ctx, new Cursor(getCursor().getParentOrThrow(), cd));
                if (scopes.defaults != null && !scopes.defaults.cycles.isEmpty() && redundant.containsAll(ids(scopes.defaults.cycles))) {
                    body = body.withStatements(ListUtils.map(body.getStatements(), s -> isEmptyDefaults(s) ? null : s));
                    maybeRemoveImport(RECIPE_SPEC);
                }
                return cd.withBody(body);
            }
        });
    }

    /**
     * The calls setting cycles or expected cycles, grouped by the spec they configure.
     */
    private static class Scopes {
        @Nullable
        Scope defaults;

        final Map<UUID, Scope> specLambdas = new LinkedHashMap<>();

        boolean setElsewhere;

        Set<UUID> redundantCycles() {
            Set<UUID> redundant = new HashSet<>();
            Integer classExpected = defaults == null ? null : defaults.expected();
            boolean classSetsCycles = defaults != null && !defaults.cycles.isEmpty();
            boolean classExpectedKnown = defaults == null || defaults.expectedCycles.isEmpty() || classExpected != null;
            if (defaults != null && defaults.isRedundant(classExpected) && specLambdas.isEmpty() && !setElsewhere) {
                redundant.addAll(ids(defaults.cycles));
            }
            if (!classSetsCycles) {
                for (Scope lambda : specLambdas.values()) {
                    if (lambda.expectedCycles.isEmpty() ? classExpectedKnown && lambda.isRedundant(classExpected) :
                            lambda.isRedundant(lambda.expected())) {
                        redundant.addAll(ids(lambda.cycles));
                    }
                }
            }
            return redundant;
        }
    }

    private static class Scope {
        final List<J.MethodInvocation> cycles = new ArrayList<>();
        final List<J.MethodInvocation> expectedCycles = new ArrayList<>();

        /**
         * The single literal expected cycle count, or null when there is none or it cannot be read.
         */
        @Nullable
        Integer expected() {
            return expectedCycles.size() == 1 ? literal(expectedCycles.get(0)) : null;
        }

        /**
         * Whether the single literal cycle count equals what rewriteRun derives from the expected count.
         */
        boolean isRedundant(@Nullable Integer expected) {
            if (cycles.size() != 1 || expectedCycles.size() > 1 || (!expectedCycles.isEmpty() && expected == null)) {
                return false;
            }
            Integer cycleCount = literal(cycles.get(0));
            return cycleCount != null && cycleCount == (expected == null ? 2 : Math.max(2, expected + 1));
        }
    }

    private static class Collector extends JavaIsoVisitor<Scopes> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Scopes scopes) {
            return classDecl;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, Scopes scopes) {
            return newClass;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Scopes scopes) {
            boolean cycles = CYCLES.matches(method);
            if (cycles || EXPECTED_CYCLES.matches(method)) {
                Scope scope = scopeOf(scopes);
                if (scope == null) {
                    scopes.setElsewhere = true;
                } else {
                    (cycles ? scope.cycles : scope.expectedCycles).add(method);
                }
            }
            return super.visitMethodInvocation(method, scopes);
        }

        private @Nullable Scope scopeOf(Scopes scopes) {
            Cursor enclosing = getCursor().dropParentUntil(t -> t instanceof J.Lambda && isSpecLambda((J.Lambda) t) ||
                    t instanceof J.MethodDeclaration || t == Cursor.ROOT_VALUE || t instanceof J.ClassDeclaration);
            Object scope = enclosing.getValue();
            if (scope instanceof J.Lambda) {
                return scopes.specLambdas.computeIfAbsent(((J.Lambda) scope).getId(), id -> new Scope());
            }
            if (scope instanceof J.MethodDeclaration && isDefaults((J.MethodDeclaration) scope)) {
                if (scopes.defaults == null) {
                    scopes.defaults = new Scope();
                }
                return scopes.defaults;
            }
            return null;
        }
    }

    private static class Remover extends JavaVisitor<ExecutionContext> {
        private final Set<UUID> redundant;

        Remover(Set<UUID> redundant) {
            this.redundant = redundant;
        }

        @Override
        public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J j = super.visitMethodInvocation(method, ctx);
            if (!(j instanceof J.MethodInvocation)) {
                return j;
            }
            J.MethodInvocation mi = (J.MethodInvocation) j;
            if (redundant.contains(mi.getId()) && mi.getSelect() != null) {
                // A statement left as the bare spec does nothing
                if (mi.getSelect() instanceof J.Identifier && getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    return null;
                }
                return mi.getSelect().withPrefix(mi.getPrefix());
            }
            if (REWRITE_RUN.matches(mi)) {
                return mi.withArguments(ListUtils.mapFirst(mi.getArguments(), arg -> isIdentityLambda(arg) ? null : arg));
            }
            return mi;
        }
    }

    private static boolean isSpecLambda(J.Lambda lambda) {
        List<J> parameters = lambda.getParameters().getParameters();
        return parameters.size() == 1 && parameters.get(0) instanceof J.VariableDeclarations &&
               TypeUtils.isOfClassType(((J.VariableDeclarations) parameters.get(0)).getVariables().get(0).getType(), RECIPE_SPEC);
    }

    private static boolean isIdentityLambda(Expression arg) {
        if (!(arg instanceof J.Lambda) || !isSpecLambda((J.Lambda) arg) || !(((J.Lambda) arg).getBody() instanceof J.Identifier)) {
            return false;
        }
        J.Lambda lambda = (J.Lambda) arg;
        String parameter = ((J.VariableDeclarations) lambda.getParameters().getParameters().get(0)).getVariables().get(0).getSimpleName();
        return parameter.equals(((J.Identifier) lambda.getBody()).getSimpleName());
    }

    private static boolean isDefaults(J.MethodDeclaration method) {
        return "defaults".equals(method.getSimpleName()) && method.getParameters().size() == 1;
    }

    private static boolean isEmptyDefaults(Statement statement) {
        return statement instanceof J.MethodDeclaration && isDefaults((J.MethodDeclaration) statement) &&
               ((J.MethodDeclaration) statement).getBody() != null &&
               ((J.MethodDeclaration) statement).getBody().getStatements().isEmpty();
    }

    private static @Nullable Integer literal(J.MethodInvocation invocation) {
        Expression argument = invocation.getArguments().get(0);
        return argument instanceof J.Literal && ((J.Literal) argument).getValue() instanceof Integer ?
                (Integer) ((J.Literal) argument).getValue() : null;
    }

    private static Set<UUID> ids(List<? extends Tree> trees) {
        Set<UUID> ids = new HashSet<>();
        for (Tree tree : trees) {
            ids.add(tree.getId());
        }
        return ids;
    }
}
