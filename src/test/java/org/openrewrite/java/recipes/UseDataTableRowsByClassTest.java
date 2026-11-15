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
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseDataTableRowsByClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseDataTableRowsByClass())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void classNameLiteral() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  int count(RecipeRun run) {
                      List<SourcesFileResults.Row> rows = run.getDataTableRows("org.openrewrite.table.SourcesFileResults");
                      return rows.size();
                  }
              }
              """,
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  int count(RecipeRun run) {
                      List<SourcesFileResults.Row> rows = run.getDataTableRows(SourcesFileResults.class);
                      return rows.size();
                  }
              }
              """
          )
        );
    }

    @Test
    void addImportForClassNameLiteral() {
        rewriteRun(
          // Without the data table class in use, the recipe doesn't know its row type to give the rows
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(ParserTypes.Erasure).build()),
          java(
            """
              import org.openrewrite.RecipeRun;

              class Test {
                  boolean none(RecipeRun run) {
                      return run.getDataTableRows("org.openrewrite.table.SourcesFileErrors").isEmpty();
                  }
              }
              """,
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileErrors;

              class Test {
                  boolean none(RecipeRun run) {
                      return run.getDataTableRows(SourcesFileErrors.class).isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void classGetNameWithDefaultGroup() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run) {
                      return run.getDataTableRows(SourcesFileResults.class.getName(), null);
                  }
              }
              """,
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run) {
                      return run.getDataTableRows(SourcesFileResults.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void classVariable() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run, Class<SourcesFileResults> type) {
                      return run.getDataTableRows(type.getName());
                  }
              }
              """,
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run, Class<SourcesFileResults> type) {
                      return run.getDataTableRows(type);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepGroup() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run, String group) {
                      return run.getDataTableRows("org.openrewrite.table.SourcesFileResults", group);
                  }
              }
              """,
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<SourcesFileResults.Row> rows(RecipeRun run, String group) {
                      return run.getDataTableRows(SourcesFileResults.class, group);
                  }
              }
              """
          )
        );
    }

    @Test
    void dataTableFromSources() {
        rewriteRun(
          java(
            """
              package com.example.table;

              import org.openrewrite.DataTable;
              import org.openrewrite.Recipe;

              public class Findings extends DataTable<Findings.Row> {
                  public Findings(Recipe recipe) {
                      super(recipe, "Findings", "Findings.");
                  }

                  public static class Row {
                  }
              }
              """
          ),
          java(
            """
              import com.example.table.Findings;
              import org.openrewrite.RecipeRun;

              import java.util.List;

              class Test {
                  List<Findings.Row> rows(RecipeRun run) {
                      return run.getDataTableRows("com.example.table.Findings");
                  }
              }
              """,
            """
              import com.example.table.Findings;
              import org.openrewrite.RecipeRun;

              import java.util.List;

              class Test {
                  List<Findings.Row> rows(RecipeRun run) {
                      return run.getDataTableRows(Findings.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNameNotKnownToBeAClass() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;

              import java.util.List;

              class Test {
                  List<Object> rows(RecipeRun run, String name, Class<?> type, Object table) {
                      run.getDataTableRows(type.getName());
                      run.getDataTableRows(table.getClass().getName());
                      run.getDataTableRows("org.openrewrite.Outer$Table");
                      run.getDataTableRows("table");
                      return run.getDataTableRows(name);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepRowsOfAnotherType() {
        rewriteRun(
          java(
            """
              import org.openrewrite.RecipeRun;
              import org.openrewrite.table.SourcesFileResults;

              import java.util.List;

              class Test {
                  List<String> rows(RecipeRun run) {
                      return run.getDataTableRows(SourcesFileResults.class.getName());
                  }
              }
              """
          )
        );
    }
}
