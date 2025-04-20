/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.recipes;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.yaml.Assertions.yaml;

class ExamplesExtractorTest implements RewriteTest {

    @Language("java")
    private static final String RECIPE_JAVA_FILE = """
      package org.openrewrite.staticanalysis;

      import org.openrewrite.ExecutionContext;
      import org.openrewrite.Recipe;
      import org.openrewrite.TreeVisitor;
      import org.openrewrite.java.JavaIsoVisitor;

      public class ChainStringBuilderAppendCalls extends Recipe {
          @Override
          public String getDisplayName() {
              return "Chain `StringBuilder.append()` calls";
          }

          @Override
          public String getDescription() {
              return "String concatenation within calls to `StringBuilder.append()` causes unnecessary memory allocation. Except for concatenations of String literals, which are joined together at compile time. Replaces inefficient concatenations with chained calls to `StringBuilder.append()`.";
          }

          @Override
          public TreeVisitor<?, ExecutionContext> getVisitor() {
              return new JavaIsoVisitor<>(){
              };
          }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExamplesExtractor());
    }

    @DocumentExample
    @Test
    void extractJavaExampleWithDefault() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            srcMainJava(java(RECIPE_JAVA_FILE, SourceSpec::skip)),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class ChainStringBuilderAppendCallsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new ChainStringBuilderAppendCalls());
                      }

                      @DocumentExample(value = "Objects concatenation.")
                      @Test
                      void objectsConcatenation() {
                          rewriteRun(
                            java(
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A" + op + "B");
                                        sb.append(1 + op + 2);
                                    }
                                }
                                \""",
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A").append(op).append("B");
                                        sb.append(1).append(op).append(2);
                                    }
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null, // newly created
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.staticanalysis.ChainStringBuilderAppendCalls
                examples:
                - description: Objects concatenation.
                  sources:
                  - before: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A" + op + "B");
                              sb.append(1 + op + 2);
                          }
                      }
                    after: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A").append(op).append("B");
                              sb.append(1).append(op).append(2);
                          }
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void existingExampleFile() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            //language=java
            srcTestJava(
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.RemoveUnusedImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class RemoveUnusedImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new RemoveUnusedImports());
                      }

                      @DocumentExample
                      @Test
                      void removeUnusedImports() {
                          rewriteRun(
                            java(
                              ""\"
                              import java.util.List;
                              class A {}
                              ""\",
                              ""\"
                              class A {}
                              ""\"
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              """
                ---
                """,
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.RemoveUnusedImports
                examples:
                - description: ''
                  sources:
                  - before: |
                      import java.util.List;
                      class A {}
                    after: |
                      class A {}
                    language: java
                \n""",
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void parserTestWithoutRecipe() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.java;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class ParserTest implements RewriteTest {

                      @DocumentExample
                      @Test
                      void parseA() {
                          rewriteRun(java("class A {]"));
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void extractJavaExampleRecipeInSpec() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            java(RECIPE_JAVA_FILE, SourceSpec::skip),
            // language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class ChainStringBuilderAppendCallsTest implements RewriteTest {

                      @DocumentExample("Objects concatenation.")
                      @Test
                      void objectsConcatenation() {
                          rewriteRun(
                            spec -> spec.recipe(new ChainStringBuilderAppendCalls()),
                            java(
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A" + op + "B");
                                        sb.append(1 + op + 2);
                                    }
                                }
                                \""",
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A").append(op).append("B");
                                        sb.append(1).append(op).append(2);
                                    }
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null,
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.staticanalysis.ChainStringBuilderAppendCalls
                examples:
                - description: Objects concatenation.
                  sources:
                  - before: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A" + op + "B");
                              sb.append(1 + op + 2);
                          }
                      }
                    after: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A").append(op).append("B");
                              sb.append(1).append(op).append(2);
                          }
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void extractJavaExampleWithNoDescription() {
        // language=java
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            java(RECIPE_JAVA_FILE, SourceSpec::skip),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class ChainStringBuilderAppendCallsTest implements RewriteTest {

                      @DocumentExample
                      @Test
                      void objectsConcatenation() {
                          rewriteRun(
                            spec -> spec.recipe(new ChainStringBuilderAppendCalls()),
                            java(
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A" + op + "B");
                                        sb.append(1 + op + 2);
                                    }
                                }
                                \""",
                              \"""
                                class A {
                                    void method1() {
                                        StringBuilder sb = new StringBuilder();
                                        String op = "+";
                                        sb.append("A").append(op).append("B");
                                        sb.append(1).append(op).append(2);
                                    }
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null,
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.staticanalysis.ChainStringBuilderAppendCalls
                examples:
                - description: ''
                  sources:
                  - before: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A" + op + "B");
                              sb.append(1 + op + 2);
                          }
                      }
                    after: |
                      class A {
                          void method1() {
                              StringBuilder sb = new StringBuilder();
                              String op = "+";
                              sb.append("A").append(op).append("B");
                              sb.append(1).append(op).append(2);
                          }
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void extractParameters() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            // language=java
            java(
              """
                package org.openrewrite.staticanalysis;

                import lombok.EqualsAndHashCode;
                import lombok.Value;
                import org.openrewrite.*;
                import org.openrewrite.internal.lang.NonNullApi;import org.openrewrite.internal.lang.Nullable;
                import org.openrewrite.java.JavaIsoVisitor;

                import java.util.List;

                @Value
                @EqualsAndHashCode(callSuper = true)
                @NonNullApi
                public class DeclarationSiteTypeVariance extends Recipe {

                    @Option(displayName = "Variant types",
                            description = "A list of well-known classes that have in/out type variance.",
                            example = "java.util.function.Function<IN, OUT>")
                    List<String> variantTypes;

                    @Option(displayName = "Excluded bounds",
                            description = "A list of bounds that should not receive explicit variance. Globs supported.",
                            example = "java.lang.*",
                            required = false)
                    @Nullable
                    List<String> excludedBounds;

                    @Option(displayName = "Exclude final classes",
                            description = "If true, do not add `? extends` variance to final classes. " +
                                          "`? super` variance will be added regardless of finality.",
                            required = false)
                    @Nullable
                    Boolean excludeFinalClasses;

                    @Override
                    public TreeVisitor<?, ExecutionContext> getVisitor() {
                        return new JavaIsoVisitor<>() {
                        };
                    }
                }
                """
            ),
            // language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.config.Environment;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;
                  import org.openrewrite.test.SourceSpec;

                  import java.util.List;

                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.openrewrite.java.Assertions.java;

                  class DeclarationSiteTypeVarianceTest implements RewriteTest {

                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new DeclarationSiteTypeVariance(
                              List.of("java.util.function.Function<IN, OUT>"),
                              List.of("java.lang.*"),
                              true
                          ));
                      }

                      @DocumentExample
                      @Test
                      void inOutVariance() {
                          rewriteRun(
                              java(
                                  \"""
                                    interface In {}
                                    interface Out {}
                                    \"""
                              ),
                              java(
                                  \"""
                                    import java.util.function.Function;
                                    class Test {
                                        void test(Function<In, Out> f) {
                                        }
                                    }
                                    \""",
                                  \"""
                                    import java.util.function.Function;
                                    class Test {
                                        void test(Function<? super In, ? extends Out> f) {
                                        }
                                    }
                                    \"""
                              )
                          );
                      }

                      @DocumentExample
                      @Test
                      void invariance() {
                          rewriteRun(
                              spec -> spec.recipe(new DeclarationSiteTypeVariance(
                                  List.of("java.util.function.Function<INVARIANT, OUT>"),
                                  List.of("java.lang.*"),
                                  null
                              )),
                              java(
                                  \"""
                                    interface In {}
                                    interface Out {}
                                    \"""
                              ),
                              java(
                                  \"""
                                    import java.util.function.Function;
                                    class Test {
                                        void test(Function<In, Out> f) {
                                        }
                                    }
                                    \""",
                                  \"""
                                    import java.util.function.Function;
                                    class Test {
                                        void test(Function<In, ? extends Out> f) {
                                        }
                                    }
                                    \"""
                              )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null,
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.staticanalysis.DeclarationSiteTypeVariance
                examples:
                - description: ''
                  parameters:
                  - List.of("java.util.function.Function<IN, OUT>")
                  - List.of("java.lang.*")
                  - 'true'
                  sources:
                  - before: |
                      interface In {}
                      interface Out {}
                    language: java
                  - before: |
                      import java.util.function.Function;
                      class Test {
                          void test(Function<In, Out> f) {
                          }
                      }
                    after: |
                      import java.util.function.Function;
                      class Test {
                          void test(Function<? super In, ? extends Out> f) {
                          }
                      }
                    language: java
                - description: ''
                  parameters:
                  - List.of("java.util.function.Function<INVARIANT, OUT>")
                  - List.of("java.lang.*")
                  - 'null'
                  sources:
                  - before: |
                      interface In {}
                      interface Out {}
                    language: java
                  - before: |
                      import java.util.function.Function;
                      class Test {
                          void test(Function<In, Out> f) {
                          }
                      }
                    after: |
                      import java.util.function.Function;
                      class Test {
                          void test(Function<In, ? extends Out> f) {
                          }
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void yamlRecipeFromActiveRecipes() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            // language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.java.migrate.net;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.config.Environment;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class JavaNetAPIsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(
                            Environment.builder()
                              .scanRuntimeClasspath("org.openrewrite.java.migrate.net")
                              .build()
                              .activateRecipes("org.openrewrite.java.migrate.net.JavaNetAPIs"));
                      }

                      @DocumentExample
                      @Test
                      void multicastSocketGetTTLToGetTimeToLive() {
                          //language=java
                          rewriteRun(
                            java(
                              ""\"
                                package org.openrewrite.example;

                                import java.net.MulticastSocket;

                                public class Test {
                                    public static void method() {
                                        MulticastSocket s = new MulticastSocket(0);
                                        s.getTTL();
                                    }
                                }
                                ""\",
                              ""\"
                                package org.openrewrite.example;

                                import java.net.MulticastSocket;

                                public class Test {
                                    public static void method() {
                                        MulticastSocket s = new MulticastSocket(0);
                                        s.getTimeToLive();
                                    }
                                }
                                ""\"
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null, """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.migrate.net.JavaNetAPIs
                examples:
                - description: ''
                  sources:
                  - before: |
                      package org.openrewrite.example;

                      import java.net.MulticastSocket;

                      public class Test {
                          public static void method() {
                              MulticastSocket s = new MulticastSocket(0);
                              s.getTTL();
                          }
                      }
                    after: |
                      package org.openrewrite.example;

                      import java.net.MulticastSocket;

                      public class Test {
                          public static void method() {
                              MulticastSocket s = new MulticastSocket(0);
                              s.getTimeToLive();
                          }
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }


    @Test
    void yamlRecipeFromResources() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            // language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.java.migrate.net;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.config.Environment;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class JavaNetAPIsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipeFromResources("org.openrewrite.java.migrate.net.JavaNetAPIs");
                      }

                      @DocumentExample
                      @Test
                      void multicastSocketGetTTLToGetTimeToLive() {
                          //language=java
                          rewriteRun(java("class A {}", "class B {}"));
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null, """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.migrate.net.JavaNetAPIs
                examples:
                - description: ''
                  sources:
                  - before: class A {}
                    after: class B {}
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void textBlockAsParameter() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            // language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.yaml;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.Issue;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.yaml.Assertions.yaml;

                  class MergeYamlTest implements RewriteTest {

                      @DocumentExample
                      @Test
                      void nonExistentBlock() {
                          rewriteRun(
                            spec -> spec.recipe(new MergeYaml(
                              "$.spec",
                              //language=yaml
                              ""\"
                                lifecycleRule:
                                    - action:
                                          type: Delete
                                      condition:
                                          age: 7
                                ""\",
                              false,
                              null,
                              null,
                              null
                            )),
                            yaml(
                              ""\"
                                apiVersion: storage.cnrm.cloud.google.com/v1beta1
                                kind: StorageBucket
                                spec:
                                    bucketPolicyOnly: true
                                ""\",
                              ""\"
                                apiVersion: storage.cnrm.cloud.google.com/v1beta1
                                kind: StorageBucket
                                spec:
                                    bucketPolicyOnly: true
                                    lifecycleRule:
                                        - action:
                                              type: Delete
                                          condition:
                                              age: 7
                                ""\"
                            )
                          );
                      }
                  }
                  """
              )
            ),
            //language=yaml
            yaml(
              null,
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.yaml.MergeYaml
                examples:
                - description: ''
                  parameters:
                  - $.spec
                  - |
                    lifecycleRule:
                        - action:
                              type: Delete
                          condition:
                              age: 7
                  - 'false'
                  - 'null'
                  - 'null'
                  - 'null'
                  sources:
                  - before: |
                      apiVersion: storage.cnrm.cloud.google.com/v1beta1
                      kind: StorageBucket
                      spec:
                          bucketPolicyOnly: true
                    after: |
                      apiVersion: storage.cnrm.cloud.google.com/v1beta1
                      kind: StorageBucket
                      spec:
                          bucketPolicyOnly: true
                          lifecycleRule:
                              - action:
                                    type: Delete
                                condition:
                                    age: 7
                    language: yaml
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            )
          )
        );
    }

    @Test
    void singleProjectWithTwoRecipeTests() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            yaml(
              null, // newly created
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.OrderImports
                examples:
                - description: ''
                  parameters:
                  - 'null'
                  sources:
                  - before: |
                      import java.util.List;
                      class A {
                      }
                    after: |
                      class A {
                      }
                    language: java
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.RemoveUnusedImports
                examples:
                - description: ''
                  sources:
                  - before: |
                      import java.util.List;
                      class A {
                      }
                    after: |
                      class A {
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.OrderImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class OrderImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new OrderImports(null));
                      }

                      @DocumentExample
                      @Test
                      void orderImports() {
                          rewriteRun(
                            java(
                              ""\"
                                import java.util.List;
                                class A {
                                }
                                ""\",
                              ""\"
                                class A {
                                }
                                ""\"
                            )
                          );
                      }
                  }
                  """
              ),
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.RemoveUnusedImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class RemoveUnusedImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new RemoveUnusedImports());
                      }

                      @DocumentExample
                      @Test
                      void removeUnusedImports() {
                          rewriteRun(
                            java(
                              \"""
                                import java.util.List;
                                class A {
                                }
                                \""",
                              \"""
                                class A {
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void singleTestWithTwoSources() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "project",
            yaml(
              null, // newly created
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.OrderImports
                examples:
                - description: ''
                  parameters:
                  - 'null'
                  sources:
                  - before: |
                      import java.util.List;
                      class A {
                      }
                    after: |
                      class A {
                      }
                    language: java
                  - before: |
                      import java.util.List;
                      class B {
                      }
                    after: |
                      class B {
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.OrderImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class OrderImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new OrderImports(null));
                      }

                      @DocumentExample
                      @Test
                      void orderImports() {
                          rewriteRun(
                            java(
                              ""\"
                                import java.util.List;
                                class A {
                                }
                                ""\",
                              ""\"
                                class A {
                                }
                                ""\"
                            ),
                            java(
                              \"""
                                import java.util.List;
                                class B {
                                }
                                \""",
                              \"""
                                class B {
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void twoProjectsWrittenToSeparateNewFiles() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "projectA",
            yaml(
              null, // newly created
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.OrderImports
                examples:
                - description: ''
                  parameters:
                  - 'null'
                  sources:
                  - before: |
                      import java.util.List;
                      class A {
                      }
                    after: |
                      class A {
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.OrderImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class OrderImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new OrderImports(null));
                      }

                      @DocumentExample
                      @Test
                      void orderImports() {
                          rewriteRun(
                            java(
                              ""\"
                                import java.util.List;
                                class A {
                                }
                                ""\",
                              ""\"
                                class A {
                                }
                                ""\"
                            )
                          );
                      }
                  }
                  """
              )
            )
          ),
          mavenProject(
            "projectB",
            //language=yaml
            yaml(
              null, // newly created
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.RemoveUnusedImports
                examples:
                - description: ''
                  sources:
                  - before: |
                      import java.util.List;
                      class B {
                      }
                    after: |
                      class B {
                      }
                    language: java
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.RemoveUnusedImports;
                  import org.openrewrite.test.RecipeSpec;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class RemoveUnusedImportsTest implements RewriteTest {
                      @Override
                      public void defaults(RecipeSpec spec) {
                          spec.recipe(new RemoveUnusedImports());
                      }

                      @DocumentExample
                      @Test
                      void removeUnusedImports() {
                          rewriteRun(
                            java(
                              \"""
                                import java.util.List;
                                class B {
                                }
                                \""",
                              \"""
                                class B {
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void twoProjectsWrittenToSeparateExistingFiles() {
        //language=yaml
        rewriteRun(
          mavenProject(
            "projectA",
            yaml(
              "---",
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.OrderImports
                examples:
                - description: ''
                  parameters:
                  - 'null'
                  sources:
                  - before: |
                      import java.util.List;
                      class A {
                      }
                    after: |
                      class A {
                      }
                    language: java
                \n""",
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.OrderImports;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class OrderImportsTest implements RewriteTest {
                      @DocumentExample
                      @Test
                      void orderImports() {
                          rewriteRun(
                            spec -> spec.recipe(new OrderImports(null)),
                            java(
                              ""\"
                                import java.util.List;
                                class A {
                                }
                                ""\",
                              ""\"
                                class A {
                                }
                                ""\"
                            )
                          );
                      }
                  }
                  """
              )
            )
          ),
          mavenProject(
            "projectB",
            //language=yaml
            yaml(
              "---",
              """
                ---
                type: specs.openrewrite.org/v1beta/example
                recipeName: org.openrewrite.java.RemoveUnusedImports
                examples:
                - description: ''
                  sources:
                  - before: |
                      import java.util.List;
                      class B {
                      }
                    after: |
                      class B {
                      }
                    language: java
                \n""",
              spec -> spec.path("src/main/resources/META-INF/rewrite/examples.yml")
            ),
            //language=java
            srcTestJava(
              java(
                """
                  package org.openrewrite.staticanalysis;

                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.DocumentExample;
                  import org.openrewrite.java.RemoveUnusedImports;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class RemoveUnusedImportsTest implements RewriteTest {
                      @DocumentExample
                      @Test
                      void removeUnusedImports() {
                          rewriteRun(
                            spec -> spec.recipe(new RemoveUnusedImports()),
                            java(
                              \"""
                                import java.util.List;
                                class B {
                                }
                                \""",
                              \"""
                                class B {
                                }
                                \"""
                            )
                          );
                      }
                  }
                  """
              )
            )
          )
        );
    }
}
