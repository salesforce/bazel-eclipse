package com.salesforce.bazel.eclipse.core.extensions;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PLUGIN_ID;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor;

public class EclipseHeadlessBazelCommandExecutor extends DefaultBazelCommandExecutor {

    private final class InitBinaryJob extends Job {
        private final Path binary;

        private InitBinaryJob(Path binary) {
            super(format("Detecting Bazel version...", binary));
            this.binary = binary;
            setSystem(true);
            setPriority(SHORT);
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {
                List<String> commandLine = List.of(binary.toString(), "--version");
                if (isWrapExecutionIntoShell()) {
                    commandLine = wrapExecutionIntoShell(commandLine);
                }
                var pb = new ProcessBuilder(commandLine);
                pb.redirectErrorStream();
                var stdoutFile = File.createTempFile("bazel_version_", ".txt");
                pb.redirectOutput(stdoutFile);
                var result = pb.start().waitFor();
                if (result == 0) {
                    var lines = Files.readAllLines(stdoutFile.toPath());
                    for (String potentialVersion : lines) {
                        if (potentialVersion.startsWith(BAZEL_VERSION_PREFIX)) {
                            setBazelBinary(new BazelBinary(binary, BazelVersion
                                    .parseVersion(potentialVersion.substring(BAZEL_VERSION_PREFIX.length()))));
                            return Status.OK_STATUS;
                        }
                    }
                }
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.OK_STATUS;
            } catch (IOException e) {
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.error(format("Unable to detect Bazel version of '%s'!", binary), e);
            } catch (InterruptedException e) {
                LOG.warn("Interrupted waiting for bazel --version to respond for binary '{}'", binary, e);
                setBazelBinary(UNKNOWN_BAZEL_BINARY);
                return Status.CANCEL_STATUS;
            }
        }
    }

    static final String BAZEL_VERSION_PREFIX = "bazel ";
    static BazelBinary UNKNOWN_BAZEL_BINARY = new BazelBinary(Path.of("bazel"), new BazelVersion(999, 999, 999));
    static Logger LOG = LoggerFactory.getLogger(EclipseHeadlessBazelCommandExecutor.class);

    public static final String PREF_KEY_BAZEL_BINARY = "bazelBinary";

    private final IPreferenceChangeListener preferencesListener = event -> {
        if (PREF_KEY_BAZEL_BINARY.equalsIgnoreCase(event.getKey())) {
            newInitJobFromPreferences();
        }
    };

    private final IEclipsePreferences[] preferencesLookup = { getInstanceScopeNode(), getDefaultScopeNode() };

    public EclipseHeadlessBazelCommandExecutor() {
        // start with a simple default
        setBazelBinary(UNKNOWN_BAZEL_BINARY);
        // add listener to instance scope
        // note: we never unregister because we do expect BazelCorePlugin lifetime to match that of the IDE process
        preferencesLookup[0].addPreferenceChangeListener(preferencesListener);
        // schedule initial initialization sync
        var job = newInitJobFromPreferences();
        job.schedule();
        try {
            job.join();
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted waiting for Bazel binary initialization to happen");
        }
    }

    IEclipsePreferences getDefaultScopeNode() {
        return DefaultScope.INSTANCE.getNode(PLUGIN_ID);
    }

    IEclipsePreferences getInstanceScopeNode() {
        return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
    }

    @Override
    protected OutputStream getPreferredErrorStream() {
        // assuming this is displayed in the Eclipse Console, using System.out avoids the red coloring
        return System.out;
    }

    @Override
    protected void injectAdditionalOptions(List<String> commandLine) {
        // tweak for Eclipse Console
        commandLine.add("--color=yes");
        commandLine.add("--curses=no");
    }

    InitBinaryJob newInitJobFromPreferences() {
        var binary = Path.of(Platform.getPreferencesService().get(PREF_KEY_BAZEL_BINARY, "bazel", preferencesLookup));
        return new InitBinaryJob(binary);
    }

    @Override
    public void setBazelBinary(BazelBinary bazelBinary) {
        LOG.info("Using default Bazel binary '{}' (version '{}')", bazelBinary.executable(),
            bazelBinary.bazelVersion());
        super.setBazelBinary(bazelBinary);
    }
}
