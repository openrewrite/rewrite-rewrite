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
import static org.openrewrite.test.SourceSpecs.text;

class RecipeTestingBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices")
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void collapsesSingleReturnLambdaWithInlineActual() {
        rewriteRun(
          java(
            """
              import org.openrewrite.test.RewriteTest;

              import java.util.function.UnaryOperator;

              import static org.assertj.core.api.Assertions.assertThat;

              class FooTest implements RewriteTest {
                  UnaryOperator<String> assertion() {
                      return after -> {
                          assertThat(after).contains("~~>");
                          return after;
                      };
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;

              import java.util.function.UnaryOperator;

              import static org.assertj.core.api.Assertions.assertThat;

              class FooTest implements RewriteTest {
                  UnaryOperator<String> assertion() {
                      return after -> assertThat(after).contains("~~>").actual();
                  }
              }

              """
          )
        );
    }

    @Test
    void doNotChangeRefasterTemplates() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package com.google.errorprone.refaster.annotation;
              public @interface AfterTemplate {}
              """,
            """
              package com.google.errorprone.refaster.annotation;
              public @interface BeforeTemplate {}
              """)),
          java(
            """
              import com.google.errorprone.refaster.annotation.AfterTemplate;
              import com.google.errorprone.refaster.annotation.BeforeTemplate;

              import java.util.Comparator;

              public class UseStringCaseInsensitiveOrder {
                  @BeforeTemplate
                  Comparator<String> before() {
                      return (s1, s2) -> s1.compareToIgnoreCase(s2);
                  }

                  @AfterTemplate
                  Comparator<String> after() {
                      return String.CASE_INSENSITIVE_ORDER;
                  }
              }
              """
          )
        );
    }

    @Test
    void addCheckReturnValueAnnotationToLombokConfig() {
        rewriteRun(
          text(
            """
              config.stopBubbling = true
              """,
            """
              config.stopBubbling = true
              lombok.checkReturnValueAnnotation += lombok
              """,
            spec -> spec.path("lombok.config").noTrim()
          )
        );
    }

    @Test
    void retainExistingCheckReturnValueAnnotation() {
        rewriteRun(
          text(
            """
              lombok.checkReturnValueAnnotation += lombok
              """,
            spec -> spec.path("lombok.config").noTrim()
          )
        );
    }

    @Test
    void findIgnoredResultsOfLstWithers() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import static java.util.Collections.emptyList;

              class Foo {
                  void foo(J.Literal literal, J.MethodInvocation method) {
                      literal.withValue(1);
                      method.withArguments(emptyList());
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import static java.util.Collections.emptyList;

              class Foo {
                  void foo(J.Literal literal, J.MethodInvocation method) {
                      /*~~(Result of `withValue` is ignored, but `@CheckReturnValue` on the method requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/literal.withValue(1);
                      /*~~(Result of `withArguments` is ignored, but `@CheckReturnValue` on package `org.openrewrite.java.tree` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/method.withArguments(emptyList());
                  }
              }

              """
          )
        );
    }
}
