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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveImportBeforeAddImportTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveImportBeforeAddImport())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void swapMaybeAddBeforeMaybeRemove() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeAddImport("com.example.NewClass");
                              maybeRemoveImport("com.example.OldClass");
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeRemoveImport("com.example.OldClass");
                              maybeAddImport("com.example.NewClass");
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenRemoveBeforeAdd() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeRemoveImport("com.example.OldClass");
                              maybeAddImport("com.example.NewClass");
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotConsecutive() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeAddImport("com.example.NewClass");
                              J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                              maybeRemoveImport("com.example.OldClass");
                              return m;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void swapMultiplePairs() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeAddImport("com.example.NewClass1");
                              maybeRemoveImport("com.example.OldClass1");
                              maybeAddImport("com.example.NewClass2");
                              maybeRemoveImport("com.example.OldClass2");
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              maybeRemoveImport("com.example.OldClass1");
                              maybeRemoveImport("com.example.OldClass2");
                              maybeAddImport("com.example.NewClass1");
                              maybeAddImport("com.example.NewClass2");
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForNonRecipeClass() {
        rewriteRun(
          java(
            """
              public class NotARecipe {
                  public void doSomething() {
                      maybeAddImport("com.example.NewClass");
                      maybeRemoveImport("com.example.OldClass");
                  }

                  void maybeAddImport(String s) {}
                  void maybeRemoveImport(String s) {}
              }
              """
          )
        );
    }
}
