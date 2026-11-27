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

class GroupNoChangeTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GroupNoChangeTests())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void groupNoChangeTestsAfterLastChangeTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        java(
                          \"""
                            class A {}
                            \""",
                          \"""
                            class B {}
                            \"""
                        )
                      );
                  }

                  @Test
                  void doesNotChangeInterface() {
                      rewriteRun(
                        java(
                          \"""
                            interface A {

                                void m();
                            }
                            \"""
                        )
                      );
                  }

                  @Test
                  void changesAgain() {
                      rewriteRun(
                        java(
                          \"""
                            class A {}
                            \""",
                          \"""
                            class B {}
                            \"""
                        )
                      );
                  }

                  // Records are out of scope
                  @Test
                  void record() {
                      rewriteRun(
                        java(
                          \"""
                            record A() {}
                            \"""
                        )
                      );
                  }

                  @Test
                  void enumeration() {
                      rewriteRun(java("enum A {}"));
                  }

                  private static String helper() {
                      return "";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        java(
                          \"""
                            class A {}
                            \""",
                          \"""
                            class B {}
                            \"""
                        )
                      );
                  }

                  @Test
                  void changesAgain() {
                      rewriteRun(
                        java(
                          \"""
                            class A {}
                            \""",
                          \"""
                            class B {}
                            \"""
                        )
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(
                            java(
                              \"""
                                interface A {

                                    void m();
                                }
                                \"""
                            )
                          );
                      }

                      // Records are out of scope
                      @Test
                      void record() {
                          rewriteRun(
                            java(
                              \"""
                                record A() {}
                                \"""
                            )
                          );
                      }

                      @Test
                      void enumeration() {
                          rewriteRun(java("enum A {}"));
                      }
                  }

                  private static String helper() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeIntoExistingNoChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        java("class A {}", "class B {}")
                      );
                  }

                  @Test
                  void straggler() {
                      rewriteRun(
                        java("interface A {}")
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void alreadyGrouped() {
                          rewriteRun(
                            java("enum A {}")
                          );
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        java("class A {}", "class B {}")
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void alreadyGrouped() {
                          rewriteRun(
                            java("enum A {}")
                          );
                      }

                      @Test
                      void straggler() {
                          rewriteRun(
                            java("interface A {}")
                          );
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void namesThatSignalNoChangeWinOverChangeVerbs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void replaceClass() {
                      rewriteRun(
                        java("class A {}", "class B {}")
                      );
                  }

                  @Test
                  void replaceInterface() {
                      rewriteRun(
                        java("interface A {}")
                      );
                  }

                  @Test
                  void replaceNotWhenAlreadyB() {
                      rewriteRun(
                        java("class B {}")
                      );
                  }

                  @Test
                  void keepEnum() {
                      rewriteRun(
                        java("enum A {}")
                      );
                  }

                  @Test
                  void keepAnnotation() {
                      rewriteRun(
                        java("@interface A {}")
                      );
                  }

                  @Test
                  void simplifiesRecord() {
                      rewriteRun(
                        java("record A() {}")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void replaceClass() {
                      rewriteRun(
                        java("class A {}", "class B {}")
                      );
                  }

                  @Test
                  void replaceInterface() {
                      rewriteRun(
                        java("interface A {}")
                      );
                  }

                  @Test
                  void simplifiesRecord() {
                      rewriteRun(
                        java("record A() {}")
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void replaceNotWhenAlreadyB() {
                          rewriteRun(
                            java("class B {}")
                          );
                      }

                      @Test
                      void keepEnum() {
                          rewriteRun(
                            java("enum A {}")
                          );
                      }

                      @Test
                      void keepAnnotation() {
                          rewriteRun(
                            java("@interface A {}")
                          );
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedSourceSpecsAndSpecLambdas() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.java.Assertions.srcMainJava;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        srcMainJava(java("class A {}", "class B {}"))
                      );
                  }

                  @Test
                  void inSourceSet() {
                      rewriteRun(
                        spec -> spec.cycles(1),
                        srcMainJava(java("interface A {}", s -> s.path("A.java")))
                      );
                  }

                  @Test
                  void mixed() {
                      rewriteRun(
                        srcMainJava(
                          java("interface A {}"),
                          java("class A {}", "class B {}")
                        )
                      );
                  }

                  @Test
                  void twice() {
                      rewriteRun(java("interface A {}"));
                      rewriteRun(java("enum A {}"));
                  }

                  @Test
                  void multipleSources() {
                      rewriteRun(java("interface A {}"), java("enum B {}"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.java.Assertions.srcMainJava;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                        srcMainJava(java("class A {}", "class B {}"))
                      );
                  }

                  @Test
                  void mixed() {
                      rewriteRun(
                        srcMainJava(
                          java("interface A {}"),
                          java("class A {}", "class B {}")
                        )
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void inSourceSet() {
                          rewriteRun(
                            spec -> spec.cycles(1),
                            srcMainJava(java("interface A {}", s -> s.path("A.java")))
                          );
                      }

                      @Test
                      void twice() {
                          rewriteRun(java("interface A {}"));
                          rewriteRun(java("enum A {}"));
                      }

                      @Test
                      void multipleSources() {
                          rewriteRun(java("interface A {}"), java("enum B {}"));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepContinuationIndentOfFile() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                              java("class A {}", "class B {}")
                      );
                  }

                  @Test
                  void alreadyAtTarget() {
                      rewriteRun(
                              java(
                                      \"""
                                      class B {}
                                      \"""
                              )
                      );
                  }

                  @Test
                  void doesNotDowngrade() {
                      rewriteRun(
                              java("interface B {}")
                      );
                  }

                  @Test
                  void doesNotChangeEnum() {
                      rewriteRun(
                              java("enum B {}")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(
                              java("class A {}", "class B {}")
                      );
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void alreadyAtTarget() {
                          rewriteRun(
                                  java(
                                          \"""
                                          class B {}
                                          \"""
                                  )
                          );
                      }

                      @Test
                      void doesNotDowngrade() {
                          rewriteRun(
                                  java("interface B {}")
                          );
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(
                                  java("enum B {}")
                          );
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepTestsThatWouldReferToTheNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void doesNotChangeInterface() {
                      rewriteRun(java("interface A {}"));
                  }

                  @Test
                  void qualifiedWithThis() {
                      this.rewriteRun(java("interface B {}"));
                  }

                  @Test
                  void namedAfterClass() {
                      rewriteRun(java(getClass().getSimpleName()));
                  }

                  @Test
                  void doesNotChangeEnum() {
                      rewriteRun(java("enum A {}"));
                  }

                  @Test
                  void printsInstance() {
                      rewriteRun(java(toString()));
                  }

                  @Test
                  void check() {
                      rewriteRun(java("enum B {}"));
                  }

                  @Test
                  void doesNotChangeAnnotation() {
                      rewriteRun(java("@interface A {}"));
                  }

                  private void check(String before) {
                      rewriteRun(java(before));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void qualifiedWithThis() {
                      this.rewriteRun(java("interface B {}"));
                  }

                  @Test
                  void namedAfterClass() {
                      rewriteRun(java(getClass().getSimpleName()));
                  }

                  @Test
                  void printsInstance() {
                      rewriteRun(java(toString()));
                  }

                  @Test
                  void check() {
                      rewriteRun(java("enum B {}"));
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface A {}"));
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(java("enum A {}"));
                      }

                      @Test
                      void doesNotChangeAnnotation() {
                          rewriteRun(java("@interface A {}"));
                      }
                  }

                  private void check(String before) {
                      rewriteRun(java(before));
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeIntoEmptyNoChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void doesNotChangeInterface() {
                      rewriteRun(java("interface A {}"));
                  }

                  @Nested
                  class NoChange {}
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface A {}"));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepTestsThatMayExpectAChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.DocumentExample;
              import org.openrewrite.test.RewriteTest;
              import org.openrewrite.test.SourceSpecs;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void removesNothingHere() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void removeInterface() {
                      rewriteRun(java("interface A {}"));
                  }

                  @DocumentExample
                  @Test
                  void documented() {
                      rewriteRun(java("interface A {}"));
                  }

                  @Test
                  void afterRecipe() {
                      rewriteRun(
                        spec -> spec.afterRecipe(run -> {}),
                        java("interface A {}")
                      );
                  }

                  @Test
                  void afterSourceSpec() {
                      rewriteRun(java("interface A {}", s -> s.after(actual -> actual)));
                  }

                  @Test
                  void variable() {
                      SourceSpecs a = java("interface A {}");
                      rewriteRun(a);
                  }

                  @Test
                  void extraAssertion() {
                      rewriteRun(java("interface A {}"));
                      assert true;
                  }

                  @Test
                  void sharedSpec() {
                      rewriteRun(sharedSpec("enum A {}"));
                  }

                  @Test
                  void doesNotChangeInterface() {
                      rewriteRun(java("interface C {}"));
                  }

                  @Test
                  void doesNotChangeEnum() {
                      rewriteRun(java("enum C {}"));
                  }

                  @Test
                  void doesNotChangeAnnotation() {
                      rewriteRun(java("@interface C {}"));
                  }

                  private static SourceSpecs sharedSpec(String before) {
                      return java(before);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.DocumentExample;
              import org.openrewrite.test.RewriteTest;
              import org.openrewrite.test.SourceSpecs;

              import static org.openrewrite.java.Assertions.java;

              class MyRecipeTest implements RewriteTest {

                  @Test
                  void changes() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void removesNothingHere() {
                      rewriteRun(java("class A {}", "class B {}"));
                  }

                  @Test
                  void removeInterface() {
                      rewriteRun(java("interface A {}"));
                  }

                  @DocumentExample
                  @Test
                  void documented() {
                      rewriteRun(java("interface A {}"));
                  }

                  @Test
                  void afterRecipe() {
                      rewriteRun(
                        spec -> spec.afterRecipe(run -> {}),
                        java("interface A {}")
                      );
                  }

                  @Test
                  void afterSourceSpec() {
                      rewriteRun(java("interface A {}", s -> s.after(actual -> actual)));
                  }

                  @Test
                  void variable() {
                      SourceSpecs a = java("interface A {}");
                      rewriteRun(a);
                  }

                  @Test
                  void extraAssertion() {
                      rewriteRun(java("interface A {}"));
                      assert true;
                  }

                  @Test
                  void sharedSpec() {
                      rewriteRun(sharedSpec("enum A {}"));
                  }

                  @Nested
                  class NoChange {

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface C {}"));
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(java("enum C {}"));
                      }

                      @Test
                      void doesNotChangeAnnotation() {
                          rewriteRun(java("@interface C {}"));
                      }
                  }

                  private static SourceSpecs sharedSpec(String before) {
                      return java(before);
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void twoNoChangeTests() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class MyRecipeTest implements RewriteTest {

                      @Test
                      void changes() {
                          rewriteRun(java("class A {}", "class B {}"));
                      }

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface A {}"));
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(java("enum A {}"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void onlyNoChangeTests() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class FindSomethingTest implements RewriteTest {

                      @Test
                      void notFound() {
                          rewriteRun(java("interface A {}"));
                      }

                      @Test
                      void alsoNotFound() {
                          rewriteRun(java("enum A {}"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void topicGroupedNestedClasses() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Nested;
                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  class MyRecipeTest implements RewriteTest {

                      @Test
                      void changes() {
                          rewriteRun(java("class A {}", "class B {}"));
                      }

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface A {}"));
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(java("enum A {}"));
                      }

                      @Test
                      void doesNotChangeAnnotation() {
                          rewriteRun(java("@interface A {}"));
                      }

                      @Nested
                      class Records {
                          @Test
                          void record() {
                              rewriteRun(java("record A() {}"));
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void abstractTestClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.openrewrite.test.RewriteTest;

                  import static org.openrewrite.java.Assertions.java;

                  abstract class MyRecipeTest implements RewriteTest {

                      @Test
                      void changes() {
                          rewriteRun(java("class A {}", "class B {}"));
                      }

                      @Test
                      void doesNotChangeInterface() {
                          rewriteRun(java("interface A {}"));
                      }

                      @Test
                      void doesNotChangeEnum() {
                          rewriteRun(java("enum A {}"));
                      }

                      @Test
                      void doesNotChangeAnnotation() {
                          rewriteRun(java("@interface A {}"));
                      }
                  }
                  """
              )
            );
        }
    }
}
