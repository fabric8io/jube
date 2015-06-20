/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.jube.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FilesHelper {
    private static final Logger log = Logger.getLogger(FilesHelper.class.getName());

    private static final FileVisitor<Path> deleteVisitor = new DeleteVisitor();
    private static boolean windowsOs = initWindowsOs();

    private FilesHelper() {
        // noop
    }

    private static boolean initWindowsOs() {
        // initialize once as System.getProperty is not fast
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.contains("windows");
    }

    /**
     * Returns true, if the OS is windows
     */
    public static boolean isWindows() {
        return windowsOs;
    }

    public static boolean renameTo(File src, File dest) {
        try {
            Files.move(src.toPath(), dest.toPath());
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("Cannot move %s to %s", src, dest), e);
            return false;
        }
    }

    public static void recursiveDelete(File file) {
        if (!file.exists()) {
            return;
        }

        try {
            Files.walkFileTree(file.toPath(), deleteVisitor);
        } catch (IOException e) {
            log.log(Level.WARNING, String.format("Failed to walk " + file), e);
        }
    }

    public static List<String> readLines(File file) throws IOException {
        return Files.readAllLines(file.toPath(), Charset.defaultCharset());
    }

    /**
     * Normalizes the path to cater for Windows and other platforms
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        if (isWindows()) {
            // special handling for Windows where we need to convert / to \\
            return path.replace('/', '\\');
        } else {
            // for other systems make sure we use / as separators
            return path.replace('\\', '/');
        }
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses OS specific file separators (eg {@link java.io.File#separator}).
     */
    public static String compactPath(String path) {
        return compactPath(path, File.separatorChar);
    }

    /**
     * Compacts a path by stacking it and reducing <tt>..</tt>,
     * and uses the given separator.
     */
    public static String compactPath(String path, char separator) {
        if (path == null) {
            return null;
        }

        // only normalize if contains a path separator
        if (path.indexOf('/') == -1 && path.indexOf('\\') == -1)  {
            return path;
        }

        // need to normalize path before compacting
        path = normalizePath(path);

        // preserve ending slash if given in input path
        boolean endsWithSlash = path.endsWith("/") || path.endsWith("\\");

        // preserve starting slash if given in input path
        boolean startsWithSlash = path.startsWith("/") || path.startsWith("\\");

        Stack<String> stack = new Stack<String>();

        // separator can either be windows or unix style
        String separatorRegex = "\\\\|/";
        String[] parts = path.split(separatorRegex);
        for (String part : parts) {
            if (part.equals("..") && !stack.isEmpty() && !"..".equals(stack.peek())) {
                // only pop if there is a previous path, which is not a ".." path either
                stack.pop();
            } else if (part.equals(".") || part.isEmpty()) {
                // do nothing because we don't want a path like foo/./bar or foo//bar
            } else {
                stack.push(part);
            }
        }

        // build path based on stack
        StringBuilder sb = new StringBuilder();

        if (startsWithSlash) {
            sb.append(separator);
        }

        for (Iterator<String> it = stack.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(separator);
            }
        }

        if (endsWithSlash && stack.size() > 0) {
            sb.append(separator);
        }

        return sb.toString();
    }

    private static class DeleteVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            FileVisitResult result = super.visitFile(file, attrs);
            Files.delete(file);
            return result;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            FileVisitResult result = super.postVisitDirectory(dir, exc);
            Files.delete(dir);
            return result;
        }
    }
}
