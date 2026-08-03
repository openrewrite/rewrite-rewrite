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
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Set;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.Assertions.java;

class ClasspathArgumentsOnePerLineTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ClasspathArgumentsOnePerLine())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()).styles(
            List.of(
              new NamedStyles(
                randomId(), "test", "test", "test", Set.of(), List.of(
                new TabsAndIndentsStyle(false, 4, 4, 2, false)
              )
              )
            )
          )
        );
    }

    @DocumentExample
    @Test
    void oneArgumentPerLine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "assertj-core-3", "mockito-core-5"));
                  }
              }
              """,
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(),
                          "junit-jupiter-api-5",
                          "assertj-core-3",
                          "mockito-core-5"));
                  }
              }
              """
          )
        );
    }

    @Test
    void entireInvocationOnOneLine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "assertj-core-3", "mockito-core-5"));
                  }
              }
              """,
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                        "junit-jupiter-api-5",
                        "assertj-core-3",
                        "mockito-core-5"));
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithThreeArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "assertj-core-3"));
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyOnSeparateLines() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(),
                          "junit-jupiter-api-5",
                          "assertj-core-3",
                          "mockito-core-5"));
                  }
              }
              """
          )
        );
    }

    @Test
    void movesOnlyArgumentsThatShareALine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5",
                          "assertj-core-3", "mockito-core-5"));
                  }
              }
              """,
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpathFromResources(new InMemoryExecutionContext(),
                          "junit-jupiter-api-5",
                          "assertj-core-3",
                          "mockito-core-5"));
                  }
              }
              """
          )
        );
    }

    @Test
    void classpath() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpath("junit-jupiter-api-5", "assertj-core-3", "mockito-core-5", "spring-core-6"));
                  }
              }
              """,
            """
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpath(
                          "junit-jupiter-api-5",
                          "assertj-core-3",
                          "mockito-core-5",
                          "spring-core-6"));
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithThreeClasspathArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.parser(JavaParser.fromJavaVersion()
                        .classpath("junit-jupiter-api-5", "assertj-core-3", "mockito-core-5"));
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForOtherMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import java.util.List;

              class MyRecipeTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      List<String> classpath = List.of("junit-jupiter-api-5", "assertj-core-3", "mockito-core-5", "spring-core-6");
                  }
              }
              """
          )
        );
    }
}
