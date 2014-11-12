/**
 *  Copyright 2005-2014 Red Hat, Inc.
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FilesHelper {
    private static final Logger log = Logger.getLogger(FilesHelper.class.getName());

    private static final FileVisitor<Path> deleteVisitor = new DeleteVisitor();

    private FilesHelper() {
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
