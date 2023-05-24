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

import org.junit.jupiter.api.Test;

/** Tests label validation */
public class LabelTest {

    @Test
    public void testConstructor() {
        var externalWorkspaceName = "ext_workspace";
        var packagePath = new WorkspacePath("package/path");
        var targetName = TargetName.create("target/name");

        var label = Label.create(externalWorkspaceName, packagePath, targetName);
        assertThat(label.toString()).isEqualTo("@ext_workspace//package/path:target/name");
        assertThat(label.externalWorkspaceName()).isEqualTo(externalWorkspaceName);
        assertThat(label.blazePackage()).isEqualTo(packagePath);
        assertThat(label.targetName()).isEqualTo(targetName);
    }

    @Test
    public void testConstructorExternalWorkspace() {
        var packagePath = new WorkspacePath("package/path");
        var targetName = TargetName.create("target/name");

        var label = Label.create(packagePath, targetName);
        assertThat(label.toString()).isEqualTo("//package/path:target/name");
        assertThat(label.blazePackage()).isEqualTo(packagePath);
        assertThat(label.targetName()).isEqualTo(targetName);
    }

    @Test
    public void testFactoryMethod() {
        var fullLabel = "//package/path:target/name";
        var label = Label.create(fullLabel);
        assertThat(label.toString()).isEqualTo(fullLabel);
        assertThat(label.blazePackage()).isEqualTo(new WorkspacePath("package/path"));
        assertThat(label.targetName()).isEqualTo(TargetName.create("target/name"));
    }

    @Test
    public void testFactoryMethodExternalWorkspace() {
        var fullLabel = "@ext_workspace//package/path:target/name";
        var label = Label.create(fullLabel);
        assertThat(label.toString()).isEqualTo(fullLabel);
        assertThat(label.externalWorkspaceName()).isEqualTo("ext_workspace");
        assertThat(label.blazePackage()).isEqualTo(new WorkspacePath("package/path"));
        assertThat(label.targetName()).isEqualTo(TargetName.create("target/name"));
    }

    @Test
    public void testValidateLabel() {
        // Valid labels
        assertThat(Label.validate("//foo:bar")).isNull();
        assertThat(Label.validate("//foo/baz:bar")).isNull();
        assertThat(Label.validate("//:bar")).isNull();

        // Invalid labels
        assertThat(Label.validate("//foo")).isNotEmpty();
        assertThat(Label.validate("foo")).isNotEmpty();
        assertThat(Label.validate("foo:bar")).isNotEmpty();
    }

    @Test
    public void testValidatePackage() {
        // Legal names
        assertThat(Label.validatePackagePath("foo")).isNull();
        assertThat(Label.validatePackagePath("f")).isNull();
        assertThat(Label.validatePackagePath("fooBAR")).isNull();
        assertThat(Label.validatePackagePath("foo/bar")).isNull();
        assertThat(Label.validatePackagePath("f9oo")).isNull();
        assertThat(Label.validatePackagePath("f_9oo")).isNull();
        assertThat(Label.validatePackagePath("Foo")).isNull();
        assertThat(Label.validatePackagePath("9.oo")).isNull();
        // This is not advised but is technically legal
        assertThat(Label.validatePackagePath("")).isNull();

        // Illegal names
        assertThat(Label.validatePackagePath("/foo")).isNotEmpty();
        assertThat(Label.validatePackagePath("foo/")).isNotEmpty();
        assertThat(Label.validatePackagePath("foo//bar")).isNotEmpty();
    }
}
