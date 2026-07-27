package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateMcpMappingsActionTest {
    @TempDir
    Path tempDir;

    @Test
    void writesMcpMappingsAsSrgAndHeaderlessTsrgV1() throws IOException {
        var obfToSrgPath = tempDir.resolve("notch-to-srg.srg");
        var obfToSrgBuilder = IMappingBuilder.create("notch", "srg");
        var classMapping = obfToSrgBuilder.addClass("a", "net/minecraft/Test");
        classMapping.field("b", "field_1_value");
        classMapping.method("(I)V", "c", "func_2_run");
        obfToSrgBuilder.build().getMap("notch", "srg")
                .write(obfToSrgPath, IMappingFile.Format.SRG, false);

        var mcpMappingsPath = tempDir.resolve("mcp.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(mcpMappingsPath))) {
            writeZipEntry(output, "fields.csv", "searge,name,side,desc\nfield_1_value,value,0,\n");
            writeZipEntry(output, "methods.csv", "searge,name,side,desc\nfunc_2_run,runThing,0,\n");
        }

        var srgOutput = tempDir.resolve("srg-to-mcp.srg");
        var tsrgOutput = tempDir.resolve("srg-to-mcp.tsrg");
        var mcpToSrgOutput = tempDir.resolve("mcp-to-srg.srg");
        var mcpToSrgTsrgOutput = tempDir.resolve("mcp-to-srg.tsrg");
        var notchToSrgOutput = tempDir.resolve("notch-to-srg-output.srg");
        var csvOutput = tempDir.resolve("csv-output.zip");

        var environment = mock(ProcessingEnvironment.class);
        when(environment.extractData("mappings")).thenReturn(obfToSrgPath);
        when(environment.getOutputPath("srgToMcp")).thenReturn(srgOutput);
        when(environment.getOutputPath("srgToMcpTsrg")).thenReturn(tsrgOutput);
        when(environment.getOutputPath("mcpToSrg")).thenReturn(mcpToSrgOutput);
        when(environment.getOutputPath("mcpToSrgTsrg")).thenReturn(mcpToSrgTsrgOutput);
        when(environment.getOutputPath("notchToSrg")).thenReturn(notchToSrgOutput);
        when(environment.getOutputPath("csvMappings")).thenReturn(csvOutput);

        new CreateMcpMappingsAction(mcpMappingsPath, "mappings", () -> null).run(environment);

        assertThat(Files.readAllLines(srgOutput)).contains(
                "FD: net/minecraft/Test/field_1_value net/minecraft/Test/value",
                "MD: net/minecraft/Test/func_2_run (I)V net/minecraft/Test/runThing (I)V"
        );
        assertThat(Files.readAllLines(tsrgOutput)).containsExactly(
                "net/minecraft/Test net/minecraft/Test",
                "\tfield_1_value value",
                "\tfunc_2_run (I)V runThing"
        );
        assertThat(Files.readAllLines(mcpToSrgOutput)).contains(
                "FD: net/minecraft/Test/value net/minecraft/Test/field_1_value",
                "MD: net/minecraft/Test/runThing (I)V net/minecraft/Test/func_2_run (I)V"
        );
        assertThat(Files.readAllLines(mcpToSrgTsrgOutput)).containsExactly(
                "net/minecraft/Test net/minecraft/Test",
                "\tvalue field_1_value",
                "\trunThing (I)V func_2_run"
        );
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
