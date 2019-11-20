// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.salesforce.bazel.eclipse.wizard.old;

import java.io.File;

import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import com.google.common.collect.ImmutableList;

/**
 * A tree content provider that enable selecting a list of sub-directories of a directory root (the Bazel workspace
 * root).
 * 
 * REFERENCE ONLY! This is the old mechanism from the original Bazel plugin. It used the New... extension point instead
 * of the Import... extension point.
 * 
 */
public class DirectoryTreeContentProvider implements ITreeContentProvider {

    private File root;

    public DirectoryTreeContentProvider(File root) {
        this.root = root;
    }

    @Override
    public void dispose() {}

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

    @Override
    public Object[] getElements(Object inputElement) {
        if (root == null) {
            return new Object[] {};
        }
        // We only have one root
        return new Object[] { root };
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        return ((File) parentElement).listFiles(
            (f) -> (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().startsWith("bazel-")));
    }

    @Override
    public Object getParent(Object element) {
        File file = (File) element;
        if (file.equals(root)) {
            return null;
        }
        return file.getParentFile();
    }

    @Override
    public boolean hasChildren(Object element) {
        Object[] childrens = getChildren(element);
        return (childrens != null) && (childrens.length > 0);
    }

    public File getRoot() {
        return root;
    }

    public void setRoot(File root) {
        this.root = root;
    }

    /**
     * Create a tree view that use a FileTreeContentProvider for its content. The created tree view can be used to
     * select branches of a directory tree.
     * 
     * @param container
     *            The parent composite for the created tree view.
     * @return A checkbox tree view
     */
    public static CheckboxTreeViewer createTreeView(Composite container) {
        final CheckboxTreeViewer tv = new CheckboxTreeViewer(container, SWT.BORDER);
        tv.setContentProvider(new DirectoryTreeContentProvider(null));
        tv.setLabelProvider(new ILabelProvider() {

            @Override
            public void removeListener(ILabelProviderListener listener) {
                // we do not have event notifying listeners, ignore.
            }

            @Override
            public boolean isLabelProperty(Object element, String property) {
                return false;
            }

            @Override
            public void dispose() {}

            @Override
            public void addListener(ILabelProviderListener listener) {
                // we do not have event notifying listeners, ignore.
            }

            @Override
            public Image getImage(Object element) {
                return null;
            }

            @Override
            public String getText(Object element) {
                return ((File) element).getName();
            }
        });
        tv.setInput("root"); // pass a non-null that will be ignored

        tv.addCheckStateListener(event -> setChecked(tv, event.getElement()));

        return tv;
    }

    private static void setChecked(final CheckboxTreeViewer tv, Object element) {
        // When user checks a checkbox in the tree, check all its children and mark parent as greyed
        // When a user uncheck a checkbox, mark the subtree as unchecked and ungrayed and if unique
        // sibling parent as grayed.
        DirectoryTreeContentProvider provider = (DirectoryTreeContentProvider) tv.getContentProvider();

        boolean isChecked = tv.getChecked(element);
        if (tv.getGrayed(element)) {
            isChecked = !isChecked;
        }
        tv.setChecked(element, isChecked);
        tv.setGrayed(element, false);
        if (isChecked) {
            tv.setSubtreeChecked(element, true);
        } else {
            tv.setSubtreeChecked(element, false);
        }
        setGrayed(tv, provider.getParent(element));
    }

    private static void setGrayed(CheckboxTreeViewer tv, Object element) {
        if (element == null) {
            return;
        }
        DirectoryTreeContentProvider provider = (DirectoryTreeContentProvider) tv.getContentProvider();
        boolean checked = tv.getChecked(element);
        boolean grayed = false;
        for (Object object : provider.getChildren(element)) {
            grayed = grayed || tv.getGrayed(object) || tv.getChecked(object);
            checked = checked && tv.getChecked(object) && !tv.getGrayed(element);
        }
        if (checked) {
            tv.setChecked(element, true);
            tv.setGrayed(element, false);
        } else if (grayed) {
            tv.setGrayChecked(element, true);
        } else {
            tv.setChecked(element, false);
            tv.setGrayed(element, false);
        }
        setGrayed(tv, provider.getParent(element));
    }

    /**
     * Set the root of the directory tree view and refresh the view if appropriate
     */
    public static void setFileTreeRoot(CheckboxTreeViewer tv, File root) {
        DirectoryTreeContentProvider provider = (DirectoryTreeContentProvider) tv.getContentProvider();
        if ((root == null && provider.getRoot() != null) || !root.equals(provider.getRoot())) {
            provider.setRoot(root);
            tv.refresh();
        }
    }

    /**
     * Returns the list of path selected in <code>tv</code>. It returns the list of checked path without the children of
     * the checked path. Each path is returned as a string giving the relative path from the root of the tree.
     */
    public static ImmutableList<String> getSelectPathsRelativeToRoot(CheckboxTreeViewer tv) {
        DirectoryTreeContentProvider provider = (DirectoryTreeContentProvider) tv.getContentProvider();
        String root = provider.root.getAbsolutePath();
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Object element : tv.getCheckedElements()) {
            if (!tv.getGrayed(element)) {
                Object parent = provider.getParent(element);
                if (parent == null || tv.getGrayed(parent)) {
                    // Only add this element if its parent is not selected (so it's the root).
                    String path = ((File) element).getAbsolutePath();
                    // Strip root from path
                    if (path.startsWith(root)) {
                        path = path.substring(root.length());
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }
                        builder.add(path);
                    }
                }
            }
        }
        return builder.build();
    }
}
