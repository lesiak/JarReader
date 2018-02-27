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

    public static void read(URL dirUrl, JarEntryNameAndStreamCallback callback) throws IOException {
        if (!"jar".equals(dirUrl.getProtocol())) {
            throw new IllegalArgumentException("Jar protocol is expected but get " + dirUrl.getProtocol());
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        String[] pathSegments = dirUrl.getPath().split("!");
        URL jarUrl = new URL(pathSegments[0]);
        try (InputStream jarFileInputStream = jarUrl.openStream()) {
            readStream(jarFileInputStream, 1, pathSegments, callback);
        }
    }

    public static void read(URL dirUrl, JarEntryNameCallback callback) throws IOException {
        if (!"jar".equals(dirUrl.getProtocol())) {
            throw new IllegalArgumentException("Jar protocol is expected but get " + dirUrl.getProtocol());
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        String[] pathSegments = dirUrl.getPath().split("!");
        URL jarUrl = new URL(pathSegments[0]);
        try (InputStream jarFileInputStream = jarUrl.openStream()) {
            readStream(jarFileInputStream, 1, pathSegments, callback);
        }
    }

    private static void readStream(InputStream jarFileInputStream, int pathSegmentToOpen, String[] pathSegments, JarEntryNameCallback callback) throws IOException {
        readStream(jarFileInputStream, pathSegmentToOpen, pathSegments, (JarEntryHandler) (jarInputStream, jarEntry) -> callback.onFile(jarEntry.getName()));
    }


    private static void readStream(InputStream jarFileInputStream, int pathSegmentToOpen, String[] pathSegments, JarEntryNameAndStreamCallback callback) throws IOException {
        readStream(jarFileInputStream, pathSegmentToOpen, pathSegments, (JarEntryHandler) (jarInputStream, jarEntry) -> {
            InputStream jarEntryStream = ByteStreams.limit(jarInputStream, jarEntry.getSize());
            callback.onFile(jarEntry.getName(), jarEntryStream);
        });
    }

    private static void readStream(InputStream jarFileInputStream, int pathSegmentToOpen, String[] pathSegments, JarEntryHandler entryHandler) throws IOException {
        boolean isInsideInnermostJar = pathSegmentToOpen == pathSegments.length - 1;
        String pathSegmentWithoutLeadingSlash = pathSegments[pathSegmentToOpen].substring(1);
        ZipInputStream jarInputStream = new ZipInputStream(jarFileInputStream);
        ZipEntry jarEntry = null;
        while ((jarEntry = jarInputStream.getNextEntry()) != null) {
            if (!jarEntry.isDirectory() && jarEntry.getName().startsWith(pathSegmentWithoutLeadingSlash)) {
                logger.debug("Entry {} with size {} and data size {}", jarEntry.getName(), jarEntry.getSize(), jarEntry.getSize());
                if (isInsideInnermostJar) {
                    entryHandler.onJarEntry(jarInputStream, jarEntry);
                } else {
                    InputStream jarEntryStream = ByteStreams.limit(jarInputStream, jarEntry.getSize());
                    readStream(jarEntryStream, pathSegmentToOpen + 1, pathSegments, entryHandler);
                }
            }
        }
    }

    @FunctionalInterface
    public interface JarEntryHandler {
        void onJarEntry(ZipInputStream jarInputStream, ZipEntry jarEntry) throws IOException;
    }

    @FunctionalInterface
    public interface JarEntryNameCallback {
        void onFile(String name) throws IOException;
    }

    @FunctionalInterface
    public interface JarEntryNameAndStreamCallback {
        void onFile(String name, InputStream is) throws IOException;
    }
}