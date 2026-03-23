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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseTagsFieldTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseTagsField());
    }

    @DocumentExample
    @Test
    void replaceSetOfCall() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              import java.util.Set;

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
                  public Set<String> getTags() {
                      return Set.of("tag1", "tag2");
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

              import java.util.Set;

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

                  Set<String> tags = Set.of("tag1", "tag2");

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
    void replaceCollectionsUnmodifiableSet() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              import java.util.Collections;
              import java.util.HashSet;
              import java.util.Set;

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
                  public Set<String> getTags() {
                      return Collections.unmodifiableSet(new HashSet<>(super.getTags()));
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

              import java.util.Collections;
              import java.util.HashSet;
              import java.util.Set;

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

                  Set<String> tags = Collections.unmodifiableSet(new HashSet<>(super.getTags()));

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
    void notAnnotatedWithLombokValue() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              import java.util.Set;

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
                  public Set<String> getTags() {
                      return Set.of("tag1", "tag2");
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import lombok.Getter;
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              import java.util.Set;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Getter
                  final Set<String> tags = Set.of("tag1", "tag2");

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
        void methodWithMultipleStatements() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Recipe;
                  import org.openrewrite.TreeVisitor;

                  import java.util.HashSet;
                  import java.util.Set;

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
                      public Set<String> getTags() {
                          Set<String> tags = new HashSet<>(super.getTags());
                          tags.add("my-tag");
                          return tags;
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

                  import java.util.Set;

                  @Value
                  public class MyClass {
                      public Set<String> getTags() {
                          return Set.of("tag1", "tag2");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fieldAlreadyExists() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.Set;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";
                      Set<String> tags = Set.of("tag1", "tag2");
                  }
                  """
              )
            );
        }
    }
}
