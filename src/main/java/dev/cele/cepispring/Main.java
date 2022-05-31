package dev.cele.cepispring;

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        //parsing CLI Option
        CLIOptions options = new CLIOptions();
        new CommandLine(options).parseArgs(args);

        if(options.helpRequested){
            new CommandLine(options).usage(System.out);
            return;
        }

        if(options.typescript){
            //generating typscript interfaces
            new TypescriptMaker(options).run();
            return;
        }

        if(options.crud){
            System.out.println("Not Implemented yet");
            return;
        }

        new CommandLine(options).usage(System.out);

    }
}