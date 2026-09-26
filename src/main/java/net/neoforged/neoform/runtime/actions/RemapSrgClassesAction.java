package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.utils.ToolCoordinate;
import net.neoforged.srgutils.IMappingFile;

import java.io.IOException;
import java.util.List;

public class RemapSrgClassesAction extends ExternalJavaToolAction {
    public RemapSrgClassesAction() {
        super(ToolCoordinate.AUTO_RENAMING_TOOL);
    }

    @Override
    public void run(ProcessingEnvironment environment) throws IOException, InterruptedException {
        var mappingsFile = environment.getInputPath("mappings");
        if (mappingsFile == null) {
            var srgToOfficial = RemapSrgSourcesAction.buildSrgToNamedMappings(environment);
            mappingsFile = environment.getWorkspace().resolve("mappings.tsrg2");
            srgToOfficial.write(mappingsFile, IMappingFile.Format.TSRG2, false);
        }

        setArgs(List.of(
                "--input", "{input}",
                "--output", "{output}",
                "--map", environment.getPathArgument(mappingsFile.toAbsolutePath()),
                // Legacy jars (e.g. Forge 1.12.2) are signed; renaming classes invalidates the signature,
                // which breaks any consumer that verifies digests (e.g. the decompiler reading the jar).
                "--strip-sigs"
        ));
        super.run(environment);
    }
}
