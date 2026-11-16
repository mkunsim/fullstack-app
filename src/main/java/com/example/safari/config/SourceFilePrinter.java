package com.example.safari.config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SourceFilePrinter {

    public static void main(String[] args) {

        Path projectPath = Path.of(".").toAbsolutePath().normalize();
        Path outputFile = projectPath.getParent()
                .resolve("source-files.txt");

        System.out.println("\n====================================");
        System.out.println(" EXPORTING TSX AND JAVA FILES");
        System.out.println("====================================");

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
             Stream<Path> files = Files.walk(projectPath)) {

            files.filter(Files::isRegularFile)
                 .filter(file -> {
                     String name = file.getFileName().toString();
                     return name.endsWith(".tsx") || name.endsWith(".java");
                 })
                 .filter(file -> !file.equals(outputFile))
                 .filter(file -> !file.getFileName()
                         .toString().equals("SourceFilePrinter.java"))
                 .filter(file -> !file.startsWith(projectPath.resolve("target")))
                 .sorted()
                 .forEach(file -> {

                     try {
                         writer.write("\n====================================\n");
                         writer.write("FILE: " + file + "\n");
                         writer.write("====================================\n\n");

                         writer.write(Files.readString(file));
                         writer.write("\n\n");

                     } catch (IOException e) {
                         System.err.println(
                                 "Could not read " + file + ": " + e.getMessage());
                     }
                 });

            writer.write("\n========== EXPORT COMPLETE ==========\n");

            System.out.println("Export completed successfully!");

        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
        }

        System.out.println("Output file: " + outputFile);
    }
}
