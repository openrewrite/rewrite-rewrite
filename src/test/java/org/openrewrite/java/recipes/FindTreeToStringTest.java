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
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.javascript.Assertions.javascript;

class FindTreeToStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new FindTreeToString())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void typescriptTreeToString() {
        rewriteRun(
          rewriteTypes(),
          typescript(
            """
              import { J } from "@openrewrite/rewrite/java";
              import { JS } from "@openrewrite/rewrite/javascript";

              function matches(node: J.Identifier, jsNode: JS.Binary, name: string) {
                  return node.toString() === name && jsNode.toString() === name && name.toString() === "test";
              }
              """,
            """
              import { J } from "@openrewrite/rewrite/java";
              import { JS } from "@openrewrite/rewrite/javascript";

              function matches(node: J.Identifier, jsNode: JS.Binary, name: string) {
                  return /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/node.toString() === name && /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/jsNode.toString() === name && name.toString() === "test";
              }
              """
          )
        );
    }

    @Test
    void typescriptStringConversions() {
        rewriteRun(
          rewriteTypes(),
          typescript(
            """
              import { Tree } from "@openrewrite/rewrite";

              function text(tree: Tree, trees: Tree[], name: string) {
                  const a = String(tree);
                  const b = "Tree: " + tree;
                  let c = "";
                  c += tree;
                  const d = `Tree: ${tree}`;
                  const e = trees.toString();
                  const f = `${name}`;
                  const g = String(name);
              }
              """,
            """
              import { Tree } from "@openrewrite/rewrite";

              function text(tree: Tree, trees: Tree[], name: string) {
                  const a = String(/*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/tree);
                  const b = "Tree: " + /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/tree;
                  let c = "";
                  c += /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/tree;
                  const d = `Tree: ${/*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/tree}`;
                  const e = /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/trees.toString();
                  const f = `${name}`;
                  const g = String(name);
              }
              """
          )
        );
    }

    @Test
    void typescriptNonConversions() {
        rewriteRun(
          rewriteTypes(),
          typescript(
            """
              import { Tree } from "@openrewrite/rewrite";

              declare function template(strings: TemplateStringsArray, ...trees: Tree[]): Tree;
              function preserveTree(tree: Tree) {
                  return template`${tree}`;
              }
              function unrelated(value: { toString(): string }, name: string) {
                  return value.toString() === name.toString();
              }
              """
          )
        );
    }

    @Test
    void typescriptTestSourcesAreIgnored() {
        rewriteRun(
          rewriteTypes(),
          typescript(
            """
              import { J } from "@openrewrite/rewrite/java";

              function matches(node: J.Identifier) {
                  return node.toString() === "test";
              }
              """,
            spec -> spec.path("src/recipe.test.ts")
          )
        );
    }

    @Test
    void javaScriptTreeToString() {
        rewriteRun(
          rewriteTypes(),
          javascript(
            """
              import { tree } from "@openrewrite/rewrite";

              function matches() {
                  return tree().toString() === "test";
              }
              """,
            """
              import { tree } from "@openrewrite/rewrite";

              function matches() {
                  return /*~~(LST converted to text. For matching, use node properties (`simpleName`, `J.Literal.value`), type utilities, a method matcher or a visitor. Reuse trees directly or through template parameters. If source text is required, use a printer with a cursor.)~~>*/tree().toString() === "test";
              }
              """,
            spec -> spec.path("recipe.js")
          )
        );
    }

    private static SourceSpecs rewriteTypes() {
        return typescript(
            """
              declare module "@openrewrite/rewrite" {
                  export interface Tree {
                      readonly id: string;
                  }
                  export function tree(): Tree;
              }
              declare module "@openrewrite/rewrite/javascript" {
                  import { J } from "@openrewrite/rewrite/java";
                  export namespace JS {
                      export interface Binary extends J {}
                  }
              }
              declare module "@openrewrite/rewrite/java" {
                  import { Tree } from "@openrewrite/rewrite";
                  export interface J extends Tree {}
                  export namespace J {
                      export interface Identifier extends J {
                          readonly simpleName: string;
                      }
                  }
              }
              """,
            spec -> spec.path("rewrite.d.ts")
          );
    }

    @DocumentExample
    @Test
    void toStringAndConcatenation() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.marker.SearchResult;

              class A extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      if (method.getSelect() != null && method.getSelect().toString().startsWith("Foo")) {
                          return SearchResult.found(method, "Called on " + method.getSelect());
                      }
                      return method;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.java.JavaIsoVisitor;
              import org.openrewrite.java.tree.J;
              import org.openrewrite.marker.SearchResult;

              class A extends JavaIsoVisitor<ExecutionContext> {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                      if (method.getSelect() != null && /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/method.getSelect().toString().startsWith("Foo")) {
                          return SearchResult.found(method, "Called on " + /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/method.getSelect());
                      }
                      return method;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertingMethods() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.util.Objects;

              class A {
                  void m(J.Identifier id, StringBuilder sb) {
                      String a = String.valueOf(id);
                      String b = Objects.toString(id, "");
                      String c = String.format("%s and %s", id, id.getSimpleName());
                      String d = "%s".formatted(id);
                      sb.append(id).append(id.getSimpleName());
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.util.Objects;

              class A {
                  void m(J.Identifier id, StringBuilder sb) {
                      String a = String.valueOf(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                      String b = Objects.toString(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id, "");
                      String c = String.format("%s and %s", /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id, id.getSimpleName());
                      String d = "%s".formatted(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                      sb.append(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id).append(id.getSimpleName());
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReferenceAndCompoundAssignment() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.stream.Collectors;

              class A {
                  String m(List<J.Identifier> ids, J.Literal literal) {
                      String s = ids.stream().map(J.Identifier::toString).collect(Collectors.joining());
                      s += literal;
                      return s;
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.stream.Collectors;

              class A {
                  String m(List<J.Identifier> ids, J.Literal literal) {
                      String s = ids.stream().map(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/J.Identifier::toString).collect(Collectors.joining());
                      s += /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/literal;
                      return s;
                  }
              }
              """
          )
        );
    }

    @Test
    void printingAndInsertingMethods() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.text.MessageFormat;

              class A {
                  String m(J.Identifier id, StringBuilder sb) {
                      System.out.println(id);
                      System.out.printf("%s%n", id);
                      sb.insert(0, id);
                      return MessageFormat.format("{0}", id);
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.text.MessageFormat;

              class A {
                  String m(J.Identifier id, StringBuilder sb) {
                      System.out.println(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                      System.out.printf("%s%n", /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                      sb.insert(0, /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                      return MessageFormat.format("{0}", /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/id);
                  }
              }
              """
          )
        );
    }

    @Test
    void conversionMethodReferencesOnTreeStreams() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Collectors;

              class A {
                  String m(List<J.Identifier> ids, List<String> names) {
                      return ids.stream().map(Object::toString).collect(Collectors.joining()) +
                             ids.stream().map(String::valueOf).collect(Collectors.joining()) +
                             ids.stream().map(Objects::toString).collect(Collectors.joining()) +
                             names.stream().map(Object::toString).collect(Collectors.joining());
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Collectors;

              class A {
                  String m(List<J.Identifier> ids, List<String> names) {
                      return ids.stream().map(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/Object::toString).collect(Collectors.joining()) +
                             ids.stream().map(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/String::valueOf).collect(Collectors.joining()) +
                             ids.stream().map(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/Objects::toString).collect(Collectors.joining()) +
                             names.stream().map(Object::toString).collect(Collectors.joining());
                  }
              }
              """
          )
        );
    }

    @Test
    void collectionsOfTrees() {
        rewriteRun(
          java(
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;

              class A {
                  String m(List<J.Identifier> ids, Map<String, ? extends J> byName, Optional<J> maybe, List<String> names, Map<String, Integer> counts) {
                      return ids.toString() + byName + String.valueOf(maybe) + names + counts.toString();
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.J;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;

              class A {
                  String m(List<J.Identifier> ids, Map<String, ? extends J> byName, Optional<J> maybe, List<String> names, Map<String, Integer> counts) {
                      return /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/ids.toString() + /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/byName + String.valueOf(/*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/maybe) + names + counts.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void typeVariableBoundByTree() {
        rewriteRun(
          java(
            """
              import org.openrewrite.SourceFile;

              class A {
                  <T extends SourceFile> String m(T sourceFile) {
                      return sourceFile.toString();
                  }
              }
              """,
            """
              import org.openrewrite.SourceFile;

              class A {
                  <T extends SourceFile> String m(T sourceFile) {
                      return /*~~(LST converted to text. For matching, use node accessors (`getSimpleName()`, `J.Literal.getValue()`), `TypeUtils`, `MethodMatcher` or a visitor; for expression comparison, consider `SemanticallyEqual`. Reuse trees directly or through `JavaTemplate` parameters. If source text is required, use `print(Cursor)` on each tree.)~~>*/sourceFile.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void noTreeConvertedToString() {
        rewriteRun(
          java(
            """
              import org.openrewrite.Cursor;
              import org.openrewrite.java.tree.J;

              class A {
                  String m(J.MethodInvocation method, Cursor cursor, int count) {
                      String name = "Called " + method.getSimpleName() + " " + count + " times";
                      String type = "Of type " + method.getMethodType() + method.getMethodType().toString();
                      return name + type + method.print(cursor) + method.getPrefix().toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void testSourcesAreIgnored() {
        rewriteRun(
          srcTestJava(
            java(
              """
                import org.openrewrite.java.tree.J;

                class ATest {
                    String m(J.Identifier id) {
                        return id.toString() + id;
                    }
                }
                """
            )
          )
        );
    }
}
