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

import org.openrewrite.Recipe;

public class SourceSpecTextBlockNewLine extends Recipe {
    @Override
    public String getDisplayName() {
        return "New line at the end of `SourceSpecs` text blocks";
    }

    @Override
    public String getDescription() {
        return "Text blocks that assert before and after source code should have a new line after it is closed.";
    }
}
