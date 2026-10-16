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

class FindSharedVisitorInstancesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new FindSharedVisitorInstances())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void staticPreconditionWalkingTheTree() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesMethod;

              public class A extends Recipe {
                  private static final TreeVisitor<?, ExecutionContext> PRECONDITION = Preconditions.or(
                          new UsesMethod<>("org.junit.Assert assertEquals(..)"),
                          new UsesMethod<>("org.junit.Assert assertNotEquals(..)"));

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
                      return Preconditions.check(PRECONDITION, new JavaIsoVisitor<>());
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesMethod;

              public class A extends Recipe {
                  private static final TreeVisitor<?, ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/PRECONDITION = Preconditions.or(
                          new UsesMethod<>("org.junit.Assert assertEquals(..)"),
                          new UsesMethod<>("org.junit.Assert assertNotEquals(..)"));

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
                      return Preconditions.check(PRECONDITION, new JavaIsoVisitor<>());
                  }
              }
              """
          )
        );
    }

    @Test
    void staticPreconditionAssignedInStaticInitializer() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              import java.util.List;

              class A {
                  private static final List<MethodMatcher> matchers;
                  private static final TreeVisitor<?, ExecutionContext> precondition;

                  static {
                      matchers = List.of(new MethodMatcher("org.junit.Assert assertEquals(..)"));
                      precondition = Preconditions.or(matchers.stream().map(UsesMethod::new).toArray(TreeVisitor[]::new));
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              import java.util.List;

              class A {
                  private static final List<MethodMatcher> matchers;
                  private static final TreeVisitor<?, ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/precondition;

                  static {
                      matchers = List.of(new MethodMatcher("org.junit.Assert assertEquals(..)"));
                      precondition = Preconditions.or(matchers.stream().map(UsesMethod::new).toArray(TreeVisitor[]::new));
                  }
              }
              """
          )
        );
    }

    @Test
    void staticVisitorDeclaredAsSupertype() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.AnnotationMatcher;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.RemoveAnnotationVisitor;

              class A {
                  private static final JavaIsoVisitor<ExecutionContext> REMOVE_AUTOWIRED = new RemoveAnnotationVisitor(new AnnotationMatcher("@Autowired"));
                  private static final RemoveAnnotationVisitor removeOverride = new RemoveAnnotationVisitor(new AnnotationMatcher("@Override"));
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.AnnotationMatcher;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.RemoveAnnotationVisitor;

              class A {
                  private static final JavaIsoVisitor<ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/REMOVE_AUTOWIRED = new RemoveAnnotationVisitor(new AnnotationMatcher("@Autowired"));
                  private static final RemoveAnnotationVisitor /*~~(Visitor instance shared across concurrent visits)~~>*/removeOverride = new RemoveAnnotationVisitor(new AnnotationMatcher("@Override"));
              }
              """
          )
        );
    }

    @Test
    void staticAnonymousVisitor() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesType;

              class A {
                  static final JavaIsoVisitor<ExecutionContext> VISITOR = new JavaIsoVisitor<ExecutionContext>() {
                  };
                  static final UsesType<ExecutionContext> USES_ID = new UsesType<ExecutionContext>("jakarta.persistence.Id", false) {
                  };
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesType;

              class A {
                  static final JavaIsoVisitor<ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/VISITOR = new JavaIsoVisitor<ExecutionContext>() {
                  };
                  static final UsesType<ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/USES_ID = new UsesType<ExecutionContext>("jakarta.persistence.Id", false) {
                  };
              }
              """
          )
        );
    }

    @Test
    void staticCustomVisitor() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.List;

              class A {
                  private static final Extractor EXTRACTOR = new Extractor();

                  private static class Extractor extends JavaIsoVisitor<List<String>> {
                  }
              }
              """,
            """
              import org.openrewrite.java.JavaIsoVisitor;

              import java.util.List;

              class A {
                  private static final Extractor /*~~(Visitor instance shared across concurrent visits)~~>*/EXTRACTOR = new Extractor();

                  private static class Extractor extends JavaIsoVisitor<List<String>> {
                  }
              }
              """
          )
        );
    }

    @Test
    void staticArraysAndCollectionsOfVisitors() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.search.UsesMethod;
              import org.openrewrite.java.search.UsesType;

              import java.util.List;
              import java.util.Map;

              class A {
                  static final TreeVisitor<?, ExecutionContext>[] ARRAY = new TreeVisitor[]{new UsesMethod<>("java.util.List add(..)")};
                  static final List<TreeVisitor<?, ExecutionContext>> LIST = List.of(new UsesType<>("java.util.List", false));
                  static final Map<String, ? extends TreeVisitor<?, ExecutionContext>> MAP = Map.of();
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.search.UsesMethod;
              import org.openrewrite.java.search.UsesType;

              import java.util.List;
              import java.util.Map;

              class A {
                  static final TreeVisitor<?, ExecutionContext>[] /*~~(Visitor instance shared across concurrent visits)~~>*/ARRAY = new TreeVisitor[]{new UsesMethod<>("java.util.List add(..)")};
                  static final List<TreeVisitor<?, ExecutionContext>> /*~~(Visitor instance shared across concurrent visits)~~>*/LIST = List.of(new UsesType<>("java.util.List", false));
                  static final Map<String, ? extends TreeVisitor<?, ExecutionContext>> /*~~(Visitor instance shared across concurrent visits)~~>*/MAP = Map.of();
              }
              """
          )
        );
    }

    @Test
    void recipeInstanceField() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.search.UsesMethod;

              public class A extends Recipe {
                  private final TreeVisitor<?, ExecutionContext> precondition = new UsesMethod<>("java.util.List add(..)");

                  @Override
                  public String getDisplayName() {
                      return "A";
                  }

                  @Override
                  public String getDescription() {
                      return "A.";
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.search.UsesMethod;

              public class A extends Recipe {
                  private final TreeVisitor<?, ExecutionContext> /*~~(Visitor instance shared across concurrent visits)~~>*/precondition = new UsesMethod<>("java.util.List add(..)");

                  @Override
                  public String getDisplayName() {
                      return "A";
                  }

                  @Override
                  public String getDescription() {
                      return "A.";
                  }
              }
              """
          )
        );
    }

    @Test
    void statelessPreconditions() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.search.UsesJavaVersion;
              import org.openrewrite.java.search.UsesType;

              import java.util.List;
              import java.util.stream.Stream;

              class A {
                  static final List<String> TYPES = List.of("org.junit.jupiter.api.Test", "org.junit.Test");
                  static final TreeVisitor<?, ExecutionContext> MAPPED_BY_LAMBDA = Preconditions.or(TYPES.stream().map(t -> new UsesType<>(t, false)).toArray(UsesType[]::new));
                  static final TreeVisitor<?, ExecutionContext> MAPPED_BY_CONSTRUCTOR_REFERENCE = Preconditions.or(Stream.of(17, 21).map(UsesJavaVersion::new).toArray(TreeVisitor[]::new));
                  static final UsesType<ExecutionContext> USES_ID = new UsesType<>("jakarta.persistence.Id", false);
                  static final TreeVisitor<?, ExecutionContext> OR = Preconditions.or(
                          new UsesType<>("org.junit.jupiter.api.Test", false),
                          Preconditions.and(new UsesJavaVersion<>(17), Preconditions.not(new UsesType<>("org.junit.Test", false))));
                  static final TreeVisitor<?, ExecutionContext>[] ARRAY = new TreeVisitor[]{new UsesType<>("java.util.List", false)};
                  static final TreeVisitor<?, ExecutionContext> ASSIGNED_IN_STATIC_BLOCK;

                  static {
                      ASSIGNED_IN_STATIC_BLOCK = Preconditions.or(new UsesType<>("java.util.List", false));
                  }
              }
              """
          )
        );
    }

    @Test
    void visitorsCreatedPerGetVisitorCall() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Preconditions;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.MethodMatcher;
              import org.openrewrite.java.search.UsesMethod;

              import java.util.function.Supplier;

              public class A extends Recipe {
                  private static final MethodMatcher ADD = new MethodMatcher("java.util.List add(..)");
                  private static final Supplier<TreeVisitor<?, ExecutionContext>> FACTORY = () -> new UsesMethod<>(ADD);

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
                      TreeVisitor<?, ExecutionContext> precondition = usesAdd();
                      return Preconditions.check(precondition, new JavaIsoVisitor<ExecutionContext>() {
                          private final TreeVisitor<?, ExecutionContext> nested = new UsesMethod<>(ADD);
                      });
                  }

                  private static TreeVisitor<?, ExecutionContext> usesAdd() {
                      return Preconditions.or(new UsesMethod<>(ADD));
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceFieldOutsideRecipe() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.TreeVisitor;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.search.UsesMethod;

              class A extends JavaIsoVisitor<ExecutionContext> {
                  private final TreeVisitor<?, ExecutionContext> delegate = new UsesMethod<>("java.util.List add(..)");
              }
              """
          )
        );
    }
}
