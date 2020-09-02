package com.salesforce.bazel.sdk.console;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Creates CommandConsole instances using stdout/stderr.
 */
public class StandardCommandConsoleFactory implements CommandConsoleFactory {

	@Override
	public CommandConsole get(String name, String title) throws IOException {
		return new CommandConsole() {

			@Override
			public OutputStream createOutputStream() {
				return System.out;
			}

			@Override
			public OutputStream createErrorStream() {
				return System.err;
			}
		};
	}

}
