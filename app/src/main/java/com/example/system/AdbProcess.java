package com.example.system;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class AdbProcess extends Process {

    private final AdbShellStream stream;

    public AdbProcess(AdbShellStream stream) {
        this.stream = stream;
    }

    @Override
    public OutputStream getOutputStream() {
        return stream.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return stream.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return stream.getInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return stream.awaitExit();
    }

    @Override
    public int exitValue() {
        return stream.exitCode();
    }

    @Override
    public void destroy() {
        stream.close();
    }

    @Override
    public boolean isAlive() {
        return !stream.isClosed();
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return stream.awaitExit(timeout, unit) >= 0;
    }
}
