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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class RemoveInlineDeprecatedMethodsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.recipes.RemoveInlineDeprecatedMethodsRecipe");
    }

    @DocumentExample
    @Test
    void removeRecipeAndItsCsvRow() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.recipe.rewrite-example.InlineDeprecatedMethods
              displayName: Inline deprecated delegating methods
              description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: 'com.example.Foo bar(String)'
                    replacement: 'baz(s)'
              """,
            null,
            spec -> spec.path("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml")
          ),
          text(
            """
              ecosystem,packageName,name,displayName,description,recipeCount,category1,category2,category1Description,category2Description,options,dataTables
              maven,org.openrewrite.recipe:rewrite-example,org.openrewrite.recipe.rewrite-example.InlineDeprecatedMethods,Inline deprecated delegating methods,Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.,2,Rewrite-example,Recipe,,,,
              maven,org.openrewrite.recipe:rewrite-example,com.example.MyRecipe,My recipe,Does something useful.,1,Example,,,,,
              """,
            """
              ecosystem,packageName,name,displayName,description,recipeCount,category1,category2,category1Description,category2Description,options,dataTables
              maven,org.openrewrite.recipe:rewrite-example,com.example.MyRecipe,My recipe,Does something useful.,1,Example,,,,,
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/recipes.csv")
          )
        );
    }

    @Test
    void keepOtherRecipeFiles() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/my-recipe.yml")
          ),
          text(
            """
              ecosystem,packageName,name,displayName,description,recipeCount,category1,category2,category1Description,category2Description,options,dataTables
              maven,org.openrewrite.recipe:rewrite-example,com.example.MyRecipe,My recipe,Does something useful.,1,Example,,,,,
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/recipes.csv")
          )
        );
    }
}
