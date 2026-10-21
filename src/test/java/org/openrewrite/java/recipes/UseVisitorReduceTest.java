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

class UseVisitorReduceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new UseVisitorReduce())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void returnAtomicBoolean() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  private static final MethodMatcher MATCHER = new MethodMatcher("java.util.List add(..)");

                  boolean usesAdd(Tree tree) {
                      AtomicBoolean found = new AtomicBoolean();
                      new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean f) {
                              if (MATCHER.matches(mi)) {
                                  f.set(true);
                              }
                              return super.visitMethodInvocation(mi, f);
                          }
                      }.visit(tree, found);
                      return found.get();
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  private static final MethodMatcher MATCHER = new MethodMatcher("java.util.List add(..)");

                  boolean usesAdd(Tree tree) {
                      return new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean f) {
                              if (MATCHER.matches(mi)) {
                                  f.set(true);
                              }
                              return super.visitMethodInvocation(mi, f);
                          }
                      }.reduce(tree, new AtomicBoolean()).get();
                  }
              }
              """
          )
        );
    }

    @Test
    void cursorOverload() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Cursor;
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicInteger;

              class A {
                  int countLiterals(Tree tree, Cursor parent) {
                      AtomicInteger count = new AtomicInteger();
                      new JavaIsoVisitor<AtomicInteger>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicInteger c) {
                              c.incrementAndGet();
                              return literal;
                          }
                      }.visit(tree, count, parent);
                      return count.get();
                  }
              }
              """,
            """
              import org.openrewrite.Cursor;
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicInteger;

              class A {
                  int countLiterals(Tree tree, Cursor parent) {
                      return new JavaIsoVisitor<AtomicInteger>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicInteger c) {
                              c.incrementAndGet();
                              return literal;
                          }
                      }.reduce(tree, new AtomicInteger(), parent).get();
                  }
              }
              """
          )
        );
    }

    @Test
    void singleUseInIfCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.TreeVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  String describe(TreeVisitor<?, AtomicBoolean> visitor, Tree tree) {
                      AtomicBoolean found = new AtomicBoolean(false);
                      visitor.visitNonNull(tree, found);
                      if (!found.get()) {
                          return "none";
                      }
                      return "some";
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.TreeVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  String describe(TreeVisitor<?, AtomicBoolean> visitor, Tree tree) {
                      if (!visitor.reduce(tree, new AtomicBoolean(false)).get()) {
                          return "none";
                      }
                      return "some";
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousVisitorNotInlinedIntoCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  String describe(Tree tree) {
                      AtomicBoolean found = new AtomicBoolean(false);
                      new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicBoolean f) {
                              f.set(true);
                              return literal;
                          }
                      }.visitNonNull(tree, found);
                      if (!found.get()) {
                          return "none";
                      }
                      return "some";
                  }

                  String label(Tree tree) {
                      AtomicBoolean found = new AtomicBoolean(false);
                      new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicBoolean f) {
                              f.set(true);
                              return literal;
                          }
                      }.visit(tree, found);
                      return found.get() ? "some" : "none";
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  String describe(Tree tree) {
                      AtomicBoolean found = new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicBoolean f) {
                              f.set(true);
                              return literal;
                          }
                      }.reduce(tree, new AtomicBoolean(false));
                      if (!found.get()) {
                          return "none";
                      }
                      return "some";
                  }

                  String label(Tree tree) {
                      AtomicBoolean found = new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicBoolean f) {
                              f.set(true);
                              return literal;
                          }
                      }.reduce(tree, new AtomicBoolean(false));
                      return found.get() ? "some" : "none";
                  }
              }
              """
          )
        );
    }

    @Test
    void keepWhitespaceAfterLastArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.TreeVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(TreeVisitor<?, AtomicBoolean> visitor, Tree tree) {
                      AtomicBoolean found = new AtomicBoolean();
                      visitor.visit(
                              tree,
                              found
                      );
                      return found.get();
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.TreeVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(TreeVisitor<?, AtomicBoolean> visitor, Tree tree) {
                      return visitor.reduce(
                              tree,
                              new AtomicBoolean()
                      ).get();
                  }
              }
              """
          )
        );
    }

    @Test
    void indentContinuationOfWrappedDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found =
                              new AtomicBoolean();
                      new JavaIsoVisitor<AtomicBoolean>()
                              .visit(tree, found);
                      return enabled && found.get();
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found =
                              new JavaIsoVisitor<AtomicBoolean>()
                                      .reduce(tree, new AtomicBoolean());
                      return enabled && found.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void indentAnonymousVisitorOfWrappedDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found =
                              new AtomicBoolean();
                      new JavaIsoVisitor<AtomicBoolean>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal,
                                                        AtomicBoolean f) {
                              f.set(true);
                              return literal;
                          }
                      }.visit(tree, found);
                      return enabled && found.get();
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found =
                              new JavaIsoVisitor<AtomicBoolean>() {
                                  @Override
                                  public J.Literal visitLiteral(J.Literal literal,
                                                                AtomicBoolean f) {
                                      f.set(true);
                                      return literal;
                                  }
                              }.reduce(tree, new AtomicBoolean());
                      return enabled && found.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleLaterUsesKeepDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.ArrayList;
              import java.util.List;

              class A {
                  String names(Tree tree) {
                      List<String> names = new ArrayList<>();
                      new JavaIsoVisitor<List<String>>() {
                          @Override
                          public J.Identifier visitIdentifier(J.Identifier identifier, List<String> n) {
                              n.add(identifier.getSimpleName());
                              return identifier;
                          }
                      }.visit(tree, names);
                      if (names.isEmpty()) {
                          return "";
                      }
                      return String.join(", ", names);
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.ArrayList;
              import java.util.List;

              class A {
                  String names(Tree tree) {
                      List<String> names = new JavaIsoVisitor<List<String>>() {
                          @Override
                          public J.Identifier visitIdentifier(J.Identifier identifier, List<String> n) {
                              n.add(identifier.getSimpleName());
                              return identifier;
                          }
                      }.reduce(tree, new ArrayList<>());
                      if (names.isEmpty()) {
                          return "";
                      }
                      return String.join(", ", names);
                  }
              }
              """
          )
        );
    }

    @Test
    void singleUseNotEvaluatedFirstKeepsDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found = new AtomicBoolean();
                      new JavaIsoVisitor<AtomicBoolean>().visit(tree, found);
                      return enabled && found.get();
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.concurrent.atomic.AtomicBoolean;

              class A {
                  boolean visit(Tree tree, boolean enabled) {
                      AtomicBoolean found = new JavaIsoVisitor<AtomicBoolean>().reduce(tree, new AtomicBoolean());
                      return enabled && found.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void visitorHeldInLocalVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.HashSet;
              import java.util.Set;

              class A {
                  Set<J.Literal> literals(Tree tree) {
                      JavaIsoVisitor<Set<J.Literal>> visitor = new JavaIsoVisitor<Set<J.Literal>>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, Set<J.Literal> literals) {
                              literals.add(literal);
                              return literal;
                          }
                      };
                      Set<J.Literal> literals = new HashSet<>();
                      visitor.visit(tree, literals);
                      return literals;
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.HashSet;
              import java.util.Set;

              class A {
                  Set<J.Literal> literals(Tree tree) {
                      JavaIsoVisitor<Set<J.Literal>> visitor = new JavaIsoVisitor<Set<J.Literal>>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, Set<J.Literal> literals) {
                              literals.add(literal);
                              return literal;
                          }
                      };
                      return visitor.reduce(tree, new HashSet<>());
                  }
              }
              """
          )
        );
    }

    @Test
    void discardedReduceResult() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicReference;

              class A {
                  J.Literal firstLiteral(Tree tree) {
                      AtomicReference<J.Literal> first = new AtomicReference<>();
                      new JavaIsoVisitor<AtomicReference<J.Literal>>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicReference<J.Literal> f) {
                              f.compareAndSet(null, literal);
                              return literal;
                          }
                      }.reduce(tree, first);
                      J.Literal literal = first.get();
                      return literal;
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;

              import java.util.concurrent.atomic.AtomicReference;

              class A {
                  J.Literal firstLiteral(Tree tree) {
                      J.Literal literal = new JavaIsoVisitor<AtomicReference<J.Literal>>() {
                          @Override
                          public J.Literal visitLiteral(J.Literal literal, AtomicReference<J.Literal> f) {
                              f.compareAndSet(null, literal);
                              return literal;
                          }
                      }.reduce(tree, new AtomicReference<>()).get();
                      return literal;
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void accumulatorCapturedByVisitor() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A {
                      boolean hasLiteral(Tree tree) {
                          AtomicBoolean found = new AtomicBoolean();
                          new JavaIsoVisitor<AtomicBoolean>() {
                              @Override
                              public J.Literal visitLiteral(J.Literal literal, AtomicBoolean f) {
                                  found.set(true);
                                  return literal;
                              }
                          }.visit(tree, found);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void interveningStatement() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;

                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A {
                      boolean visit(Tree tree) {
                          AtomicBoolean found = new AtomicBoolean();
                          System.out.println("visiting");
                          new JavaIsoVisitor<AtomicBoolean>().visit(tree, found);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void notATreeVisitor() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A {
                      static class Walker {
                          void visit(Object tree, AtomicBoolean found) {
                              found.set(tree != null);
                          }
                      }

                      boolean walk(Object tree) {
                          AtomicBoolean found = new AtomicBoolean();
                          new Walker().visit(tree, found);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void accumulatorNotSecondArgument() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;

                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A {
                      boolean visit(Tree tree, AtomicBoolean other) {
                          AtomicBoolean found = new AtomicBoolean();
                          new JavaIsoVisitor<AtomicBoolean>().visit(tree, other);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void initializerWithSideEffects() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;

                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A {
                      AtomicBoolean create() {
                          return new AtomicBoolean();
                      }

                      boolean visit(Tree tree) {
                          AtomicBoolean found = create();
                          new JavaIsoVisitor<AtomicBoolean>().visit(tree, found);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void accumulatorNarrowerThanVisitorParameter() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;

                  import java.util.ArrayList;
                  import java.util.List;

                  class A {
                      ArrayList<String> visit(Tree tree) {
                          ArrayList<String> names = new ArrayList<>();
                          new JavaIsoVisitor<List<String>>().visit(tree, names);
                          return names;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void executionContext() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.ExecutionContext;
                  import org.openrewrite.InMemoryExecutionContext;
                  import org.openrewrite.Tree;
                  import org.openrewrite.TreeVisitor;

                  class A {
                      Object scan(TreeVisitor<?, ExecutionContext> scanner, Tree tree) {
                          ExecutionContext ctx = new InMemoryExecutionContext();
                          scanner.visit(tree, ctx);
                          return ctx.getMessage("found");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void annotatedTypeArgument() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.jspecify.annotations.Nullable;
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  import java.util.concurrent.atomic.AtomicReference;

                  class A {
                      String firstLiteral(Tree tree) {
                          AtomicReference<@Nullable String> first = new AtomicReference<>();
                          new JavaIsoVisitor<AtomicReference<String>>() {
                              @Override
                              public J.Literal visitLiteral(J.Literal literal, AtomicReference<String> f) {
                                  f.compareAndSet(null, literal.getValueSource());
                                  return literal;
                              }
                          }.visit(tree, first);
                          String value = first.get();
                          return value == null ? "" : value;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void superVisit() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.openrewrite.Tree;
                  import org.openrewrite.java.JavaIsoVisitor;
                  import org.openrewrite.java.tree.J;

                  import java.util.concurrent.atomic.AtomicBoolean;

                  class A extends JavaIsoVisitor<AtomicBoolean> {
                      @Override
                      public J visit(Tree tree, AtomicBoolean found) {
                          return super.visit(tree, found);
                      }

                      boolean hasLiteral(Tree tree) {
                          AtomicBoolean found = new AtomicBoolean();
                          super.visit(tree, found);
                          return found.get();
                      }
                  }
                  """
              )
            );
        }
    }
}
