package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.cache.CacheKey;
import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Creates SRG <-> MCP mapping files from legacy MCP CSV mappings and the obfuscated -> SRG mapping.
 */
public class CreateMcpMappingsAction extends BuiltInAction {
    private final Path mcpMappingsPath;
    private final String obfToSrgDataId;
    private final Supplier<CacheKey.AnnotatedValue> obfToSrgCacheKey;

    public CreateMcpMappingsAction(Path mcpMappingsPath,
                                   String obfToSrgDataId,
                                   Supplier<CacheKey.AnnotatedValue> obfToSrgCacheKey) {
        this.mcpMappingsPath = mcpMappingsPath;
        this.obfToSrgDataId = obfToSrgDataId;
        this.obfToSrgCacheKey = obfToSrgCacheKey;
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
                classBuilder.method(
                        mappedMethod.getMappedDescriptor(),
                        srgName,
                        csvMappings.methodName(srgName)
                );
            }
        }

        var srgToMcp = builder.build().getMap("srg", "mcp");
        srgToMcp.write(environment.getOutputPath("srgToMcp"), IMappingFile.Format.SRG, false);
        srgToMcp.write(environment.getOutputPath("srgToMcpTsrg"), IMappingFile.Format.TSRG, false);
        var mcpToSrg = srgToMcp.reverse();
        mcpToSrg.write(environment.getOutputPath("mcpToSrg"), IMappingFile.Format.SRG, false);
        mcpToSrg.write(environment.getOutputPath("mcpToSrgTsrg"), IMappingFile.Format.TSRG, false);
        obfToSrg.write(environment.getOutputPath("notchToSrg"), IMappingFile.Format.SRG, false);
        Files.copy(mcpMappingsPath, environment.getOutputPath("csvMappings"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder ck) {
        super.computeCacheKey(ck);
        ck.addPath("mcp mappings", mcpMappingsPath);
        ck.add("obf to srg mappings", obfToSrgCacheKey.get());
    }
}
