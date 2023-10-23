package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import java.util.Objects;

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * An entry pointing to another target, which may be generated output.
 */
public class LabelEntry implements Entry {

    private final BazelLabel label;

    public LabelEntry(BazelLabel label) {
        this.label = label;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        var other = (LabelEntry) obj;
        return Objects.equals(label, other.label);
    }

    /**
     * @return the label
     */
    public BazelLabel getLabel() {
        return label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public String toString() {
        return label.toString();
    }

}