package dev.cele.cepispring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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

    public static Map<File, CompilationUnit> filterByPackageOrClassContains(Map<File, CompilationUnit> javaFiles, String filterString){
        String filter = filterString.toLowerCase();
        return javaFiles.entrySet().stream().filter( entry ->{
            CompilationUnit cu = entry.getValue();
            String packageName = cu.getPackageDeclaration().map(it -> it.getNameAsString()).orElse("");
            String className = cu.getType(0).getNameAsString();

            return packageName.toLowerCase().contains(filter) || className.toLowerCase().endsWith(filter);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<File, CompilationUnit> filterByHasAnnotation(Map<File, CompilationUnit> javaFiles, String filterString){
        return javaFiles.entrySet().stream()
                .filter(entry ->{
                    if(!entry.getValue().getType(0).isClassOrInterfaceDeclaration()){
                        return false;
                    }
                    ClassOrInterfaceDeclaration coi = (ClassOrInterfaceDeclaration) entry.getValue().getType(0);

                    return coi.isAnnotationPresent(filterString);
                }).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    public static Map<File, CompilationUnit> filterByIsClass(Map<File, CompilationUnit> javaFiles) {
        return javaFiles.entrySet().stream().filter(entry -> {
            if(!entry.getValue().getType(0).isClassOrInterfaceDeclaration()){
                return false;
            }
            ClassOrInterfaceDeclaration coi = (ClassOrInterfaceDeclaration) entry.getValue().getType(0);

            return !coi.isInterface();
        }).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    public static Map<File, CompilationUnit> filterByIsInterface(Map<File, CompilationUnit> javaFiles) {
        return javaFiles.entrySet().stream().filter(entry -> {
            if(!entry.getValue().getType(0).isClassOrInterfaceDeclaration()){
                return false;
            }
            ClassOrInterfaceDeclaration coi = (ClassOrInterfaceDeclaration) entry.getValue().getType(0);

            return coi.isInterface();
        }).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    public static String toKebabCase(String className) {
        return className.chars().mapToObj(c -> ""+(char)c).map(c -> {
            if(Character.isUpperCase(c.charAt(0))){
                return "-"+c.toLowerCase();
            }
            return c;
        }).collect(Collectors.joining()).substring(1);
    }
}
