package fr.minemobs.mcpserver;

import net.md_5.specialsource.Jar;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class JarRemapper {

    private final File mappings;

    public JarRemapper(File mappingsIn) {
        this.mappings = mappingsIn;
    }

    public void remap(File in, File out) throws IOException {
        System.out.println("Remapping jar");
        FileUtils.copyFile(in, out);
        MCPServer.createJarRemapper(MCPServer.createJarMapping(mappings)).remapJar(Jar.init(in), out);
    }

}
