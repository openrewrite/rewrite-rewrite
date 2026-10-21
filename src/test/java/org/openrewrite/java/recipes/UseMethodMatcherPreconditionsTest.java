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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseMethodMatcherPreconditionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseMethodMatcherPreconditions())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void passMatcherToUsesMethod() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              public class SampleRecipe extends Recipe {
                  private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)");

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
                      return Preconditions.check(new UsesMethod<>("com.example.Foo bar(..)"), new JavaIsoVisitor<ExecutionContext>() {
                      });
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              public class SampleRecipe extends Recipe {
                  private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)");

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
                      return Preconditions.check(new UsesMethod<>(BAR), new JavaIsoVisitor<ExecutionContext>() {
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void matchOverridesAndStringConstant() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.DeclaresMethod;
              import org.openrewrite.java.search.UsesMethod;

              class Sample {
                  private static final String BAR_PATTERN = "com.example.Foo bar(..)";
                  private static final MethodMatcher BAR = new MethodMatcher(BAR_PATTERN, true);
                  private static final MethodMatcher BAZ = new MethodMatcher("com.example.Foo baz()", false);

                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(
                              Preconditions.or(
                                      new UsesMethod<>("com.example.Foo bar(..)", true),
                                      new DeclaresMethod<>(BAR_PATTERN, true),
                                      new UsesMethod<>("com.example.Foo baz()")),
                              new JavaIsoVisitor<ExecutionContext>() {
                              });
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.DeclaresMethod;
              import org.openrewrite.java.search.UsesMethod;

              class Sample {
                  private static final String BAR_PATTERN = "com.example.Foo bar(..)";
                  private static final MethodMatcher BAR = new MethodMatcher(BAR_PATTERN, true);
                  private static final MethodMatcher BAZ = new MethodMatcher("com.example.Foo baz()", false);

                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(
                              Preconditions.or(
                                      new UsesMethod<>(BAR),
                                      new DeclaresMethod<>(BAR),
                                      new UsesMethod<>(BAZ)),
                              new JavaIsoVisitor<ExecutionContext>() {
                              });
                  }
              }
              """
          )
        );
    }

    @Test
    void findMethodsVisitorToUsesMethod() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.FindMethodDeclaration;
              import org.openrewrite.java.search.FindMethods;

              class Sample {
                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(
                              Preconditions.and(
                                      new FindMethods("com.example.Foo bar(..)", true).getVisitor(),
                                      Preconditions.not(new FindMethodDeclaration("*..* main(String[])", false).getVisitor())),
                              new JavaIsoVisitor<ExecutionContext>() {
                              });
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.DeclaresMethod;
              import org.openrewrite.java.search.UsesMethod;

              class Sample {
                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(
                              Preconditions.and(
                                      new UsesMethod<>("com.example.Foo bar(..)", true),
                                      Preconditions.not(new DeclaresMethod<>("*..* main(String[])", false))),
                              new JavaIsoVisitor<ExecutionContext>() {
                              });
                  }
              }
              """
          )
        );
    }

    @Test
    void findMethodsVisitorToUsesMethodWithMatcher() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.FindMethods;

              class Sample {
                  private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)");

                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(new FindMethods("com.example.Foo bar(..)", null).getVisitor(), new JavaIsoVisitor<ExecutionContext>() {
                      });
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              class Sample {
                  private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)");

                  TreeVisitor<?, ExecutionContext> getVisitor() {
                      return Preconditions.check(new UsesMethod<>(BAR), new JavaIsoVisitor<ExecutionContext>() {
                      });
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void differentPattern() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Preconditions;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;
                  import org.openrewrite.java.search.UsesMethod;

                  class Sample {
                      private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)");

                      TreeVisitor<?, ExecutionContext> getVisitor() {
                          return Preconditions.check(new UsesMethod<>("com.example.Foo bar()"), new JavaIsoVisitor<ExecutionContext>() {
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void differentMatchOverrides() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Preconditions;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;
                  import org.openrewrite.java.search.UsesMethod;

                  class Sample {
                      private static final MethodMatcher BAR = new MethodMatcher("com.example.Foo bar(..)", true);

                      TreeVisitor<?, ExecutionContext> getVisitor() {
                          return Preconditions.check(new UsesMethod<>("com.example.Foo bar(..)"), new JavaIsoVisitor<ExecutionContext>() {
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void matcherFromOptionOrInstanceField() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Option;
                  import org.openrewrite.Preconditions;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;
                  import org.openrewrite.java.search.UsesMethod;

                  class Sample {
                      @Option(displayName = "Method pattern", description = "The method pattern.", example = "com.example.Foo bar(..)")
                      String methodPattern;

                      private final MethodMatcher bar = new MethodMatcher("com.example.Foo bar(..)");

                      private final MethodMatcher matcher = new MethodMatcher(methodPattern);

                      TreeVisitor<?, ExecutionContext> getVisitor() {
                          return Preconditions.check(
                                  Preconditions.or(new UsesMethod<>(methodPattern), new UsesMethod<>("com.example.Foo bar(..)")),
                                  new JavaIsoVisitor<ExecutionContext>() {
                                  });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void findMethodsRunAsVisitor() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Preconditions;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.search.FindMethods;
                  import org.openrewrite.java.search.UsesType;

                  class Sample {
                      TreeVisitor<?, ExecutionContext> getVisitor() {
                          return Preconditions.check(
                                  new UsesType<>("com.example.Foo", false),
                                  new FindMethods("com.example.Foo bar(..)", true).getVisitor());
                      }

                      TreeVisitor<?, ExecutionContext> getOtherVisitor() {
                          return new FindMethods("com.example.Foo bar(..)", true).getVisitor();
                      }
                  }
                  """
              )
            );
        }
    }
}
