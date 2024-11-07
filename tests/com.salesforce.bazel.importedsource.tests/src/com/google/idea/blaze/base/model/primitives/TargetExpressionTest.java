/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.google.idea.blaze.base.model.primitives;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/** Tests for {@link com.google.idea.blaze.base.model.primitives.TargetExpression}. */
public class TargetExpressionTest {

    @Test
    public void emptyExpressionShouldThrow() {
        try {
            TargetExpression.fromString("");
            fail("Empty expressions should not be allowed.");
        } catch (InvalidTargetException expected) {}
    }

    @Test
    public void globExpressionShouldYieldGeneralTargetExpression() {
        var target = TargetExpression.fromStringSafe("//package/...");
        assertThat(target.getClass()).isSameInstanceAs(TargetExpression.class);
    }

    @Test
    public void testFailingValidations() {
        assertThat(TargetExpression.validate("@/package_path:rule")).isNotNull();
        assertThat(TargetExpression.validate("@repo&//package_path:rule")).isNotNull();
        assertThat(TargetExpression.validate("../path")).isNotNull();
        assertThat(TargetExpression.validate("path/../other")).isNotNull();
        assertThat(TargetExpression.validate("path/.:rule")).isNotNull();
        assertThat(TargetExpression.validate("//path:rule:other_rule")).isNotNull();
        assertThat(TargetExpression.validate("//path:rule/")).isNotNull();
        assertThat(TargetExpression.validate("//path:rule//a")).isNotNull();
    }

    @Test
    public void testPassingValidations() {
        assertThat(TargetExpression.validate("foo:bar")).isNull();
        assertThat(TargetExpression.validate("foo:all")).isNull();
        assertThat(TargetExpression.validate("foo/...:all")).isNull();
        assertThat(TargetExpression.validate("foo:*")).isNull();

        assertThat(TargetExpression.validate("//foo")).isNull();
        assertThat(TargetExpression.validate("-//foo:bar")).isNull();
        assertThat(TargetExpression.validate("-//foo:all")).isNull();

        assertThat(TargetExpression.validate("//foo/all")).isNull();
        assertThat(TargetExpression.validate("java/com/google/foo/Bar.java")).isNull();
        assertThat(TargetExpression.validate("//foo/...:all")).isNull();

        assertThat(TargetExpression.validate("//...")).isNull();
        assertThat(TargetExpression.validate("@//package_path:rule")).isNull();
        assertThat(TargetExpression.validate("@repo//foo:bar")).isNull();
        assertThat(TargetExpression.validate("@repo//foo:all")).isNull();
        assertThat(TargetExpression.validate("-@repo//:bar")).isNull();
    }

    @Test
    public void validLabelShouldYieldLabel() {
        var target = TargetExpression.fromStringSafe("//package:rule");
        assertThat(target).isInstanceOf(Label.class);
    }
}
