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

import static org.openrewrite.java.Assertions.java;

class InlineNestedVisitorClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineNestedVisitorClass())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void inlineNestedVisitorClass() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                          if (literal.getValue() == null) {
                              return literal;
                          }
                          return super.visitLiteral(literal, ctx);
                      }
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                              if (literal.getValue() == null) {
                                  return literal;
                              }
                              return super.visitLiteral(literal, ctx);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineVisitorWithInstanceFields() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      int literals;

                      @Override
                      public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                          literals++;
                          return literal;
                      }
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          int literals;

                          @Override
                          public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                              literals++;
                              return literal;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void shiftMultiLineCommentsAlongWithTheCode() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      /**
                       * Skips literals that are already null.
                       *
                       * @param literal the literal to check
                       */
                      @Override
                      public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                          /*
                           * Nothing to do here.
                           */
                          return literal;
                      }
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          /**
                           * Skips literals that are already null.
                           *
                           * @param literal the literal to check
                           */
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                              /*
                               * Nothing to do here.
                               */
                              return literal;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenReferencedMoreThanOnce() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  static TreeVisitor<?, ExecutionContext> another() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenPreconditionWraps() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesType;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(new UsesType<>("java.util.List", true), new MyRecipeVisitor());
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenNotPrivate() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistConstantsOntoTheRecipeClass() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {

                      private static final MethodMatcher ADD = new MethodMatcher("java.util.List add(..)");
                      private static final MethodMatcher REMOVE = new MethodMatcher("java.util.List remove(..)");

                      @Override
                      public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                          if (ADD.matches(method) || REMOVE.matches(method)) {
                              return method;
                          }
                          return super.visitMethodInvocation(method, ctx);
                      }
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  private static final MethodMatcher ADD = new MethodMatcher("java.util.List add(..)");
                  private static final MethodMatcher REMOVE = new MethodMatcher("java.util.List remove(..)");

                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              if (ADD.matches(method) || REMOVE.matches(method)) {
                                  return method;
                              }
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
    void hoistConstantsAfterTheFieldsTheyReferTo() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  private static final String TYPE = "java.util.List";

                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private static final MethodMatcher ADD = new MethodMatcher(TYPE + " add(..)");
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  private static final String TYPE = "java.util.List";

                  private static final MethodMatcher ADD = new MethodMatcher(TYPE + " add(..)");

                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotHoistConstantsThatCollideWithAnExistingField() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  private static final String ADD = "add";

                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private static final MethodMatcher ADD = new MethodMatcher("java.util.List add(..)");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenStaticMethodDeclared() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private static boolean isNull(J.Literal literal) {
                          return literal.getValue() == null;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenStaticMembersDeclared() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      static MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenConstructorDeclared() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      MyRecipeVisitor() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineDocumentedVisitor() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  /**
                   * Worth keeping around.
                   */
                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistConstantsAfterAFieldDeclaredLast() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private static final MethodMatcher ADD = new MethodMatcher(TYPE + " add(..)");
                  }

                  private static final String TYPE = "java.util.List";
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                      };
                  }

                  private static final String TYPE = "java.util.List";

                  private static final MethodMatcher ADD = new MethodMatcher(TYPE + " add(..)");
              }
              """
          )
        );
    }

    @Test
    void doNotInlineVisitorThatAlsoImplementsAnInterface() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new MyRecipeVisitor();
                  }

                  private static class MyRecipeVisitor extends JavaIsoVisitor<ExecutionContext> implements Runnable {
                      @Override
                      public void run() {
                      }
                  }
              }
              """
          )
        );
    }
}
