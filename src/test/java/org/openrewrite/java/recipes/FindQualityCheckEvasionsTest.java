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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;

class FindQualityCheckEvasionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.recipes.FindQualityCheckEvasions")
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void findsStateKeptWhereChecksDontLook() {
        rewriteRun(
          srcMainJava(
            java(
              """
                import org.openrewrite.ExecutionContext;
                import org.openrewrite.Recipe;
                import org.openrewrite.Tree;
                import org.openrewrite.TreeVisitor;
                import org.openrewrite.marker.AlreadyReplaced;

                import java.util.HashSet;
                import java.util.Set;

                public class Smuggles extends Recipe {
                    @Override
                    public String getDisplayName() {
                        return "Smuggles";
                    }

                    @Override
                    public String getDescription() {
                        return "Keeps state where the checks don't look.";
                    }

                    @Override
                    public TreeVisitor<?, ExecutionContext> getVisitor() {
                        return new TreeVisitor<Tree, ExecutionContext>() {
                            @Override
                            public Tree visit(Tree tree, ExecutionContext ctx) {
                                Set<String> seen = ctx.computeMessageIfAbsent("seen", k -> new HashSet<>());
                                getCursor().getRoot().putMessage("plan", seen);
                                return tree.withMarkers(tree.getMarkers().add(new AlreadyReplaced(Tree.randomId(), "plan", "sha256")));
                            }
                        };
                    }
                }
                """,
              """
                import org.openrewrite.ExecutionContext;
                import org.openrewrite.Recipe;
                import org.openrewrite.Tree;
                import org.openrewrite.TreeVisitor;
                import org.openrewrite.marker.AlreadyReplaced;

                import java.util.HashSet;
                import java.util.Set;

                public class Smuggles extends Recipe {
                    @Override
                    public String getDisplayName() {
                        return "Smuggles";
                    }

                    @Override
                    public String getDescription() {
                        return "Keeps state where the checks don't look.";
                    }

                    @Override
                    public TreeVisitor<?, ExecutionContext> getVisitor() {
                        return new TreeVisitor<Tree, ExecutionContext>() {
                            @Override
                            public Tree visit(Tree tree, ExecutionContext ctx) {
                                Set<String> seen = /*~~>*/ctx.computeMessageIfAbsent("seen", k -> new HashSet<>());
                                getCursor().getRoot().putMessage("plan", seen);
                                return tree.withMarkers(tree.getMarkers().add(/*~~>*/new AlreadyReplaced(Tree.randomId(), "plan", "sha256")));
                            }
                        };
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void findsTestsThatPinCyclesOrTurnChecksOff() {
        rewriteRun(
          srcTestJava(
            java(
              """
                import org.openrewrite.test.RecipeQualityValidation;
                import org.openrewrite.test.RecipeSpec;

                class PinsTest {
                    void defaults(RecipeSpec spec) {
                        spec.cycles(1).expectedCyclesThatMakeChanges(1)
                          .recipeQualityValidation(RecipeQualityValidation.builder().lightweightAccumulators(false).build());
                    }
                }
                """,
              """
                import org.openrewrite.test.RecipeQualityValidation;
                import org.openrewrite.test.RecipeSpec;

                class PinsTest {
                    void defaults(RecipeSpec spec) {
                        /*~~>*/spec.cycles(1).expectedCyclesThatMakeChanges(1)
                          .recipeQualityValidation(/*~~>*/RecipeQualityValidation.builder().lightweightAccumulators(false).build());
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void leavesRecipeTestsWithoutEvasionsAlone() {
        rewriteRun(
          srcTestJava(
            java(
              """
                import org.openrewrite.test.RecipeSpec;

                class PlainTest {
                    void defaults(RecipeSpec spec) {
                        spec.expectedCyclesThatMakeChanges(2);
                    }
                }
                """
            )
          )
        );
    }
}
