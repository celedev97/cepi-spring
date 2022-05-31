package dev.cele.cepispring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {
    public static HashMap<File, CompilationUnit> parseDirectory(Path directory){
        if(directory == null){
            throw new RuntimeException("No directory specified");
        }

        SourceRoot sourceRoot = new SourceRoot(directory);

        List<File> files = getAllFilesInDirectory(directory.toFile(), null);
        List<File> javaFiles = files.stream().filter(it -> it.getName().endsWith(".java")).collect(Collectors.toList());


        //creating a compilation unit for each java file
        HashMap<File, CompilationUnit> javaFilesMap = new HashMap<>();
        for (File javaFile : javaFiles) {
            //making java file local
            Path relativePath = directory.relativize(javaFile.toPath());
            //extracting the package name from the relativePath
            String packageName = relativePath.getParent().toString().replace(File.separator, ".");

            //parsing the java file
            javaFilesMap.put(javaFile, sourceRoot.parse(packageName, javaFile.getName()));
        }

        return javaFilesMap;
    }

    public static List<File> getAllFilesInDirectory(File directory, List<File> output){
        if(output == null){
            output = new ArrayList<>();
        }
        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if(fList == null)
            return output;

        for (File file : fList) {
            if (file.isFile()) {
                output.add(file);
            } else if (file.isDirectory()) {
                getAllFilesInDirectory(file, output);
            }
        }
        return output;
    }

    public static Map<File, CompilationUnit> filterByPackageOrClassContains(HashMap<File, CompilationUnit> javaFiles, String filterString){
        String filter = filterString.toLowerCase();
        return javaFiles.entrySet().stream().filter( entry ->{
            CompilationUnit cu = entry.getValue();
            String packageName = cu.getPackageDeclaration().map(it -> it.getNameAsString()).orElse("");
            String className = cu.getType(0).getNameAsString();

            return packageName.toLowerCase().contains(filter) || className.toLowerCase().endsWith(filter);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
