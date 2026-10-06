package net.neoforged.neoform.runtime.graph;

public enum NodeOutputType {
    JAR(".jar"),
    JAR_MANIFEST(".MF"),
    TXT(".txt"),
    ZIP(".zip"),
    JSON(".json"),
    TSRG(".tsrg"),
    // TSRG2 intentionally shares the .tsrg extension; do not infer the format from the file name.
    TSRG2(".tsrg"),
    SRG(".srg");

    private final String extension;

    NodeOutputType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
