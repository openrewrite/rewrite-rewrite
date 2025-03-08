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
package org.openrewrite.rewrite;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.concurrent.atomic.AtomicReference;

@Getter
public class Recipe implements Trait<Tree> {
  private static final MethodMatcher getDisplayName = new MethodMatcher("org.openrewrite.Recipe getDisplayName()", true);
  private static final MethodMatcher getDescription = new MethodMatcher("org.openrewrite.Recipe getDescription()", true);

  private Cursor cursor;

  public Recipe(Cursor cursor) {
    this.cursor = cursor;
  }

  public String getDisplayName() {
    if (getTree() instanceof J.ClassDeclaration) {
      return getLiteralReturnValue(getDisplayName);
    }
    return getYamlMappingValue("displayName");
  }

  public Recipe withDisplayName(String displayName) {
    if (getTree() instanceof J.ClassDeclaration) {
      return withLiteralReturnValue(getDisplayName, displayName);
    }
    return withYamlMappingValue("displayName", displayName);
  }

  public String getDescription() {
    if (getTree() instanceof J.ClassDeclaration) {
      return getLiteralReturnValue(getDescription);
    }
    return getYamlMappingValue("description");
  }

  public Recipe withDescription(String description) {
    if (getTree() instanceof J.ClassDeclaration) {
      return withLiteralReturnValue(getDescription, description);
    }
    return withYamlMappingValue("description", description);
  }

  private String getYamlMappingValue(String key) {
    AtomicReference<String> value = new AtomicReference<>();
    new YamlVisitor<Integer>() {
      @Override
      public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, Integer p) {
        if (new JsonPathMatcher("$." + key).matches(getCursor()) &&
            entry.getValue() instanceof Yaml.Scalar) {
          value.set(((Yaml.Scalar) entry.getValue()).getValue());
        }
        return entry;
      }
    }.visit(getTree(), 0, cursor.getParentOrThrow());
    return value.get();
  }

  private String getLiteralReturnValue(MethodMatcher method) {
    StringBuilder retValue = new StringBuilder();
    J.ClassDeclaration cd = (J.ClassDeclaration) getTree();
    new JavaIsoVisitor<StringBuilder>() {
      @Override
      public J.Return visitReturn(J.Return aReturn, StringBuilder ret) {
        J.MethodDeclaration md = getCursor().firstEnclosing(J.MethodDeclaration.class);
        if (md != null && method.matches(md, cd)) {
          if (aReturn.getExpression() instanceof J.Literal) {
            ret.append(((J.Literal) aReturn.getExpression()).getValue());
          }
        }
        return aReturn;
      }
    }.visit(cd, retValue, getCursor().getParentOrThrow());
    return retValue.toString();
  }

  private Recipe withLiteralReturnValue(MethodMatcher method, String value) {
    J.ClassDeclaration cd = (J.ClassDeclaration) getTree();
    cursor = new Cursor(cursor.getParent(), new JavaIsoVisitor<Integer>() {
      @Override
      public J.Return visitReturn(J.Return aReturn, Integer p) {
        J.MethodDeclaration md = getCursor().firstEnclosing(J.MethodDeclaration.class);
        if (md != null && method.matches(md, cd)) {
          if (aReturn.getExpression() instanceof J.Literal) {
            J.Literal exp = (J.Literal) aReturn.getExpression();
            if (!value.equals(exp.getValue())) {
              return aReturn.withExpression(exp
                .withValue(value)
                .withValueSource("\"" + value + "\""));
            }
          }
        }
        return aReturn;
      }
    }.visitNonNull(cd, 0, getCursor().getParentOrThrow()));
    return this;
  }

  private Recipe withYamlMappingValue(String key, String value) {
    cursor = new Cursor(cursor.getParent(), new YamlVisitor<Integer>() {
      @Override
      public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, Integer p) {
        if (new JsonPathMatcher("$." + key).matches(getCursor()) &&
            entry.getValue() instanceof Yaml.Scalar) {
          return entry.withValue(((Yaml.Scalar) entry.getValue()).withValue(value));
        }
        return entry;
      }
    }.visitNonNull(getTree(), 0, cursor.getParentOrThrow()));
    return this;
  }

  public static class Matcher extends SimpleTraitMatcher<Recipe> {
    @Override
    protected @Nullable Recipe test(Cursor cursor) {
      Object value = cursor.getValue();
      if (value instanceof J.ClassDeclaration) {
        J.ClassDeclaration classDecl = (J.ClassDeclaration) value;
        if (TypeUtils.isAssignableTo("org.openrewrite.Recipe", classDecl.getType())) {
          return new Recipe(cursor);
        }
      } else if (value instanceof Yaml.Document) {
        AtomicReference<Recipe> recipe = new AtomicReference<>();
        new YamlVisitor<Integer>() {
          @Override
          public Yaml visitScalar(Yaml.Scalar scalar, Integer p) {
            if (new JsonPathMatcher("$.type").matches(getCursor().getParentOrThrow()) &&
                "specs.openrewrite.org/v1beta/recipe".equals(scalar.getValue())) {
              recipe.set(new Recipe(cursor));
            }
            return scalar;
          }
        }.visit((Yaml.Document) value, 0, cursor.getParentOrThrow());
        return recipe.get();
      }
      return null;
    }
  }
}
