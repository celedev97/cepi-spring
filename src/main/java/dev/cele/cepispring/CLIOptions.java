package dev.cele.cepispring;


import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;

@CommandLine.Command(
        name = "cepi-fastcrud",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Create CRUDS or Typescript interfaces from Java source code")
public class CLIOptions {

    @CommandLine.Option(names = {"-t", "--typescript"}, description = "generate typescript interfaces")
    public boolean typescript;

    @CommandLine.Option(names = {"-c", "--crud"}, description = "generate c# interfaces")
    public boolean crud;

    @CommandLine.Option(
            names = { "-d", "--dir" },
            paramLabel = "project/src/main/java",
            description = "The directory of the home of the java source code"
    )
    public Path directory;

    @CommandLine.Option(
            names = { "-o", "--out" },
            paramLabel = "outputDirectory",
            description = "The directory for the output of the program"
    )
    public Path output;

    @CommandLine.Parameters( paramLabel = "FILE", description = "one or more files to archive")
    public File[] files;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    public boolean helpRequested = false;
}
