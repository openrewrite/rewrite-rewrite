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
        spec.recipeFromResources(
          "org.openrewrite.java.recipes.FindQualityCheckEvasionsInRecipes",
          "org.openrewrite.java.recipes.FindQualityCheckEvasionsInTests")
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
                                Set<String> seen = /*~~(Keep state across source files in a `ScanningRecipe` accumulator, and within one source file in cursor messages, rather than on the `ExecutionContext` a run shares)~~>*/ctx.computeMessageIfAbsent("seen", k -> new HashSet<>());
                                getCursor().getRoot().putMessage("plan", seen);
                                return tree.withMarkers(tree.getMarkers().add(/*~~(Check the tree for the change already made, rather than reusing the marker rewrite's own `FindAndReplace` uses to carry information)~~>*/new AlreadyReplaced(Tree.randomId(), "plan", "sha256")));
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
                        /*~~(Drop `cycles(..)`: `rewriteRun` already runs one cycle past `expectedCyclesThatMakeChanges`, and that extra cycle checks the recipe settles)~~>*/spec.cycles(1).expectedCyclesThatMakeChanges(1)
                          .recipeQualityValidation(/*~~(Fix what this quality check reports in the recipe, rather than turning the check off in the test)~~>*/RecipeQualityValidation.builder().lightweightAccumulators(false).build());
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void findsMethodReferencesToExecutionContextState() {
        rewriteRun(
          srcMainJava(
            java(
              """
                import org.openrewrite.ExecutionContext;

                import java.util.Map;
                import java.util.function.Supplier;

                class Messages {
                    Supplier<Map<String, Object>> messages(ExecutionContext ctx) {
                        return ctx::getMessages;
                    }
                }
                """,
              """
                import org.openrewrite.ExecutionContext;

                import java.util.Map;
                import java.util.function.Supplier;

                class Messages {
                    Supplier<Map<String, Object>> messages(ExecutionContext ctx) {
                        return /*~~(Keep state across source files in a `ScanningRecipe` accumulator, and within one source file in cursor messages, rather than on the `ExecutionContext` a run shares)~~>*/ctx::getMessages;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void findsTestsThatTurnTypeValidationOff() {
        rewriteRun(
          srcTestJava(
            java(
              """
                import org.openrewrite.test.RecipeSpec;
                import org.openrewrite.test.TypeValidation;

                class LooseTest {
                    void defaults(RecipeSpec spec) {
                        spec.typeValidationOptions(TypeValidation.none());
                    }
                }
                """,
              """
                import org.openrewrite.test.RecipeSpec;
                import org.openrewrite.test.TypeValidation;

                class LooseTest {
                    void defaults(RecipeSpec spec) {
                        spec.typeValidationOptions(/*~~(Fix the missing types, or relax only the type validation that fails, rather than turning all of it off)~~>*/TypeValidation.none());
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
