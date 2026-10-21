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

class ListUtilsFilterOverMapToNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ListUtilsFilterOverMapToNull())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void ternaryReturningNull() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> withoutList(List<J.Import> imports) {
                      return ListUtils.map(imports, i -> "java.util.List".equals(i.getTypeName()) ? null : i);
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> withoutList(List<J.Import> imports) {
                      return ListUtils.filter(imports, i -> !"java.util.List".equals(i.getTypeName()));
                  }
              }
              """
          )
        );
    }

    @Test
    void ternaryKeepingElement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> onlyStatic(List<J.Import> imports) {
                      return ListUtils.map(imports, i -> i.isStatic() ? i : null);
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> onlyStatic(List<J.Import> imports) {
                      return ListUtils.filter(imports, i -> i.isStatic());
                  }
              }
              """
          )
        );
    }

    @Test
    void blockLambdaWithEqualityCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> without(List<Statement> statements, Statement removed) {
                      return ListUtils.map(statements, s -> {
                          if (s == removed) {
                              //noinspection DataFlowIssue
                              return null;
                          }
                          return s;
                      });
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> without(List<Statement> statements, Statement removed) {
                      return ListUtils.filter(statements, s -> s != removed);
                  }
              }
              """
          )
        );
    }

    @Test
    void blockLambdaWithNegatedCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> onlyMethods(List<Statement> statements) {
                      return ListUtils.map(statements, s -> {
                          if (!(s instanceof J.MethodDeclaration)) return null;
                          return s;
                      });
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> onlyMethods(List<Statement> statements) {
                      return ListUtils.filter(statements, s -> s instanceof J.MethodDeclaration);
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseKeepingElement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> onlyMethods(List<Statement> statements) {
                      return ListUtils.map(statements, s -> {
                          if (s instanceof J.MethodDeclaration) {
                              return s;
                          } else {
                              return null;
                          }
                      });
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> onlyMethods(List<Statement> statements) {
                      return ListUtils.filter(statements, s -> s instanceof J.MethodDeclaration);
                  }
              }
              """
          )
        );
    }

    @Test
    void parenthesizeCompoundCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> withoutEmpty(List<Statement> statements, boolean enabled) {
                      return ListUtils.map(statements, s -> enabled && s instanceof J.Empty ? null : s);
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.java.tree.Statement;

              import java.util.List;

              class A {
                  List<Statement> withoutEmpty(List<Statement> statements, boolean enabled) {
                      return ListUtils.filter(statements, s -> !(enabled && s instanceof J.Empty));
                  }
              }
              """
          )
        );
    }

    @Test
    void redundantParenthesesAroundCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> withoutStatic(List<J.Import> imports) {
                      return ListUtils.map(imports, i -> (i.isStatic()) ? null : i);
                  }
              }
              """,
            """
              import org.openrewrite.internal.ListUtils;
              import org.openrewrite.java.tree.J;

              import java.util.List;

              class A {
                  List<J.Import> withoutStatic(List<J.Import> imports) {
                      return ListUtils.filter(imports, i -> !i.isStatic());
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;

              import static org.openrewrite.internal.ListUtils.map;

              class A {
                  List<J.Import> onlyStatic(List<J.Import> imports) {
                      return map(imports, i -> i.isStatic() ? i : null);
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;

              import static org.openrewrite.internal.ListUtils.filter;

              class A {
                  List<J.Import> onlyStatic(List<J.Import> imports) {
                      return filter(imports, i -> i.isStatic());
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void explanatoryComment() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.J;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> withoutEmpty(List<Statement> statements) {
                          return ListUtils.map(statements, s -> {
                              // Empty statements are left behind by removed declarations
                              if (s instanceof J.Empty) {
                                  return null;
                              }
                              return s;
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void elementTransformed() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.Space;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> trim(List<Statement> statements) {
                          return ListUtils.map(statements, s -> s.getPrefix().isEmpty() ? null : s.withPrefix(Space.EMPTY));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void indexedLambda() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> withoutFirst(List<Statement> statements) {
                          return ListUtils.map(statements, (i, s) -> i == 0 ? null : s);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void mapFirst() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.J;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> withoutLeadingEmpty(List<Statement> statements) {
                          return ListUtils.mapFirst(statements, s -> s instanceof J.Empty ? null : s);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void additionalStatementsInBlock() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.J;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> withoutEmpty(List<Statement> statements) {
                          return ListUtils.map(statements, s -> {
                              System.out.println(s);
                              if (s instanceof J.Empty) {
                                  return null;
                              }
                              return s;
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void neverNull() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.internal.ListUtils;
                  import org.openrewrite.java.tree.Space;
                  import org.openrewrite.java.tree.Statement;

                  import java.util.List;

                  class A {
                      List<Statement> trim(List<Statement> statements) {
                          return ListUtils.map(statements, s -> s.getPrefix().isEmpty() ? s : s.withPrefix(Space.EMPTY));
                      }
                  }
                  """
              )
            );
        }
    }
}
