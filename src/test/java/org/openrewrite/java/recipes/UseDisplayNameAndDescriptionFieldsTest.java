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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseDisplayNameAndDescriptionFieldsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
          .recipe(new UseDisplayNameAndDescriptionFields());
    }

    @DocumentExample
    @Test
    void replaceSimpleStringLiteralMethods() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";

                  String description = "My description.";

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceStringConcatenation() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My" + (" ") + "Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "First line. " +
                              "Second line.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My" + (" ") + "Recipe";

                  String description = "First line. " +
                              "Second line.";

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnlyDisplayName() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return someMethod();
                  }

                  private String someMethod() {
                      return "description";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";

                  @Override
                  public String getDescription() {
                      return someMethod();
                  }

                  private String someMethod() {
                      return "description";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnlyDescription() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return computeName();
                  }

                  private String computeName() {
                      return "My Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return computeName();
                  }

                  private String computeName() {
                      return "My Recipe";
                  }

                  String description = "My description.";

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Test
    void handleEscapedCharacters() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My \\"Recipe\\"";
                  }

                  @Override
                  public String getDescription() {
                      return "Line 1\\nLine 2";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My \\"Recipe\\"";

                  String description = "Line 1\\nLine 2";

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void methodCallsOtherMethods() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Recipe;
                  import org.openrewrite.TreeVisitor;

                  @Value
                  public class MyRecipe extends Recipe {
                      @Override
                      public String getDisplayName() {
                          return computeName();
                      }

                      private String computeName() {
                          return "My Recipe";
                      }

                      @Override
                      public String getDescription() {
                          return computeDescription();
                      }

                      private String computeDescription() {
                          return "My description.";
                      }

                      @Override
                      public TreeVisitor<?, ExecutionContext> getVisitor() {
                          return TreeVisitor.noop();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void notRecipeClass() {
            rewriteRun(
              java(
                """
                  import lombok.Value;

                  @Value
                  public class MyClass {
                      public String getDisplayName() {
                          return "My Name";
                      }

                      public String getDescription() {
                          return "My description.";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fieldsAlreadyExist() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";
                  }
                  """
              )
            );
        }

        @Test
        void notAnnotatedWithLombokValue() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Recipe;
                  import org.openrewrite.TreeVisitor;

                  public class MyRecipe extends Recipe {
                      @Override
                      public String getDisplayName() {
                          return "My Recipe";
                      }

                      @Override
                      public String getDescription() {
                          return "My description.";
                      }

                      @Override
                      public TreeVisitor<?, ExecutionContext> getVisitor() {
                          return TreeVisitor.noop();
                      }
                  }
                  """
              )
            );
        }
    }
}
