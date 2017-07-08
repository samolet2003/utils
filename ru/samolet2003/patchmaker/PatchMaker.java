package ru.samolet2003.patchmaker;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Websphere EAR patch maker
 */
public class PatchMaker {
    private static String DIFF_CMD;
    private static String SEVEN_ZIP_CMD;
    private static File oldArch;
    private static File newArch;
    private static PrintStream logStream;
    private static OutputStream logOutputStream;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalStateException("Usage: PatchMaker old.ear new.ear diff_cmd 7zip_cmd");
        }
        oldArch = new File(args[0]);
        newArch = new File(args[1]);
        DIFF_CMD = args[2];
        SEVEN_ZIP_CMD = args[3];

        // logStream = System.out;
        logOutputStream = new FileOutputStream("PatchMaker.log");
        logStream = new PrintStream(logOutputStream);
        try {
            makePatch();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            try {
                logStream.println(e.getMessage());
                e.printStackTrace(logStream);
            } catch (Exception ignore) {
            }
        } finally {
            IOUtils.closeQuietly(logStream);
        }
    }

    private static void makePatch() throws IOException, InterruptedException {
        if (!oldArch.isFile())
            throw new IllegalArgumentException("File not found: " + oldArch.getAbsolutePath());
        if (!newArch.isFile())
            throw new IllegalArgumentException("File not found: " + newArch.getAbsolutePath());
        File oldUnpacked = new File("old_unpacked");
        File newUnpacked = new File("new_unpacked");

        if (oldUnpacked.exists()) {
            println("deleting " + oldUnpacked.getAbsolutePath());
            FileUtils.deleteDirectory(oldUnpacked);
        }
        if (newUnpacked.exists()) {
            println("deleting " + newUnpacked.getAbsolutePath());
            FileUtils.deleteDirectory(newUnpacked);
        }
        unzip(oldArch, oldUnpacked);
        unzip(newArch, newUnpacked);

        unzipJarWars(oldUnpacked);
        unzipJarWars(newUnpacked);

        generateDelProps(oldUnpacked, newUnpacked, new ArrayList<File>(), true);
        deleteSameFilesInNew(oldUnpacked, newUnpacked);

        if (exec("jar", "cvfM", "patch.jar", "-C", newUnpacked.getAbsolutePath(), ".") != 0) {
            throw new IllegalStateException("exec failed");
        }

        println("FINISHED");
    }

    private static void print(String s) {
        logStream.print(s);
    }

    private static void println(String s) {
        logStream.println(s);
    }

    private static void generateDelProps(File oldDir, File newDir, List<File> toDelete, boolean top) throws IOException {
        if (!oldDir.exists()) {
            println("*** Old Directory doesn't exist: " + oldDir.getAbsolutePath());
            return;
        }
        if (!newDir.exists()) {
            println("*** New Directory doesn't exist: " + newDir.getAbsolutePath());
            return;
        }
        File[] files = oldDir.listFiles();
        if (files != null) {
            for (File oldFile : files) {
                File newFile = new File(newDir, oldFile.getName());
                if (oldFile.isDirectory() && newFile.isDirectory()) {
                    List<File> toDelete2 = toDelete;
                    if (isJarWar(newFile.getName())) {
                        toDelete2 = new ArrayList<File>();
                    }
                    generateDelProps(oldFile, newFile, toDelete2, false);
                } else if (oldFile.isFile() && !newFile.exists()) {
                    println("*** todelete: " + newFile.getAbsolutePath());
                    toDelete.add(newFile);
                }
            }
        }
        if ((top || isJarWar(newDir.getAbsolutePath())) && !toDelete.isEmpty()) {
            File metainf = new File(newDir, "META-INF");
            if (!metainf.exists()) {
                if (!metainf.mkdirs())
                    throw new IllegalStateException("Cannot create directory: " + metainf.getAbsolutePath());
            }
            File delprops = new File(metainf, "ibm-partialapp-delete.props");
            println("*** delprops: " + delprops.getAbsolutePath());
            FileOutputStream out = new FileOutputStream(delprops);
            try {
                PrintStream printStream = new PrintStream(out, false, "UTF-8");
                for (File delFile : toDelete) {
                    String relative = newDir.toURI().relativize(delFile.toURI()).getPath();
                    println("*** relative path: " + relative);
                    printStream.println(relative);
                }
                IOUtils.closeQuietly(printStream);
            } finally {
                IOUtils.closeQuietly(out);
            }
            toDelete.clear();
        }
    }

    private static void deleteSameFilesInNew(File oldDir, File newDir) throws IOException, InterruptedException {
        if (!oldDir.exists()) {
            println("*** Old Directory doesn't exist: " + oldDir.getAbsolutePath());
            return;
        }
        if (!newDir.exists()) {
            println("*** New Directory doesn't exist: " + newDir.getAbsolutePath());
            return;
        }
        File[] files = newDir.listFiles();
        if (files != null) {
            for (File newFile : files) {
                File oldFile = new File(oldDir, newFile.getName());
                if (!oldFile.exists()) {
                    println("*** Old file doesn't exist: " + oldFile.getAbsolutePath());
                    continue;
                }
                if (newFile.isFile() != oldFile.isFile()) {
                    throw new IllegalStateException("isFile " + oldFile.getAbsolutePath() + " != isFile "
                            + newFile.getAbsolutePath());
                }
                if (newFile.isFile()) {
                    int exitCode = exec(DIFF_CMD, "-u", "-w", oldFile.getAbsolutePath(), newFile.getAbsolutePath());
                    if (exitCode != 0) {
                        println("*** files differ: " + oldFile.getAbsolutePath() + " " + newFile.getAbsolutePath());
                    } else {
                        println("deleting " + newFile.getAbsolutePath());
                        if (!newFile.delete())
                            throw new IllegalStateException("Cannot delete file: " + newFile.getAbsolutePath());
                    }
                } else {
                    deleteSameFilesInNew(oldFile, newFile);
                }
            }
        }
        files = newDir.listFiles();
        if (files == null || files.length == 0) {
            println("deleting empty directory: " + newDir.getAbsolutePath());
            if (!newDir.delete()) {
                throw new IllegalStateException("Cannot delete directory: " + newDir.getAbsolutePath());
            }
        }
    }

    private static void unzipJarWars(File dir) throws IOException, InterruptedException {
        for (File jarwar : dir.listFiles(JarWarFilter.INSTANCE)) {
            File unpackdir = new File(jarwar.getParentFile(), jarwar.getName() + "_");
            unzip(jarwar, unpackdir);
            if (!jarwar.delete())
                throw new IllegalStateException("Cannot delete: " + jarwar.getAbsolutePath());
            unpackdir.renameTo(jarwar);
        }
    }

    private static void unzip(File arch, File dir) throws IOException, InterruptedException {
        int exitCode = exec(SEVEN_ZIP_CMD, "x", arch.getAbsolutePath(), "-o" + dir.getAbsolutePath());
        if (exitCode != 0)
            throw new IllegalStateException("exec failed");
    }

    private static int exec(String... args) throws IOException, InterruptedException {
        print("exec:");
        for (String s : args) {
            print(" ");
            print(s);
        }
        println("");
        Process process = Runtime.getRuntime().exec(args);
        Thread inreader = new Thread(new StreamHandlerThread(process.getInputStream(), logOutputStream));
        Thread erreader = new Thread(new StreamHandlerThread(process.getErrorStream(), logOutputStream));
        inreader.start();
        erreader.start();
        int exitCode = process.waitFor();
        println("exitCode=" + exitCode);
        inreader.join();
        erreader.join();
        return exitCode;
    }

    private static class StreamHandlerThread implements Runnable {
        InputStream in;
        OutputStream out;

        public StreamHandlerThread(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                IOUtils.copy(in, out);
            } catch (IOException e) {
                println(e.getMessage());
                e.printStackTrace(logStream);
            } finally {
                IOUtils.closeQuietly(in);
            }
        }
    }

    private static class JarWarFilter implements FilenameFilter {
        static final JarWarFilter INSTANCE = new JarWarFilter();

        @Override
        public boolean accept(File dir, String name) {
            return isJarWar(name);
        }
    }

    private static final boolean isJarWar(String filename) {
        return filename.toLowerCase().endsWith(".jar") || filename.toLowerCase().endsWith(".war");
    }
}
