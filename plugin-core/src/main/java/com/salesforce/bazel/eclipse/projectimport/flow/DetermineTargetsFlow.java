package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Determines the configured targets, for import, for each Bazel Package being imported.
 */
public class DetermineTargetsFlow implements ImportFlow {

    private static final LogHelper LOG = LogHelper.log(DetermineTargetsFlow.class);

    @Override
    public String getProgressText() {
        return "Analyzing targets";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getSelectedBazelPackages());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressMonitor) {
        Map<BazelPackageLocation, List<BazelLabel>> packageLocationToTargets = new HashMap<>();
        for (BazelPackageLocation packageLocation : ctx.getSelectedBazelPackages()) {
            List<BazelLabel> targets = packageLocation.getBazelTargets();
            if (targets == null) {
                EclipseProjectStructureInspector inspector = new EclipseProjectStructureInspector(packageLocation);
                targets = inspector.getBazelTargets();
            }
            packageLocationToTargets.put(packageLocation, Collections.unmodifiableList(targets));
            LOG.info("Configured targets for " + packageLocation.getBazelPackageFSRelativePath() + ": " + targets);
        }
        ctx.setPackageLocationToTargets(packageLocationToTargets);
    }
}
