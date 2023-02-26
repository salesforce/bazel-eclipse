package com.salesforce.bazel.eclipse.core.projectview;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raw section in a <code>.bazelproject</code> file
 */
class RawSection {

    private final String name;
    private final String body;

    public RawSection(String name, String body) {
        this.name = name;
        this.body = body;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        var other = (RawSection) obj;
        return Objects.equals(this.name, other.name) && Objects.equals(this.body, other.body);
    }

    /**
     * @return trimmed version of the body as {@link Path}
     * @throws NullPointerException
     *             if there is no body
     */
    public Path getBodyAsPath() {
        return Path.of(getBodyAsSingleValue());
    }

    /**
     * @return trimmed version of the body
     * @throws NullPointerException
     *             if there is no body
     */
    public String getBodyAsSingleValue() {
        return requireNonNull(this.body, () -> format("invalid section '%s' - no body", name)).trim();
    }

    public String getName() {
        return this.name;
    }

    public String getRawBody() {
        return this.body;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, body);
    }

    @Override
    public String toString() {
        return this.name + ":\n---\n" + this.body + "\n---\n";
    }
}