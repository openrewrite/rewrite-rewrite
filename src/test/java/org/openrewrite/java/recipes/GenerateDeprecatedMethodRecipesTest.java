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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.yaml.Assertions.yaml;

class GenerateDeprecatedMethodRecipesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GenerateDeprecatedMethodRecipes());
    }

    @DocumentExample
    @Test
    void constructorDelegation() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Foo {
                  private final String a;
                  private final String b;

                  public Foo(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }

                  @Deprecated
                  public Foo(String a) {
                      this(a, null);
                  }
              }
              """
          ),
          yaml(
            doesNotExist(),
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo <constructor>(java.lang.String)'
                    replacement: 'this(a, null)'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void methodDelegation() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Bar {
                  public void newMethod(String s, String defaultVal) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, "default");
                  }
              }
              """
          ),
          yaml(
            doesNotExist(),
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Bar oldMethod(java.lang.String)'
                    replacement: 'newMethod(s, "default")'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void returnStatementDelegation() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Ret {
                  public String newMethod(String s, int n) {
                      return s + n;
                  }

                  @Deprecated
                  public String oldMethod(String s) {
                      return newMethod(s, 0);
                  }
              }
              """
          ),
          yaml(
            doesNotExist(),
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Ret oldMethod(java.lang.String)'
                    replacement: 'newMethod(s, 0)'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void nonDeprecatedMethodIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Baz {
                  public void newMethod(String s) {
                  }

                  public void oldMethod(String s) {
                      newMethod(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void complexBodyIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Qux {
                  public void newMethod(String s) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      System.out.println("logging");
                      newMethod(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void toBeRemovedAnnotation() {
        rewriteRun(
          java(
            """
              package com.example;

              import org.openrewrite.internal.ToBeRemoved;

              public class Tbr {
                  public void newMethod(String s, int n) {
                  }

                  @ToBeRemoved(after = "2025-01-01")
                  public void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """
          ),
          yaml(
            doesNotExist(),
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Tbr oldMethod(java.lang.String)'
                    replacement: 'newMethod(s, 0)'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void externalCallIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class External {
                  @Deprecated
                  public void oldMethod(String s) {
                      System.out.println(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void appendsToExistingFile() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Foo {
                  public Foo(String a, String b) {
                  }

                  @Deprecated
                  public Foo(String a) {
                      this(a, null);
                  }
              }
              """
          ),
          yaml(
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Existing description.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.other.Bar doStuff(java.lang.String)'
                    replacement: 'doStuffNew(s)'
              """,
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Existing description.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.other.Bar doStuff(java.lang.String)'
                    replacement: 'doStuffNew(s)'
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo <constructor>(java.lang.String)'
                    replacement: 'this(a, null)'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void replacesMatchingMethodPattern() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Foo {
                  public Foo(String a, String b) {
                  }

                  @Deprecated
                  public Foo(String a) {
                      this(a, null);
                  }
              }
              """
          ),
          yaml(
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Existing.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo <constructor>(java.lang.String)'
                    replacement: 'this(a, "old")'
              """,
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipes.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Existing.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo <constructor>(java.lang.String)'
                    replacement: 'this(a, null)'
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          )
        );
    }

    @Test
    void multiModuleProject() {
        rewriteRun(
          mavenProject("module-a",
            srcMainJava(
              java(
                """
                  package com.example.a;

                  public class FooA {
                      public FooA(String a, String b) {
                      }

                      @Deprecated
                      public FooA(String a) {
                          this(a, null);
                      }
                  }
                  """
              )
            ),
            yaml(
              doesNotExist(),
              //language=yaml
              """
                type: specs.openrewrite.org/v1beta/recipe
                name: org.openrewrite.recipes.InlineDeprecatedMethods
                displayName: Inline deprecated delegating methods
                description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
                recipeList:
                  - org.openrewrite.java.InlineMethodCalls:
                      methodPattern: 'com.example.a.FooA <constructor>(java.lang.String)'
                      replacement: 'this(a, null)'
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
            )
          ),
          mavenProject("module-b",
            srcMainJava(
              java(
                """
                  package com.example.b;

                  public class FooB {
                      public void current(String s, int n) {
                      }

                      @Deprecated
                      public void legacy(String s) {
                          current(s, 0);
                      }
                  }
                  """
              )
            ),
            yaml(
              doesNotExist(),
              //language=yaml
              """
                type: specs.openrewrite.org/v1beta/recipe
                name: org.openrewrite.recipes.InlineDeprecatedMethods
                displayName: Inline deprecated delegating methods
                description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
                recipeList:
                  - org.openrewrite.java.InlineMethodCalls:
                      methodPattern: 'com.example.b.FooB legacy(java.lang.String)'
                      replacement: 'current(s, 0)'
                """,
              spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
            )
          )
        );
    }
}
