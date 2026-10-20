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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

public class RemoveImportBeforeAddImport extends Recipe {

    private static final MethodMatcher MAYBE_ADD_IMPORT_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaVisitor maybeAddImport(..)", true);
    private static final MethodMatcher MAYBE_REMOVE_IMPORT_MATCHER =
            new MethodMatcher("org.openrewrite.java.JavaVisitor maybeRemoveImport(..)", true);

    @Getter
    final String displayName = "Reorder `maybeRemoveImport` before `maybeAddImport`";

    @Getter
    final String description = "Reorders `maybeAddImport` and `maybeRemoveImport` calls so that imports are removed before new imports " +
            "are added. This ordering prevents potential conflicts when the import being added and the import being " +
            "removed resolve to the same simple class name.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesMethod<>(MAYBE_ADD_IMPORT_MATCHER), new UsesMethod<>(MAYBE_REMOVE_IMPORT_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        List<Statement> statements = b.getStatements();
                        if (statements.size() < 2) {
                            return b;
                        }

                        List<Statement> reordered = new ArrayList<>(statements);
                        boolean changed = false;
                        int i = 0;

                        while (i < statements.size()) {
                            int end = i;
                            while (end < statements.size() && isImportCall(statements.get(end))) {
                                end++;
                            }
                            if (end - i < 2) {
                                i = end + 1;
                                continue;
                            }

                            // Stable-partition the whole run (removes first) so a single cycle converges
                            List<Statement> run = new ArrayList<>(end - i);
                            for (int j = i; j < end; j++) {
                                if (matches(statements.get(j), MAYBE_REMOVE_IMPORT_MATCHER)) {
                                    run.add(statements.get(j));
                                }
                            }
                            for (int j = i; j < end; j++) {
                                if (matches(statements.get(j), MAYBE_ADD_IMPORT_MATCHER)) {
                                    run.add(statements.get(j));
                                }
                            }
                            for (int j = i; j < end; j++) {
                                Statement moved = run.get(j - i);
                                if (moved != statements.get(j)) {
                                    reordered.set(j, moved.withPrefix(statements.get(j).getPrefix()));
                                    changed = true;
                                }
                            }
                            i = end;
                        }

                        return changed ? b.withStatements(reordered) : b;
                    }

                    private boolean isImportCall(Statement stmt) {
                        return matches(stmt, MAYBE_ADD_IMPORT_MATCHER) || matches(stmt, MAYBE_REMOVE_IMPORT_MATCHER);
                    }

                    private boolean matches(Statement stmt, MethodMatcher matcher) {
                        return stmt instanceof J.MethodInvocation && matcher.matches((J.MethodInvocation) stmt);
                    }
                }
        );
    }
}
