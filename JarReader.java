package com.lesiak.utils;

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    private static void readStream(InputStream jarFileInputStream, String name, int pathSegmentToOpen, String[] paths, InputStreamCallback callback) throws IOException {
        if (pathSegmentToOpen == paths.length) {
            callback.onFile(name, jarFileInputStream);
            return;
        }
        ZipInputStream jarInputStream = new ZipInputStream(jarFileInputStream);
        try {
            ZipEntry jarEntry = null;
            while ((jarEntry = jarInputStream.getNextEntry()) != null) {
                String jarEntryName = "/" + jarEntry.getName();
                if (!jarEntry.isDirectory() && jarEntryName.startsWith(paths[pathSegmentToOpen])) {
                    InputStream jarEntryStream = ByteStreams.limit(jarInputStream, jarEntry.getSize());
                    logger.debug("Entry {} with size {} and data size {}", jarEntryName, jarEntry.getSize(), jarEntry.getSize());
                    readStream(jarEntryStream, jarEntryName, pathSegmentToOpen + 1, paths, callback);
                }
            }
        } finally {
            jarInputStream.close();
        }
    }

    public static interface InputStreamCallback {
        void onFile(String name, InputStream is) throws IOException;
    }
}