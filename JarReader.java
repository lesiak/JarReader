package com.lesiak.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reading files from jar file URL, taking care of nested jar
 * <ul>
 * <li>jar:file:/C:/Temp/single.jar!/foo</li>
 * <li>jar:file:/C:/Temp/outer.jar!/lib/inner.jar!/foo</li>
 * </ul>
 */
public class JarReader {

    private static final Logger logger =
            LoggerFactory.getLogger(JarReader.class);

    public static void read(URL jarUrl, InputStreamCallback callback) throws IOException {
        if (!"jar".equals(jarUrl.getProtocol())) {
            throw new IllegalArgumentException("Jar protocol is expected but get " + jarUrl.getProtocol());
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        String jarPath = jarUrl.getPath().substring(5);
        String[] paths = jarPath.split("!");
        FileInputStream jarFileInputStream = new FileInputStream(paths[0]);
        readStream(jarFileInputStream, paths[0], 1, paths, callback);
    }

    private static void readStream(InputStream jarFileInputStream, String name, int i, String[] paths, InputStreamCallback callback) throws IOException {
        if (i == paths.length) {
            callback.onFile(name, jarFileInputStream);
            return;
        }
        ZipInputStream jarInputStream = new ZipInputStream(jarFileInputStream);
        try {
            ZipEntry jarEntry = null;
            while ((jarEntry = jarInputStream.getNextEntry()) != null) {
                String jarEntryName = "/" + jarEntry.getName();
                if (!jarEntry.isDirectory() && jarEntryName.startsWith(paths[i])) {
                    byte[] byteArray = copyStream(jarInputStream, jarEntry);
                    logger.debug("Entry {} with size {} and data size {}", jarEntryName, jarEntry.getSize(), byteArray.length);
                    readStream(new ByteArrayInputStream(byteArray), jarEntryName, i + 1, paths, callback);
                }
            }
        } finally {
            jarInputStream.close();
        }
    }

    private static byte[] copyStream(InputStream in, ZipEntry entry)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long size = entry.getSize();
        if (size > -1) {
            byte[] buffer = new byte[1024 * 4];
            int n = 0;
            long count = 0;
            while (-1 != (n = in.read(buffer)) && count < size) {
                baos.write(buffer, 0, n);
                count += n;
            }
        } else {
            while (true) {
                int b = in.read();
                if (b == -1) {
                    break;
                }
                baos.write(b);
            }
        }
        baos.close();
        return baos.toByteArray();
    }

    public static interface InputStreamCallback {
        void onFile(String name, InputStream is) throws IOException;
    }
}