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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StaticFinalMatcherFieldsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new StaticFinalMatcherFields())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void methodMatcherInRecipe() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              public class A extends Recipe {
                  private final MethodMatcher listAdd = new MethodMatcher("java.util.List add(..)", true);

                  @Override
                  public String getDisplayName() {
                      return "A";
                  }

                  @Override
                  public String getDescription() {
                      return "A.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              if (listAdd.matches(method)) {
                                  return method.withName(method.getName().withSimpleName("addAll"));
                              }
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              public class A extends Recipe {
                  private static final MethodMatcher LIST_ADD = new MethodMatcher("java.util.List add(..)", true);

                  @Override
                  public String getDisplayName() {
                      return "A";
                  }

                  @Override
                  public String getDescription() {
                      return "A.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                              if (LIST_ADD.matches(method)) {
                                  return method.withName(method.getName().withSimpleName("addAll"));
                              }
                              return super.visitMethodInvocation(method, ctx);
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void annotationAndTypeMatchersInStaticNestedVisitor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.AnnotationMatcher;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.TypeMatcher;
              import org.openrewrite.java.tree.J;

              class A {
                  static class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private final AnnotationMatcher overrideAnnotation = new AnnotationMatcher("@java.lang.Override");
                      private final TypeMatcher listType = new TypeMatcher("java.util.List", true);

                      @Override
                      public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                          if (overrideAnnotation.matches(annotation) || listType.matches(annotation.getType())) {
                              return annotation;
                          }
                          return super.visitAnnotation(annotation, ctx);
                      }
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.AnnotationMatcher;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.TypeMatcher;
              import org.openrewrite.java.tree.J;

              class A {
                  static class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private static final AnnotationMatcher OVERRIDE_ANNOTATION = new AnnotationMatcher("@java.lang.Override");
                      private static final TypeMatcher LIST_TYPE = new TypeMatcher("java.util.List", true);

                      @Override
                      public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                          if (OVERRIDE_ANNOTATION.matches(annotation) || LIST_TYPE.matches(annotation.getType())) {
                              return annotation;
                          }
                          return super.visitAnnotation(annotation, ctx);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void initializerReferencingConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              class Visitor extends JavaIsoVisitor<ExecutionContext> {
                  private static final String LIST = "java.util.List";
                  private final MethodMatcher MATCHER = new MethodMatcher(LIST + " add(..)", Boolean.TRUE);

                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return MATCHER.matches(method) ? method : super.visitMethodInvocation(method, ctx);
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              class Visitor extends JavaIsoVisitor<ExecutionContext> {
                  private static final String LIST = "java.util.List";
                  private static final MethodMatcher MATCHER = new MethodMatcher(LIST + " add(..)", Boolean.TRUE);

                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      return MATCHER.matches(method) ? method : super.visitMethodInvocation(method, ctx);
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void initializerReadsOption() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.Option;
                  import org.openrewrite.Recipe;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.MethodMatcher;
                  import org.openrewrite.java.search.UsesMethod;

                  public class A extends Recipe {
                      @Option(displayName = "Method pattern", description = "A method pattern.", example = "java.util.List add(..)")
                      String methodPattern;

                      private final MethodMatcher matcher = new MethodMatcher(methodPattern);

                      @Override
                      public String getDisplayName() {
                          return "A";
                      }

                      @Override
                      public String getDescription() {
                          return "A.";
                      }

                      @Override
                      public TreeVisitor<?, ExecutionContext> getVisitor() {
                          return new UsesMethod<>(matcher);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void notPrivate() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;

                  class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      protected final MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");
                  }
                  """
              )
            );
        }

        @Test
        void javaTemplate() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.JavaTemplate;

                  class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private final JavaTemplate template = JavaTemplate.builder("1").build();
                  }
                  """
              )
            );
        }

        @Test
        void neitherRecipeNorVisitor() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.java.MethodMatcher;

                  class A {
                      private final MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");
                  }
                  """
              )
            );
        }

        @Test
        void anonymousVisitor() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.TreeVisitor;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;
                  import org.openrewrite.java.tree.J;

                  class A {
                      TreeVisitor<?, ExecutionContext> visitor() {
                          return new JavaIsoVisitor<ExecutionContext>() {
                              private final MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");

                              @Override
                              public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                  return matcher.matches(method) ? method : super.visitMethodInvocation(method, ctx);
                              }
                          };
                      }
                  }
                  """
              )
            );
        }

        @Test
        void innerClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;

                  class A {
                      class Visitor extends JavaIsoVisitor<ExecutionContext> {
                          private final MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void staticFieldDeclaredLater() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;

                  class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private final MethodMatcher matcher = new MethodMatcher(PATTERN);
                      private static final String PATTERN = String.valueOf("java.util.List add(..)");
                  }
                  """
              )
            );
        }

        @Test
        void constantNameAlreadyInUse() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;

                  class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private static final String MATCHER = "java.util.List add(..)";
                      private final MethodMatcher matcher = new MethodMatcher(MATCHER);
                  }
                  """
              )
            );
        }

        @Test
        void lombokGetter() {
            rewriteRun(
              //language=java
              java(
                """
                  import lombok.Getter;
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.MethodMatcher;

                  @Getter
                  class Visitor extends JavaIsoVisitor<ExecutionContext> {
                      private final MethodMatcher matcher = new MethodMatcher("java.util.List add(..)");
                  }
                  """
              )
            );
        }
    }
}
