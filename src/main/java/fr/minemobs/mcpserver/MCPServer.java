package fr.minemobs.mcpserver;

import net.md_5.specialsource.JarMapping;
import net.thesimpleteam.colors.Colors;
import org.apache.commons.cli.*;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MCPServer {

    public static void main(String[] args) {
        //TODO Support for old server jar format
        Options options = new Options();
        options.addOption(Option.builder("f").longOpt("file").required().hasArg(true).desc("The server jar file").build());
        options.addOption("m", "mappings", true, "The mappings file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args, true);
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }
        //Print current directory
        File file = new File(cmd.getOptionValue("f"));
        File mappings = new File(cmd.getOptionValue("m"));
        if (!file.exists()) {
            System.out.println(colorize("⚠", Color.RED) + "Server jar not found");
            return;
        }
        if (!file.getName().endsWith(".jar")) {
            System.out.println(colorize("⚠", Color.RED) + "File is not a jar");
        }
        if (!mappings.exists()) {
            System.out.println(colorize("⚠", Color.RED) + "Mappings file not found");
        }
        try (ZipFile zipFile = new ZipFile(file)) {
            Stream<ZipEntry> entries = StreamSupport.stream(Spliterators.spliteratorUnknownSize(zipFile.entries().asIterator(), Spliterator.ORDERED), false);
            List<ZipEntry> jars = entries.filter(entry -> entry.getName().endsWith(".jar")).toList();
            jars.forEach(entry -> System.out.println(colorize("Found jar: ", Color.GREEN) + getEntryName(entry)));
            Optional<ZipEntry> entry = jars.stream().filter(e -> getEntryName(e).startsWith("server")).findFirst();
            if (entry.isEmpty()) {
                System.out.println(colorize("⚠", Color.RED) + "No server jar found !");
                return;
            }
            File unMappedFile = File.createTempFile("unmapped-server-" + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()), ".jar");
            Files.copy(zipFile.getInputStream(entry.get()), unMappedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File mappedFile = new File(getEntryName(entry.get()));
            File libs = new File("libs");
            libs.mkdir();
            jars.stream().filter(e -> !getEntryName(e).startsWith("server")).forEach(e -> {
                try {
                    File f = new File(libs, getEntryName(e));
                    if (f.exists()) return;
                    Files.copy(zipFile.getInputStream(e), f.toPath());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            File output = new File("output");
            File main = new File(output, "src/main");
            main.mkdirs();
            decompile(output, unMappedFile, libs);
            if (!mappedFile.exists()) {
                new JarRemapper(mappings).remap(unMappedFile, mappedFile);
                System.out.println(colorize("✔", Color.GREEN) + "Server jar remapped");
            }
            System.out.println(colorize("⏲️", Color.YELLOW) + "Extracting...");
            extract(output, unMappedFile, main);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void extract(File output, File mappedFile, File main) throws IOException {
        try(ZipFile compressedJar = new ZipFile(new File(output, mappedFile.getName()), StandardCharsets.UTF_8)) {
            Stream<ZipEntry> decompiledEntries = StreamSupport.stream(Spliterators.spliteratorUnknownSize(compressedJar.entries().asIterator(), Spliterator.ORDERED), false);
            decompiledEntries.forEach(zipEntry -> {
                if (zipEntry.getName().endsWith(".java")) {
                    try {
                        File f = new File(main, "java/" + zipEntry.getName());
                        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                        Files.copy(compressedJar.getInputStream(zipEntry), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        File f = new File(main, "resources/" + zipEntry.getName());
                        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                        Files.copy(compressedJar.getInputStream(zipEntry), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        System.out.println(colorize("✔", Color.GREEN) + "Extracted");
    }

    private static void decompile(File output, File mappedFile, File libs) {
        if (Files.notExists(Path.of(output.getAbsolutePath(), mappedFile.getName()))) {
            System.out.println(colorize("⏲️", Color.YELLOW) + "Decompiling...");
            ConsoleDecompiler decompiler = new ConsoleDecompiler(output);
            decompiler.addSource(mappedFile);
            Arrays.stream(libs.listFiles()).filter(Objects::nonNull).filter(File::isFile).filter(f -> f.getName().endsWith(".class")).forEach(decompiler::addLibrary);
            decompiler.decompileContext();
            System.out.println(colorize("✔", Color.GREEN) + "Decompiled");
        }
    }

    public static String colorize(String str, Color color) {
        return Colors.getForegroundColorFromRGB(color) + str + Colors.RESET;
    }

    private static String getEntryName(ZipEntry entry) {
        String[] path = entry.getName().split("/");
        return path[path.length - 1];
    }

    public static net.md_5.specialsource.JarRemapper createJarRemapper(JarMapping mapping) {
        return new net.md_5.specialsource.JarRemapper(null, mapping, null);
    }

    public static JarMapping createJarMapping(File mappings) {
        JarMapping mapping = new JarMapping();
        try {
            mapping.loadMappings(mappings);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapping;
    }

    private static class ConsoleDecompiler extends org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler {

        public ConsoleDecompiler(File destination) {
            super(destination, Map.of(), new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {
                    this.writeMessage(message, severity, null);
                }

                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {
                    Color textColor = switch (severity) {
                        case ERROR -> Color.RED;
                        case TRACE -> Color.CYAN;
                        case INFO -> Color.GREEN;
                        case WARN -> Color.YELLOW;
                    };
                    System.out.println(colorize(message, textColor));
                }
            });
        }
    }
}
