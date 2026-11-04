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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;

class FindDeprecatedMethodDelegationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDeprecatedMethodDelegations());
    }

    @DocumentExample
    @Test
    void constructorDelegation() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getRecipeYaml)
            .isEqualTo(
              """
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo <constructor>(java.lang.String)'
                    replacement: 'this(a, null)'
                    imports:
                      - 'com.example.Foo'""")),
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
              """,
            """
              package com.example;

              public class Foo {
                  public Foo(String a, String b) {
                  }

                  /*~~>*/@Deprecated
                  public Foo(String a) {
                      this(a, null);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodDelegation() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .satisfies(row -> {
                assertThat(row.getMethodPattern()).isEqualTo("com.example.Bar oldMethod(java.lang.String)");
                assertThat(row.getReplacement()).isEqualTo("newMethod(s, \"default\")");
            })),
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
              """,
            """
              package com.example;

              public class Bar {
                  public void newMethod(String s, String defaultVal) {
                  }

                  /*~~>*/@Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, "default");
                  }
              }
              """
          )
        );
    }

    @Test
    void returnStatementDelegation() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getReplacement)
            .isEqualTo("newMethod(s, 0)")),
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
              """,
            """
              package com.example;

              public class Ret {
                  public String newMethod(String s, int n) {
                      return s + n;
                  }

                  /*~~>*/@Deprecated
                  public String oldMethod(String s) {
                      return newMethod(s, 0);
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
              """,
            """
              package com.example;

              import org.openrewrite.internal.ToBeRemoved;

              public class Tbr {
                  public void newMethod(String s, int n) {
                  }

                  /*~~>*/@ToBeRemoved(after = "2025-01-01")
                  public void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void protectedDelegatingToProtected() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Prot {
                  protected void newMethod(String s, int n) {
                  }

                  @Deprecated
                  protected void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """,
            """
              package com.example;

              public class Prot {
                  protected void newMethod(String s, int n) {
                  }

                  /*~~>*/@Deprecated
                  protected void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void recordsImportsTheReplacementNeeds() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getRecipeYaml)
            .isEqualTo(
              """
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Timeout <constructor>(java.lang.String)'
                    replacement: 'this(name, emptyList(), Duration.ofSeconds(1), new Timeout.Unit())'
                    imports:
                      - 'com.example.Timeout'
                      - 'java.time.Duration'
                    staticImports:
                      - 'java.util.Collections.emptyList'""")),
          java(
            """
              package com.example;

              import java.time.Duration;
              import java.util.List;

              import static java.util.Collections.emptyList;

              public class Timeout {
                  public static class Unit {
                  }

                  public Timeout(String name, List<String> tags, Duration duration, Unit unit) {
                  }

                  @Deprecated
                  public Timeout(String name) {
                      this(name, emptyList(), Duration.ofSeconds(1), new Timeout.Unit());
                  }
              }
              """,
            """
              package com.example;

              import java.time.Duration;
              import java.util.List;

              import static java.util.Collections.emptyList;

              public class Timeout {
                  public static class Unit {
                  }

                  public Timeout(String name, List<String> tags, Duration duration, Unit unit) {
                  }

                  /*~~>*/@Deprecated
                  public Timeout(String name) {
                      this(name, emptyList(), Duration.ofSeconds(1), new Timeout.Unit());
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedClassConstructorImportsOutermostClass() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getRecipeYaml)
            .isEqualTo(
              """
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Report$Row <constructor>(java.lang.String)'
                    replacement: 'this(name, null)'
                    imports:
                      - 'com.example.Report'""")),
          java(
            """
              package com.example;

              public class Report {
                  public static class Row {
                      public Row(String name, String detail) {
                      }

                      @Deprecated
                      public Row(String name) {
                          this(name, null);
                      }
                  }
              }
              """,
            """
              package com.example;

              public class Report {
                  public static class Row {
                      public Row(String name, String detail) {
                      }

                      /*~~>*/@Deprecated
                      public Row(String name) {
                          this(name, null);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multiLineReplacementCollapsed() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getReplacement)
            .isEqualTo("newMethod(new StringBuilder().append(a).toString(), b)")),
          java(
            """
              package com.example;

              public class Multi {
                  public void newMethod(String a, String b) {
                  }

                  @Deprecated
                  public void oldMethod(String a, String b) {
                      newMethod(
                              new StringBuilder()
                                      .append(a)
                                      .toString(),
                              b);
                  }
              }
              """,
            """
              package com.example;

              public class Multi {
                  public void newMethod(String a, String b) {
                  }

                  /*~~>*/@Deprecated
                  public void oldMethod(String a, String b) {
                      newMethod(
                              new StringBuilder()
                                      .append(a)
                                      .toString(),
                              b);
                  }
              }
              """
          )
        );
    }

    @Test
    void commentsInDelegationBodyStripped() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getReplacement)
            .isEqualTo("newMethod(s, \"default\")")),
          java(
            """
              package com.example;

              public class Commented {
                  public void newMethod(String s, String defaultVal) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, /* noinspection */ "default");
                  }
              }
              """,
            """
              package com.example;

              public class Commented {
                  public void newMethod(String s, String defaultVal) {
                  }

                  /*~~>*/@Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, /* noinspection */ "default");
                  }
              }
              """
          )
        );
    }

    @Test
    void singleQuotesEscapedInYaml() {
        rewriteRun(
          spec -> spec.dataTable(DeprecatedMethodDelegations.Row.class, rows -> assertThat(rows)
            .singleElement()
            .extracting(DeprecatedMethodDelegations.Row::getRecipeYaml)
            .isEqualTo(
              """
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Quote oldMethod(java.lang.String)'
                    replacement: 'newMethod(s, ''-'')'""")),
          java(
            """
              package com.example;

              public class Quote {
                  public void newMethod(String s, char separator) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, '-');
                  }
              }
              """,
            """
              package com.example;

              public class Quote {
                  public void newMethod(String s, char separator) {
                  }

                  /*~~>*/@Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, '-');
                  }
              }
              """
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
    void delegationToOtherClassIgnored() {
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
    void privateDeprecatedMethodIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Priv {
                  public void newMethod(String s, int n) {
                  }

                  @Deprecated
                  void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void packagePrivateClassIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              class Hidden {
                  public void newMethod(String s, int n) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void delegationToPrivateConstructorIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Ctor {
                  private Ctor(String a, String b) {
                  }

                  @Deprecated
                  public Ctor(String a) {
                      this(a, null);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacementReferencingPrivateFieldIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Field {
                  private static final int DEFAULT = 0;

                  public void newMethod(String s, int n) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, DEFAULT);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacementWithUnqualifiedInstanceCallIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              import java.util.List;

              public class Inst {
                  public List<String> getNames() {
                      return null;
                  }

                  public void withNames(List<String> names, String extra) {
                  }

                  @Deprecated
                  public void withName(String extra) {
                      withNames(getNames(), extra);
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterUsedTwiceIgnored() {
        rewriteRun(
          java(
            """
              package com.example;

              public class Twice {
                  public void newMethod(String s, boolean empty) {
                  }

                  @Deprecated
                  public void oldMethod(String s) {
                      newMethod(s, s.isEmpty());
                  }
              }
              """
          )
        );
    }

    @Test
    void testSourcesIgnored() {
        rewriteRun(
          srcTestJava(
            java(
              """
                package com.example;

                public class Fixture {
                    public void newMethod(String s, int n) {
                    }

                    @Deprecated
                    public void oldMethod(String s) {
                        newMethod(s, 0);
                    }
                }
                """
            )
          )
        );
    }
}
