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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseJavaTemplateStaticApplyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseJavaTemplateStaticApply())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void replacesBuilderBuildApply() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.builder("#{any()}")
                              .build()
                              .apply(getCursor(), method.getCoordinates().replace());
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.apply("#{any()}", getCursor(), method.getCoordinates().replace());
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesWithVarargs() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.builder("#{any(String)}")
                              .build()
                              .apply(getCursor(), method.getCoordinates().replace(), method.getArguments().get(0));
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.apply("#{any(String)}", getCursor(), method.getCoordinates().replace(), method.getArguments().get(0));
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenContextSensitive() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.builder("#{any()}")
                              .contextSensitive()
                              .build()
                              .apply(getCursor(), method.getCoordinates().replace());
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenImportsPresent() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.builder("#{any()}")
                              .imports("org.example.Foo")
                              .build()
                              .apply(getCursor(), method.getCoordinates().replace());
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenJavaParserPresent() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaParser;
              import org.openrewrite.java.JavaTemplate;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return JavaTemplate.builder("#{any()}")
                              .javaParser(JavaParser.fromJavaVersion().classpath("lombok"))
                              .build()
                              .apply(getCursor(), method.getCoordinates().replace());
                  }
              }
              """
          )
        );
    }
}
