package org.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConsoleLogRedirector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleLogRedirector.class);

    private ConsoleLogRedirector() {
    }

    static void install() {
        System.setOut(new PrintStream(new LineLoggingStream(false), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new LineLoggingStream(true), true, StandardCharsets.UTF_8));
    }

    private static final class LineLoggingStream extends OutputStream {
        private final boolean errorStream;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private LineLoggingStream(boolean errorStream) {
            this.errorStream = errorStream;
        }

        @Override
        public synchronized void write(int value) {
            if (value == '\n') {
                flushLine();
            } else if (value != '\r') {
                line.write(value);
            }
        }

        @Override
        public synchronized void flush() {
            flushLine();
        }

        private void flushLine() {
            if (line.size() == 0) {
                return;
            }
            String message = line.toString(StandardCharsets.UTF_8);
            line.reset();
            if (errorStream) {
                LOGGER.error("[stderr] {}", message);
            } else {
                LOGGER.info("[stdout] {}", message);
            }
        }
    }
}
