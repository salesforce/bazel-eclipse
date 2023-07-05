package com.salesforce.bazel.eclipse.ui.execution;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.ui.console.MessageConsoleStream;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

public class CapturingLiniesAndForwardingOutputStreamTest {

    private static final Predicate<String> errorPrefixFilter = (var s) -> s.startsWith("ERROR:");

    private MessageConsoleStream messageConsoleStream;

    private List<String> generateLines(int numberOfLines) {
        assertThat(numberOfLines, greaterThan(Integer.valueOf(0)));
        var lines = new ArrayList<String>();
        for (var i = 0; i < numberOfLines; i++) {
            lines.add("line äüöß \\ \"' " + i);
        }
        return lines;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -300 })
    void invalid_number_of_lines(int numberOfLines) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            try (var stream = new CapturingLiniesAndForwardingOutputStream(
                    messageConsoleStream,
                    UTF_8,
                    numberOfLines,
                    errorPrefixFilter)) {
                // empty
            }
        });
    }

    @BeforeEach
    protected void setUp() throws Exception {
        messageConsoleStream = mock(MessageConsoleStream.class);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        Mockito.validateMockitoUsage();
    }

    @ParameterizedTest
    @ValueSource(ints = { 15, 20, 16000 })
    public void write_more_then_captured_lines(int numberOfLines) throws IOException {
        var linesToCapture = 8;
        var lines = generateLines(numberOfLines);

        try (var stream = new CapturingLiniesAndForwardingOutputStream(
                messageConsoleStream,
                UTF_8,
                linesToCapture,
                errorPrefixFilter)) {
            for (String line : lines) {
                writeLineToStream(stream, line, UTF_8);
            }

            // done writing
            stream.close();

            // check sizes match
            var capturedLines = stream.getCapturedLines();
            assertEquals(linesToCapture, capturedLines.size());

            // check items match
            var lastLines = lines.subList(lines.size() - linesToCapture, lines.size());
            assertThat(capturedLines, IsIterableContainingInOrder.contains((lastLines).toArray()));
        }
    }

    @Test
    void write_single_incomplete_line() throws Exception {
        try (var stream =
                new CapturingLiniesAndForwardingOutputStream(messageConsoleStream, UTF_8, 4, errorPrefixFilter)) {

            var s1 = "s1 " + System.nanoTime();
            var s2 = " s2 " + System.nanoTime();

            stream.write(s1.getBytes(UTF_8));
            stream.write(s2.getBytes(UTF_8));

            // done writing
            stream.close();

            // check sizes match
            var capturedLines = stream.getCapturedLines();
            assertEquals(1, capturedLines.size());

            // check items match
            assertThat(capturedLines, IsIterableContainingInOrder.contains(s1 + s2));
        }
    }

    private void writeLineToStream(CapturingLiniesAndForwardingOutputStream stream, String line, Charset charset)
            throws IOException {
        line += "\n";
        stream.write(line.getBytes(charset));
    }

}
