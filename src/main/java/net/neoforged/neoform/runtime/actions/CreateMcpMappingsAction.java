package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates SRG {@code <->} MCP mapping files from legacy MCP CSV mappings and the obfuscated {@code ->} SRG mapping.
 */
public class CreateMcpMappingsAction extends BuiltInAction {
    private final Path mcpMappingsPath;
    private final String obfToSrgDataId;

    public CreateMcpMappingsAction(Path mcpMappingsPath,
                                   String obfToSrgDataId) {
        this.mcpMappingsPath = mcpMappingsPath;
        this.obfToSrgDataId = obfToSrgDataId;
    }

    @Override
    public void run(ProcessingEnvironment environment) throws IOException {
        var csvMappings = McpCsvMappings.load(mcpMappingsPath);
        var obfToSrg = IMappingFile.load(environment.extractData(obfToSrgDataId).toFile());

        var builder = IMappingBuilder.create("srg", "mcp");
        for (var mappedClass : obfToSrg.getClasses()) {
            var className = mappedClass.getMapped();
            var classBuilder = builder.addClass(className, className);

            for (var mappedField : mappedClass.getFields()) {
                var srgName = mappedField.getMapped();
                classBuilder.field(srgName, csvMappings.fieldName(srgName));
            }

            for (var mappedMethod : mappedClass.getMethods()) {
                var srgName = mappedMethod.getMapped();
                var methodBuilder = classBuilder.method(
                        mappedMethod.getMappedDescriptor(),
                        srgName,
                        csvMappings.methodName(srgName)
                );
                // Attach MCP parameter names (TSRG2 only) so that bytecode remappers can rename LVT
                // entries, which in turn lets the decompiler emit named method parameters.
                var paramSlot = 0;
                for (var argumentSize : argumentSlots(mappedMethod.getMappedDescriptor())) {
                    var paramSrgName = csvMappings.parameterSrgName(srgName, paramSlot);
                    var paramName = csvMappings.parameterName(srgName, paramSlot);
                    if (paramSrgName != null && paramName != null) {
                        methodBuilder.parameter(paramSlot, paramSrgName, paramName);
                    }
                    paramSlot += argumentSize;
                }
            }
        }

        var srgToMcp = builder.build().getMap("srg", "mcp");
        srgToMcp.write(environment.getOutputPath("srgToMcp"), IMappingFile.Format.SRG, false);
        srgToMcp.write(environment.getOutputPath("srgToMcpTsrg"), IMappingFile.Format.TSRG, false);
        srgToMcp.write(environment.getOutputPath("srgToMcpTsrg2"), IMappingFile.Format.TSRG2, false);
        var mcpToSrg = srgToMcp.reverse();
        mcpToSrg.write(environment.getOutputPath("mcpToSrg"), IMappingFile.Format.SRG, false);
        mcpToSrg.write(environment.getOutputPath("mcpToSrgTsrg"), IMappingFile.Format.TSRG, false);
        obfToSrg.write(environment.getOutputPath("notchToSrg"), IMappingFile.Format.SRG, false);
        Files.copy(mcpMappingsPath, environment.getOutputPath("csvMappings"), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Returns the LVT slot sizes of the arguments of a method descriptor, e.g. {@code (JD)V -> [2, 2]}.
     * Arrays and object types occupy one slot; {@code long} and {@code double} occupy two.
     * Avoids a dependency on ASM just for parsing descriptors.
     */
    static List<Integer> argumentSlots(String descriptor) {
        var result = new ArrayList<Integer>();
        var i = descriptor.indexOf('(') + 1;
        while (descriptor.charAt(i) != ')') {
            var size = switch (descriptor.charAt(i)) {
                case 'J', 'D' -> 2;
                default -> 1;
            };
            while (descriptor.charAt(i) == '[') {
                i++;
            }
            if (descriptor.charAt(i) == 'L') {
                i = descriptor.indexOf(';', i) + 1;
            } else {
                i++;
            }
            result.add(size);
        }
        return result;
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder ck) {
        super.computeCacheKey(ck);
        ck.addPath("mcp mappings", mcpMappingsPath);
        ck.addDataSource("obf to srg mappings", obfToSrgDataId);
    }
}
