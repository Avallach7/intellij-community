/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.NOPLogger;
import org.apache.log4j.spi.NOPLoggerRepository;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Runner {
  private static final String PATCH_FILE_NAME = "patch-file.zip";

  private static Logger logger = null;
  private static String logPath = null;
  private static boolean ourCaseSensitiveFs;

  public static Logger logger() {
    return logger;
  }

  public static boolean isCaseSensitiveFs() {
    return ourCaseSensitiveFs;
  }

  public static void main(String[] args) {
    String jarFile = getArgument(args, "jar");
    if (jarFile == null) {
      jarFile = resolveJarFile();
    }

    if (args.length >= 6 && "create".equals(args[0])) {
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];

      checkCaseSensitivity(newFolder);
      initLogger();

      // See usage for an explanation of these flags
      boolean binary = Arrays.asList(args).contains("--zip_as_binary");
      boolean strict = Arrays.asList(args).contains("--strict");
      boolean normalized = Arrays.asList(args).contains("--normalized");

      String root = getArgument(args, "root");
      if (root == null) {
        root = "";
      }
      else if (!root.endsWith("/")) {
        root += "/";
      }

      List<String> ignoredFiles = extractArguments(args, "ignored");
      List<String> criticalFiles = extractArguments(args, "critical");
      List<String> optionalFiles = extractArguments(args, "optional");
      List<String> deleteFiles = extractArguments(args, "delete");
      Map<String, String> warnings = buildWarningMap(extractArguments(args, "warning"));

      PatchSpec spec = new PatchSpec()
        .setOldVersionDescription(oldVersionDesc)
        .setNewVersionDescription(newVersionDesc)
        .setRoot(root)
        .setOldFolder(oldFolder)
        .setNewFolder(newFolder)
        .setPatchFile(patchFile)
        .setJarFile(jarFile)
        .setStrict(strict)
        .setBinary(binary)
        .setNormalized(normalized)
        .setIgnoredFiles(ignoredFiles)
        .setCriticalFiles(criticalFiles)
        .setOptionalFiles(optionalFiles)
        .setDeleteFiles(deleteFiles)
        .setWarnings(warnings);

      boolean success = create(spec);
      System.exit(success ? 0 : 1);
    }
    else if (args.length >= 2 && ("install".equals(args[0]) || "apply".equals(args[0]))) {
      String destFolder = args[1];
      checkCaseSensitivity(destFolder);

      initLogger();
      logger().info("destFolder: " + destFolder + ", case-sensitive: " + ourCaseSensitiveFs);

      UpdaterUI ui;
      if ("install".equals(args[0])) {
        ui = new SwingUpdaterUI();
      }
      else if (Arrays.asList(args).contains("--toolbox-ui")) {
        ui = new ToolboxUpdaterUI();
      }
      else {
        ui = new ConsoleUpdaterUI();
      }

      boolean backup = !Arrays.asList(args).contains("--no-backup");
      boolean success = install(jarFile, destFolder, ui, backup);
      System.exit(success ? 0 : 1);
    }
    else {
      printUsage();
    }
  }

  public static void checkCaseSensitivity(String path) {
    boolean orig = new File(path).exists();
    ourCaseSensitiveFs = orig != new File(path.toUpperCase()).exists() || orig != new File(path.toLowerCase()).exists();
  }

  private static Map<String, String> buildWarningMap(List<String> warnings) {
    Map<String, String> map = new HashMap<>();
    for (String warning : warnings) {
      int ix = warning.indexOf(":");
      if (ix != -1) {
        String path = warning.substring(0, ix);
        String message = warning.substring(ix + 1).replace("\\n","\n");
        map.put(path, message);
      }
    }
    return map;
  }

  // checks that log directory 1)exists 2)has write perm. and 3)has 1MB+ free space
  private static boolean isValidDir(String folder, long space) {
    File fileDir = new File(folder);
    return fileDir.isDirectory() && fileDir.canWrite() && fileDir.getUsableSpace() >= space;
  }

  public static String getDir(long requiredFreeSpace) {
    String dir = System.getProperty("idea.updater.log");
    if (dir == null || !isValidDir(dir, requiredFreeSpace)) {
      dir = System.getProperty("java.io.tmpdir");
      if (!isValidDir(dir, requiredFreeSpace)) {
        dir = System.getProperty("user.home");
      }
    }
    return dir;
  }

  private static void initLogger() {
    if (logger == null) {
      String logDirectory = getDir(1_000_000L);
      logPath = new File(logDirectory, "idea_updater.log").getAbsolutePath();

      FileAppender update = new FileAppender();
      update.setFile(logPath);
      update.setLayout(new PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      update.setThreshold(Level.ALL);
      update.setAppend(true);
      update.activateOptions();

      FileAppender updateError = new FileAppender();
      updateError.setFile(new File(logDirectory, "idea_updater_error.log").getAbsolutePath());
      updateError.setLayout(new PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      updateError.setThreshold(Level.ERROR);
      updateError.setAppend(false);
      updateError.activateOptions();

      logger = Logger.getLogger("com.intellij.updater");
      logger.addAppender(updateError);
      logger.addAppender(update);
      logger.setLevel(Level.ALL);
      logger.info("--- Updater started ---");
    }
  }

  public static String getArgument(String[] args, String name) {
    String flag = "--" + name + "=";
    for (String param : args) {
      if (param.startsWith(flag)) {
        return param.substring(flag.length());
      }
    }
    return null;
  }

  public static List<String> extractArguments(String[] args, String paramName) {
    List<String> result = new ArrayList<>();
    String prefix = paramName + '=';
    for (String param : args) {
      if (param.startsWith(prefix)) {
        StringTokenizer tokenizer = new StringTokenizer(param.substring(prefix.length()), ";");
        while (tokenizer.hasMoreTokens()) {
          result.add(tokenizer.nextToken());
        }
      }
    }
    return result;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void printUsage() {
    System.err.println(
      "Usage:\n" +
      "  Runner create <old_version> <new_version> <old_folder> <new_folder> <patch_file> [<file_set>=file1;file2;...] [<flags>]\n" +
      "  Runner install <folder>\n" +
      "\n" +
      "Where:\n" +
      "  <old_version>: A description of the version to generate the patch from.\n" +
      "  <new_version>: A description of the version to generate the patch to.\n" +
      "  <old_folder>: The folder where to find the old version.\n" +
      "  <new_folder>: The folder where to find the new version.\n" +
      "  <patch_file>: The .jar patch file to create which contains the patch and the patcher.\n" +
      "  <file_set>: Can be one of:\n" +
      "    ignored: The set of files that will not be included in the patch.\n" +
      "    critical: Fully included in the patch, so they can be replaced at destination even if they have changed.\n" +
      "    optional: A set of files that is ok for them no to exist when applying the patch.\n" +
      "    delete: A set of regular expressions for paths that is safe to delete without user confirmation.\n" +
      "  <flags>: Can be:\n" +
      "    --zip_as_binary: Zip and jar files will be treated as binary files and not inspected internally.\n" +
      "    --strict: The created patch will contain extra information to fully validate an installation. A strict\n" +
      "              patch will only be applied if it is guaranteed that the patched version will match exactly\n" +
      "              the source of the patch. This means that unexpected files will be deleted and all existing files\n" +
      "              will be validated\n" +
      "    --root=<dir>: Sets dir as the root directory of the patch. The root directory is the directory where the patch should be" +
      "                  applied to. For example on Mac, you can diff the two .app folders and set Contents as the root." +
      "                  The root directory is relative to <old_folder> and uses forwards-slashes as separators." +
      "    --normalized: This creates a normalized patch. This flag only makes sense in addition to --zip_as_binary\n" +
      "                  A normalized patch must be used to move from an installation that was patched\n" +
      "                  in a non-binary way to a fully binary patch. This will yield a larger patch, but the\n" +
      "                  generated patch can be applied on versions where non-binary patches have been applied to and it\n" +
      "                  guarantees that the patched version will match exactly the original one.\n");
  }

  private static boolean create(PatchSpec spec) {
    ConsoleUpdaterUI ui = new ConsoleUpdaterUI();
    boolean success = false;

    try {
      File tempPatchFile = Utils.getTempFile("patch");
      PatchFileCreator.create(spec, tempPatchFile, ui);

      logger().info("Packing JAR file: " + spec.getPatchFile() );
      ui.startProcess("Packing JAR file '" + spec.getPatchFile() + "'...");

      try (ZipOutputWrapper out = new ZipOutputWrapper(new FileOutputStream(spec.getPatchFile()));
           ZipInputStream in = new ZipInputStream(new FileInputStream(new File(spec.getJarFile())))) {
        ZipEntry e;
        while ((e = in.getNextEntry()) != null) {
          out.zipEntry(e, in);
        }

        out.zipFile(PATCH_FILE_NAME, tempPatchFile);
        out.finish();
      }

      success = true;
    }
    catch (Throwable t) {
      logger().error("create failed", t);
      ui.showError(printStackTrace(t));
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (Throwable t) {
        success = false;
        logger().error("cleanup failed", t);
        ui.showError(printStackTrace(t));
      }
    }

    return success;
  }

  private static String printStackTrace(Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    logger().info("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static boolean install(String jarFile, String destPath, UpdaterUI ui, boolean backup) {
    try {
      PatchFileCreator.PreparationResult preparationResult;
      Map<String, ValidationResult.Option> resolutions;
      File backupDir;
      PatchFileCreator.ApplicationResult applicationResult;

      try {
        File patchFile = Utils.getTempFile("patch");

        logger().info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try (ZipFile zipFile = new ZipFile(jarFile);
             InputStream in = Utils.getEntryInputStream(zipFile, PATCH_FILE_NAME);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile))) {
          Utils.copyStream(in, out);
        }

        ui.checkCancelled();

        File destDir = new File(destPath);
        preparationResult = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);

        List<ValidationResult> problems = preparationResult.validationResults;
        resolutions = problems.isEmpty() ? Collections.emptyMap() : ui.askUser(problems);

        backupDir = backup ? Utils.createTempDir() : null;
        applicationResult = PatchFileCreator.apply(preparationResult, resolutions, backupDir, ui);
      }
      catch (OperationCancelledException e) {
        logger().warn("cancelled", e);
        return false;
      }
      catch (IOException | RuntimeException | Error t) {
        logger().error("prepare failed", t);
        String message = "An unexpected error occurred when preparing the patch\n" + t.getMessage() + "\n\n" +
                         "No files were changed; retry applying the patch.\n\n" +
                         "More details in the log: " + logPath;
        ui.showError(message);
        return false;
      }

      if (!applicationResult.applied) {
        List<PatchAction> appliedActions = applicationResult.appliedActions;
        Throwable error = applicationResult.error;

        if (error != null) {
          String message = "An unexpected error occurred when applying the patch:\n" + error.getMessage() + "\n\n";
          if (appliedActions.isEmpty()) {
            message += "No files were changed; retry applying the patch.\n\n";
          }
          else if (backupDir == null) {
            message += "IDE files may be corrupted, no backup was requested, please reinstall.\n\n";
          }
          else {
            message += "IDE files may be corrupted, the patch will attempt to revert the changes.\n\n";
          }
          message += "More details in the log: " + logPath;
          ui.showError(message);
        }

        if (!appliedActions.isEmpty() && backupDir != null) {
          try {
            PatchFileCreator.revert(preparationResult, appliedActions, backupDir, ui);
          }
          catch (Throwable t) {
            logger().error("revert failed", t);
            String message = "An unexpected error occurred when reverting the patch:\n" + t.getMessage() + "\n\n" +
                             "IDE files may be corrupted, please reinstall.\n\n" +
                             "More details in the log: " + logPath;
            ui.showError(message);
          }
        }
      }

      return applicationResult.applied;
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (Throwable t) {
        logger().warn("cleanup failed", t);
      }
    }
  }

  private static String resolveJarFile() {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IllegalArgumentException("Cannot resolve JAR file path");
    if (!"jar".equals(url.getProtocol())) throw new IllegalArgumentException("Patch file is not a JAR file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IllegalArgumentException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl)).getAbsolutePath();
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static void initTestLogger() {
    if (logger == null) {
      logger = new NOPLogger(new NOPLoggerRepository(), "root");
    }
    else if (!(logger instanceof NOPLogger)) {
      throw new IllegalStateException("Non-test logger already defined");
    }
  }
}