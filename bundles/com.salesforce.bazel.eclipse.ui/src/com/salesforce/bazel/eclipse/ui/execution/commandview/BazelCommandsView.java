package com.salesforce.bazel.eclipse.ui.execution.commandview;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.PageBook;

import jakarta.inject.Inject;

public class BazelCommandsView {

    private final TreeViewer commandsTree;
    private final PageBook commandsOutput;

    @Inject
    public BazelCommandsView(Composite parent) {

        var composite = new Composite(parent, SWT.NONE);
        composite.setLayoutData(GridDataFactory.fillDefaults());
        composite.setLayout(new RowLayout());

        commandsTree = new TreeViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL);
        commandsTree.getTree().setHeaderVisible(false);
        commandsTree.getTree().setLinesVisible(false);

        var viewerColumn = new TreeViewerColumn(commandsTree, SWT.NONE);
        viewerColumn.getColumn().setWidth(300);
        viewerColumn.getColumn().setText("Names");
        viewerColumn.setLabelProvider(new ColumnLabelProvider());

        commandsOutput = new PageBook(composite, SWT.H_SCROLL | SWT.V_SCROLL);
    }

}
