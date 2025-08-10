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

class UseRewriteTestDefaultsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseRewriteTestDefaults())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void shouldRefactorWhenAllTestsUseSameRecipe() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample());
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorWhenOnlyOneTest() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorWhenTestsUseDifferentRecipes() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.FindRecipes()),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleComplexRecipeSpec() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;
              import org.openrewrite.java.JavaParser;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec
                              .recipe(new org.openrewrite.java.recipes.MissingOptionExample())
                              .parser(JavaParser.fromJavaVersion().classpath("junit")),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec
                              .recipe(new org.openrewrite.java.recipes.MissingOptionExample())
                              .parser(JavaParser.fromJavaVersion().classpath("junit")),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;
              import org.openrewrite.java.JavaParser;

              class MyTest implements RewriteTest {

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec
                              .recipe(new org.openrewrite.java.recipes.MissingOptionExample())
                              .parser(JavaParser.fromJavaVersion().classpath("junit"));
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorWhenSomeTestsHaveDifferentSpecs() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()).expectedCyclesThatMakeChanges(2),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorWhenDefaultsAlreadyExists() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.FindRecipes());
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleTestsWithNoRecipeSpec() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleNestedClasses() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Nested
                  class NestedTest {
                      @Test
                      void test3() {
                          rewriteRun(
                              spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                              org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                          );
                      }

                      @Test
                      void test4() {
                          rewriteRun(
                              spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                              org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                          );
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample());
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Nested
                  class NestedTest {
                      @Test
                      void test3() {
                          rewriteRun(
                              spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                              org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                          );
                      }

                      @Test
                      void test4() {
                          rewriteRun(
                              spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                              org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                          );
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldPreserveOtherRewriteRunOverloads() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }

                  @Test
                  void test3() {
                      var input = "class C {}";
                      rewriteRun(
                          spec -> spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample()),
                          org.openrewrite.java.Assertions.java(input, input)
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample());
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }

                  @Test
                  void test3() {
                      var input = "class C {}";
                      rewriteRun(
                          org.openrewrite.java.Assertions.java(input, input)
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleMethodReference() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {
                  @Test
                  void test1() {
                      rewriteRun(
                          this::configureSpec,
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          this::configureSpec,
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }

                  private void configureSpec(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyTest implements RewriteTest {

                  @Override
                  public void defaults(RecipeSpec spec) {
                      configureSpec(spec);
                  }

                  @Test
                  void test1() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class A {}", "class A {}")
                      );
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                          org.openrewrite.java.Assertions.java("class B {}", "class B {}")
                      );
                  }

                  private void configureSpec(RecipeSpec spec) {
                      spec.recipe(new org.openrewrite.java.recipes.MissingOptionExample());
                  }
              }
              """
          )
        );
    }
}
