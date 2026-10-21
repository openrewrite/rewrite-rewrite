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

class UnconditionalMaybeImportTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnconditionalMaybeImport())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void unwrapGuardedMaybeRemoveImport() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                              boolean isMock = "Mock".equals(annotation.getSimpleName());
                              if (isMock) {
                                  maybeRemoveImport("org.mockito.Mock");
                              }
                              maybeAddImport("org.mockito.Spy");
                              return annotation;
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
              import org.openrewrite.java.tree.J;

              public class SampleRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "Sample";
                  }

                  @Override
                  public String getDescription() {
                      return "Sample recipe.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                              boolean isMock = "Mock".equals(annotation.getSimpleName());
                              maybeRemoveImport("org.mockito.Mock");
                              maybeAddImport("org.mockito.Spy");
                              return annotation;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapUnbracedGuard() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      boolean isList = "List".equals(method.getSimpleName());
                      if (isList)
                          maybeAddImport("java.util.List");
                      return method;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      boolean isList = "List".equals(method.getSimpleName());
                      maybeAddImport("java.util.List");
                      return method;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapSeveralCallsKeepingTheirComments() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.JavaType;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  private static final String LIST = "java.util.List";
                  private JavaType type;
                  private boolean changed;
                  private int count;

                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      if (this.type instanceof JavaType.Class && !(changed || count > 0)) {
                          // the old type is gone
                          maybeRemoveImport("java.util.Vector");
                          this.maybeAddImport(LIST, true);
                          maybeAddImport("java.util." + "Collections", "emptyList");
                      }
                      return method;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.JavaType;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  private static final String LIST = "java.util.List";
                  private JavaType type;
                  private boolean changed;
                  private int count;

                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      // the old type is gone
                      maybeRemoveImport("java.util.Vector");
                      this.maybeAddImport(LIST, true);
                      maybeAddImport("java.util." + "Collections", "emptyList");
                      return method;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapGuardAroundFullyQualifiedTypeOverload() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.JavaType;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  private JavaType.FullyQualified oldType;
                  private boolean replaced;

                  @Override
                  public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                      if (replaced) {
                          maybeRemoveImport(oldType);
                      }
                      return identifier;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.JavaType;

              class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                  private JavaType.FullyQualified oldType;
                  private boolean replaced;

                  @Override
                  public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                      maybeRemoveImport(oldType);
                      return identifier;
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void elseBranch() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          boolean isList = "List".equals(identifier.getSimpleName());
                          if (isList) {
                              maybeAddImport("java.util.List");
                          } else {
                              maybeAddImport("java.util.Set");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void elseIf() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          boolean isList = "List".equals(identifier.getSimpleName());
                          boolean isSet = "Set".equals(identifier.getSimpleName());
                          if (isList) {
                              return identifier.withSimpleName("Set");
                          } else if (isSet) {
                              maybeAddImport("java.util.Set");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionWithMethodInvocation() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if ("List".equals(identifier.getSimpleName())) {
                              maybeAddImport("java.util.List");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionWithIncrement() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private int visits;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if (visits++ == 0) {
                              maybeAddImport("java.util.List");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void changedTreeGuard() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                          J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                          if (cu != c) {
                              maybeRemoveImport("org.junit.jupiter.api.Test");
                          }
                          return c;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void onlyIfReferencedNotTrue() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private boolean changed;
                      private boolean onlyIfReferenced;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if (changed) {
                              maybeAddImport("java.util.List", false);
                          }
                          if (changed) {
                              maybeAddImport("java.util.Collections", "emptyList", false);
                          }
                          if (changed) {
                              maybeAddImport("java.util", "Set", null, "S", false);
                          }
                          if (changed) {
                              maybeAddImport("java.util.Map", onlyIfReferenced);
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void mixedBody() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private boolean changed;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if (changed) {
                              maybeAddImport("java.util.List");
                              return identifier.withSimpleName("List");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionGuardsArgument() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;
                  import org.openrewrite.java.tree.JavaType;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private String fullyQualifiedName;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if (fullyQualifiedName != null) {
                              maybeAddImport(fullyQualifiedName);
                          }
                          if (identifier.getType() instanceof JavaType.FullyQualified) {
                              maybeRemoveImport((JavaType.FullyQualified) identifier.getType());
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionDerivedFromArgument() {
            rewriteRun(
              java(
                """
                  import org.jspecify.annotations.Nullable;
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      @Override
                      public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                          String fqn = fullyQualifiedName(newClass);
                          boolean hasType = fqn != null;
                          if (hasType) {
                              maybeAddImport(fqn);
                          }
                          return newClass;
                      }

                      private @Nullable String fullyQualifiedName(J.NewClass newClass) {
                          return null;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionAssignedFromArgument() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private String fqn;
                      private boolean known;

                      @Override
                      public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                          known = fqn != null;
                          if (known) {
                              maybeAddImport(fqn);
                          }
                          return newClass;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void argumentWithMethodInvocation() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;
                  import org.openrewrite.java.tree.JavaType;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private boolean changed;
                      private JavaType.FullyQualified type;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          if (changed) {
                              maybeAddImport(type.getFullyQualifiedName());
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void commentOnIf() {
            rewriteRun(
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  class SampleVisitor extends JavaIsoVisitor<ExecutionContext> {
                      private boolean changed;

                      @Override
                      public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                          // only when the identifier was changed
                          if (changed) {
                              maybeAddImport("java.util.List");
                          }
                          if (changed /* again */) {
                              maybeAddImport("java.util.Set");
                          }
                          return identifier;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void notJavaVisitorMethod() {
            rewriteRun(
              java(
                """
                  class Imports {
                      private boolean changed;

                      void maybeAddImport(String fullyQualifiedName) {
                      }

                      void update() {
                          if (changed) {
                              maybeAddImport("java.util.List");
                          }
                      }
                  }
                  """
              )
            );
        }
    }
}
