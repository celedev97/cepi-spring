package dev.cele.cepispring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TypescriptMaker implements Runnable {
    private final CLIOptions options;

    public TypescriptMaker(CLIOptions options) {
        this.options = options;
    }

    @Override
    public void run() {
        //scanning the directory
        HashMap<File, CompilationUnit> javaFiles = Utils.parseDirectory(options.directory);

        //getting the DTOs
        Map<File, CompilationUnit> dtos = Utils.filterByPackageOrClassContains(javaFiles ,"dto");
        Map<File, CompilationUnit> entities = Utils.filterByPackageOrClassContains(javaFiles ,"entity");


        if(dtos.size() == 0){
            dtos = entities;
            entities = new HashMap<>();
        }

        //converting the DTOs to Typescript
        Map<File, CompilationUnit> finalEntities = entities;
        dtos.forEach((file, dto) -> {
            //finding the entity with the same name as this DTO
            String dtoName = dto.getType(0).getNameAsString();
            CompilationUnit dtoEntity = finalEntities.values().stream()
                    .filter(entity -> dtoName.contains(entity.getType(0).getNameAsString()))
                    .findFirst().orElse(null);

            String typeScript = convertToTypescript(dto, dtoEntity);
            System.out.println(typeScript);
            if(options.output != null){

                String typeScriptFileName = dtoName.replaceAll("(?i)dto","") + ".model.ts";
                Path outputFilePath = options.output.resolve(typeScriptFileName);

                System.out.println("Writing to file: "+outputFilePath);

                try {
                    Files.createDirectories(outputFilePath.getParent());
                    Files.write(outputFilePath, typeScript.getBytes());
                    System.out.println("Done!");
                } catch (IOException e) {
                    System.out.println("There was an error writing the file: "+outputFilePath);
                }
            }
        });
    }

    private String convertToTypescript(CompilationUnit dto, CompilationUnit entity) {
        //region preparing data
        if(!dto.getType(0).isClassOrInterfaceDeclaration()){
            return null;
        }
        if(entity != null && !entity.getType(0).isClassOrInterfaceDeclaration()){
            entity = null;
        }

        ClassOrInterfaceDeclaration dtoClass = dto.getType(0).asClassOrInterfaceDeclaration();
        ClassOrInterfaceDeclaration entityClass = entity != null ? entity.getType(0).asClassOrInterfaceDeclaration() : null;


        //creating the interface opening
        StringWriter output = new StringWriter();
        PrintWriter printer = new PrintWriter(output);
        //endregion


        printer.println("export interface " + dtoClass.getNameAsString().replaceAll("(?i)dto", "") + " {");

        dtoClass.getFields().forEach(field -> {
            VariableDeclarator variable = field.getVariable(0);
            String variableName = variable.getNameAsString();

            //checking if the variable is the @ID field
            if(variableName.equals("id")){
                printer.println("  "+variableName+": "+ tsType(variable.getType()) +" | undefined,");
            }else{
                printer.println("  "+variableName+": "+ tsType(variable.getType()) +",");
            }



        });

        printer.println("}");
        return output.toString();
    }

    private static String tsType(Type type) {
        String typeString = type.toString();
        //checking if it's a number
        if(typeString.equals("int") ||
                typeString.equals("long") ||
                typeString.equals("double") ||
                typeString.equals("float") ||
                typeString.equals("short") ||
                typeString.equals("byte") ||
                typeString.equals("Integer") ||
                typeString.equals("Long") ||
                typeString.equals("Double") ||
                typeString.equals("Float") ||
                typeString.equals("Short") ||
                typeString.equals("Byte")
        ){
            return "number";
        }

        if(typeString.equals("boolean") || typeString.equals("Boolean")){
            return "boolean";
        }

        if (typeString.equals("String")) {
            return "string";
        }

        if (typeString.equals("Object")) {
            return "any";
        }

        if(typeString.startsWith("List")){
            return tsType(((ClassOrInterfaceType)type).getTypeArguments().get().get(0))+"[]";
        }

        return type.toString().replaceAll("(?i)dto", "");
    }

}
