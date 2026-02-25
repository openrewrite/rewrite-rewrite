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

class UseVisitWithParentCursorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseVisitWithParentCursor())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void replaceDirectVisitMethodCallWithVisit() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class OtherJavaVisitor extends JavaVisitor<ExecutionContext> {
              }

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      J m = super.visitMethodInvocation(method, ctx);
                      m = new OtherJavaVisitor().visitMethodInvocation(method, ctx);
                      return m;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class OtherJavaVisitor extends JavaVisitor<ExecutionContext> {
              }

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      J m = super.visitMethodInvocation(method, ctx);
                      m = new OtherJavaVisitor().visit(method, ctx, getCursor().getParentTreeCursor());
                      return m;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCallOnLocalVariable() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                      J cd = super.visitClassDeclaration(classDecl, ctx);
                      JavaVisitor<ExecutionContext> other = new JavaVisitor<>() {};
                      cd = other.visitClassDeclaration(classDecl, ctx);
                      return cd;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                      J cd = super.visitClassDeclaration(classDecl, ctx);
                      JavaVisitor<ExecutionContext> other = new JavaVisitor<>() {};
                      cd = other.visit(classDecl, ctx, getCursor().getParentTreeCursor());
                      return cd;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCallInAnonymousVisitorInsideRecipe() {
        rewriteRun(
          java(
            """
              import org.openrewrite.*;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() { return ""; }
                  @Override
                  public String getDescription() { return ""; }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                              mi = (J.MethodInvocation) new JavaVisitor<ExecutionContext>() {}.visitMethodInvocation(mi, ctx);
                              return mi;
                          }
                      };
                  }
              }
              """,
            """
              import org.openrewrite.*;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() { return ""; }
                  @Override
                  public String getDescription() { return ""; }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                              mi = (J.MethodInvocation) new JavaVisitor<ExecutionContext>() {
                              }.visit(mi, ctx, getCursor().getParentTreeCursor());
                              return mi;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSuperCall() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return super.visitMethodInvocation(method, ctx);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeThisCall() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  public J doSomething(J.MethodInvocation method, ExecutionContext ctx) {
                      return this.visitMethodInvocation(method, ctx);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUnqualifiedCall() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  public J doSomething(J.MethodInvocation method, ExecutionContext ctx) {
                      return visitMethodInvocation(method, ctx);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangePlainVisitCall() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class SomeJavaVisitor extends JavaVisitor<ExecutionContext> {
                  @Override
                  public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      JavaVisitor<ExecutionContext> other = new JavaVisitor<>() {};
                      return (J) other.visit(method, ctx);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCallOutsideVisitor() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaVisitor;
              import org.openrewrite.java.tree.J;

              class Utility {
                  static J transform(JavaVisitor<ExecutionContext> visitor, J.MethodInvocation mi, ExecutionContext ctx) {
                      return visitor.visitMethodInvocation(mi, ctx);
                  }
              }
              """
          )
        );
    }
}
