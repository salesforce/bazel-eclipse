/*-
 *
 */
package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.newOutputStream;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link FilterOutputStream} which captures bytes written to another output stream into a file.
 */
public class FileCapturingOutputStream extends FilterOutputStream {

    private final Path file;
    private final OutputStream fileOut;

    /**
     * @param out
     *            {@link OutputStream} to capture
     * @throws IOException
     */
    public FileCapturingOutputStream(OutputStream out) throws IOException {
        this(out, Files.createTempFile("FileCapturingOutputStream", ".bin"));
    }

    public FileCapturingOutputStream(OutputStream out, Path file) throws IOException {
        super(out);
        this.file = file;
        fileOut = new BufferedOutputStream(newOutputStream(file));
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            fileOut.close();
        }
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        fileOut.flush();
    }

    public Path getFile() {
        return file;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        fileOut.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        fileOut.write(b);
    }
}
