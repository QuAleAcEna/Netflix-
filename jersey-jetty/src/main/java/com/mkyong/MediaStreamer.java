package com.mkyong;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.EOFException;
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
                int read = 0;
                try {
                    read = raf.read(buf, 0, buf.length > length ? length : buf.length);
                    if(read == -1) break;
                    length -= read;
                } catch (IOException ex) {
                    try {
                        System.err.printf("Error while reading from raf:FileSize = %d, read = %d, length = %d, error = "+ex.toString()+'\n',raf.length(),read,length);
                    } catch (IOException ex1) {
                    }
                }
                try {
                    outputStream.write(buf, 0, read);
                }
                catch (EOFException ex) {
                }
                 catch (IOException ex) {
                        System.err.printf("Error while writing to OutStream:read = %d, length = %d, error = "+ex.toString()+'\n',read,length);

                }

            }
        } finally {
            try {
              raf.close();
            } catch (IOException ex) {
                System.err.println("Error while closing raf/output stream");

            }
        }
    }

    public int getLength() {
        return length;
    }
}
