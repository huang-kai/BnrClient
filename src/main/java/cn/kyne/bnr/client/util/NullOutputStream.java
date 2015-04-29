package cn.kyne.bnr.client.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Singleton OutputStream that discards everything written to it.
 */
public class NullOutputStream extends OutputStream {
    
    private static NullOutputStream INSTANCE = new NullOutputStream();

    /**
     * Private constructor to prevent instantiation.
     */
    private NullOutputStream() {
    }
    
    public static NullOutputStream getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(int b) throws IOException {
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
    }

    @Override
    public void write(byte[] b) throws IOException {
    }

}
