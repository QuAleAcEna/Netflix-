package com.mkyong;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * Media streaming utility
 *
 * @author Arul Dhesiaseelan (arul@httpmine.org)
 */
public class MediaStreamer implements StreamingOutput {

    private int length;
    private final RandomAccessFile raf;
    final byte[] buf = new byte[4096];

    public MediaStreamer(int length, RandomAccessFile raf) {
        this.length = length;
        this.raf = raf;
    }

    @Override
    public void write(OutputStream outputStream) {
        try {
            while (length != 0) {
                int read;
                try {
                    read = raf.read(buf, 0, buf.length > length ? length : buf.length);
                    outputStream.write(buf, 0, read);
                    length -= read;
                } catch (IOException ex) {
                    System.err.println("Error while reading from raf or writing to OutStream");
                }
            }
        } finally {
            try {
              if(raf != null)
                raf.close();
              if(outputStream != null)
                outputStream.close();
            } catch (IOException ex) {
                System.err.println("Error while closing raf");

            }
        }
    }

    public int getLenth() {
        return length;
    }
}
