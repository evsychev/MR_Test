package org.example;

import org.example.logging.ConsoleLogRedirector;
import org.example.xr.OpenXrApplication;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        ConsoleLogRedirector.install();
        OpenXrApplication.run();
    }
}
