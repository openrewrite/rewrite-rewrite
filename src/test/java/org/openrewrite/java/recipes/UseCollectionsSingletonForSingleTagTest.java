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

class UseCollectionsSingletonForSingleTagTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseCollectionsSingletonForSingleTag());
    }

    @DocumentExample
    @Test
    void tagsField() {
        rewriteRun(
          java(
            """
              import lombok.EqualsAndHashCode;
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.HashSet;
              import java.util.Set;

              @EqualsAndHashCode(callSuper = false)
              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = new HashSet<>(Arrays.asList("netty"));
              }
              """,
            """
              import lombok.EqualsAndHashCode;
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @EqualsAndHashCode(callSuper = false)
              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("netty");
              }
              """
          )
        );
    }

    @Test
    void getTagsMethod() {
        rewriteRun(
          java(
            """
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.HashSet;
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
                      return new HashSet<>(Arrays.asList("netty"));
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

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
                      return singleton("netty");
                  }
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedAsList() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.HashSet;
              import java.util.Set;

              import static java.util.Arrays.asList;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = new HashSet<>(asList("RSPEC-S3046"));
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("RSPEC-S3046");
              }
              """
          )
        );
    }

    @Test
    void singletonListCopy() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.HashSet;
              import java.util.Set;

              import static java.util.Collections.singletonList;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = new HashSet<>( singletonList( "RSPEC-S6204" ) );
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("RSPEC-S6204");
              }
              """
          )
        );
    }

    @Test
    void linkedHashSetCopy() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.LinkedHashSet;
              import java.util.Set;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = new LinkedHashSet<>(Arrays.asList("netty"));
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("netty");
              }
              """
          )
        );
    }

    @Test
    void unmodifiableSetCopy() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.Collections;
              import java.util.HashSet;
              import java.util.Set;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("netty")));
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("netty");
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedUnmodifiableSet() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.LinkedHashSet;
              import java.util.Set;

              import static java.util.Arrays.asList;
              import static java.util.Collections.unmodifiableSet;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = unmodifiableSet(new LinkedHashSet<>(asList("netty")));
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("netty");
              }
              """
          )
        );
    }

    @Test
    void composesWithUseTagsField() {
        rewriteRun(
          spec -> spec.recipes(new UseTagsField(), new UseCollectionsSingletonForSingleTag()),
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.HashSet;
              import java.util.Set;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";

                  @Override
                  public Set<String> getTags() {
                      return new HashSet<>(Arrays.asList("netty"));
                  }
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";

                  Set<String> tags = singleton("netty");
              }
              """
          )
        );
    }

    @Test
    void keepImportsStillInUse() {
        rewriteRun(
          java(
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.HashSet;
              import java.util.Set;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = new HashSet<>(Arrays.asList("netty"));
                  Set<String> other = new HashSet<>(Arrays.asList("a", "b"));
              }
              """,
            """
              import lombok.Value;
              import org.openrewrite.Recipe;

              import java.util.Arrays;
              import java.util.HashSet;
              import java.util.Set;

              import static java.util.Collections.singleton;

              @Value
              public class MyRecipe extends Recipe {
                  String displayName = "My Recipe";
                  String description = "My description.";
                  Set<String> tags = singleton("netty");
                  Set<String> other = new HashSet<>(Arrays.asList("a", "b"));
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void multipleTags() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.Arrays;
                  import java.util.HashSet;
                  import java.util.Set;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";
                      Set<String> tags = new HashSet<>(Arrays.asList("liberty", "websphere"));
                  }
                  """
              )
            );
        }

        @Test
        void unmodifiableSetOfMultipleTags() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.HashSet;
                  import java.util.Set;

                  import static java.util.Arrays.asList;
                  import static java.util.Collections.unmodifiableSet;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";
                      Set<String> tags = unmodifiableSet(new HashSet<>(asList("liberty", "websphere")));
                  }
                  """
              )
            );
        }

        @Test
        void arrayOfTags() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.Arrays;
                  import java.util.HashSet;
                  import java.util.Set;

                  @Value
                  public class MyRecipe extends Recipe {
                      private static final String[] TAGS = {"liberty", "websphere"};

                      String displayName = "My Recipe";
                      String description = "My description.";
                      Set<String> tags = new HashSet<>(Arrays.asList(TAGS));
                  }
                  """
              )
            );
        }

        @Test
        void noTags() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.Arrays;
                  import java.util.HashSet;
                  import java.util.Set;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";
                      Set<String> tags = new HashSet<>(Arrays.asList());
                  }
                  """
              )
            );
        }

        @Test
        void otherMethodOfRecipe() {
            rewriteRun(
              java(
                """
                  import lombok.Value;
                  import org.openrewrite.Recipe;

                  import java.util.Arrays;
                  import java.util.HashSet;
                  import java.util.Set;

                  @Value
                  public class MyRecipe extends Recipe {
                      String displayName = "My Recipe";
                      String description = "My description.";

                      Set<String> names() {
                          return new HashSet<>(Arrays.asList("netty"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void localVariableThatIsMutated() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.Recipe;

                  import java.util.Arrays;
                  import java.util.HashSet;
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
                          Set<String> tags = new HashSet<>(Arrays.asList("netty"));
                          tags.addAll(super.getTags());
                          return tags;
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
                  import java.util.Arrays;
                  import java.util.HashSet;
                  import java.util.Set;

                  public class MyClass {
                      Set<String> tags = new HashSet<>(Arrays.asList("netty"));
                  }
                  """
              )
            );
        }
    }
}
