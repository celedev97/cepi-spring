package dev.cele.cepispring.crud;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.cele.cepispring.CLIOptions;
import dev.cele.cepispring.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CrudMaker implements Runnable {
    private final CLIOptions options;

    private final Map<File, CompilationUnit> entities;
    private final Map<File, CompilationUnit> repositories;
    private final Map<File, CompilationUnit> services;
    private final Map<File, CompilationUnit> servicesImpl;
    private final Map<File, CompilationUnit> controllers;

    private final Map<File, CompilationUnit> dtos;

    private final String entityPackage;
    private final String repositoryPackage;
    private final String servicePackage;
    private final String serviceImplPackage;
    private final String controllerPackage;
    private final String dtoPackage;

    private final String entityPath;
    private final String repositoryPath;
    private final String servicePath;
    private final String serviceImplPath;
    private final String controllerPath;
    private final String dtoPath;


    public CrudMaker(CLIOptions options) {
        this.options = options;


        //scanning the directory
        Map<File, CompilationUnit> javaFiles = Utils.parseDirectory(options.directory);

        //region filtering the classes in groups

        //finding repositories
        repositories = Utils.filterByHasAnnotation(javaFiles, "Repository");

        //finding the entities
        entities = Utils.filterByHasAnnotation(javaFiles, "Entity");


        //finding services implementation
        servicesImpl = Utils.filterByIsClass(
                Utils.filterByHasAnnotation(javaFiles, "Service")
        );

        //finding services interface
        List<String> servicesInterfacesName = servicesImpl.values().stream().flatMap(cu ->
                ((ClassOrInterfaceDeclaration)cu.getType(0)).getImplementedTypes().stream()
        ).map(type -> type.asClassOrInterfaceType().getNameAsString()).distinct().collect(Collectors.toList());

        services = Utils.filterByIsInterface(javaFiles).entrySet().stream().filter(entry ->{
            return servicesInterfacesName.contains(entry.getValue().getType(0).getNameAsString());
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        //finding controllers
        controllers = Utils.filterByHasAnnotation(javaFiles, "RestController");

        //finding DTOs
        dtos = Utils.filterByPackageOrClassContains(javaFiles,"dto");
        //endregion

        //region Finding the package names
        //finding the entity package
        entityPackage = entities.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        entityPath = entityPackage.replace(".", "/");

        //finding the repository package
        repositoryPackage = repositories.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        repositoryPath = repositoryPackage.replace(".", "/");

        //finding the service package
        servicePackage = services.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        servicePath = servicePackage.replace(".", "/");

        //finding the serviceImpl package
        serviceImplPackage = servicesImpl.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        serviceImplPath = serviceImplPackage.replace(".", "/");

        //finding the controller package
        controllerPackage = controllers.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        controllerPath = controllerPackage.replace(".", "/");

        //finding the dto package
        dtoPackage = dtos.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        dtoPath = dtoPackage.replace(".", "/");
        //endregion

    }

    @Override
    public void run() {
        //getting the entities in the repositories
        List<ClassOrInterfaceType> repositoriesEntities = repositories.values().stream()
                .map(repo -> repo.getType(0).asClassOrInterfaceDeclaration())
                .map(repo -> repo.getExtendedTypes().get(0).asClassOrInterfaceType())
                .map(jpaRepo -> jpaRepo.getTypeArguments().get().get(0).asClassOrInterfaceType()).collect(Collectors.toList());

        //getting unmapped entities
        Map<File, CompilationUnit> unmappedEntities = entities.entrySet().stream().filter(entry ->
                repositoriesEntities.stream().noneMatch(
                    repoEntity -> repoEntity.getNameAsString().equals(entry.getValue().getType(0).getNameAsString())
                )
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        System.out.println("Found " + unmappedEntities.size() + " unmapped entities:");
        unmappedEntities.forEach((file, cu) -> System.out.println(cu.getType(0).getNameAsString()));

        pic

        //creating cruds for unmapped entities
        unmappedEntities.forEach((file, entity) -> createCrud(entity));
    }

    private void createCrud(CompilationUnit entityCu) {
        EntityInfo entity = new EntityInfo(entityCu);

        //create DTO???
        //ONLY IF IT DOESN'T EXIST

        generateAndPrint(entity, "Dto", dtoPath, this::createDto);
        generateAndPrint(entity, "Repository", repositoryPath, this::createRepository);
        generateAndPrint(entity, "Service", servicePath, this::createService);
        generateAndPrint(entity, "ServiceImpl", serviceImplPath, this::createServiceImpl);
        generateAndPrint(entity, "Controller", controllerPath, this::createController);

    }

    private void generateAndPrint(EntityInfo entity, String subfix, String packagePath, Function<EntityInfo, String> call) {
        System.out.println("\n\nCreating "+subfix+" for " + entity.name);
        System.out.println("==============================================================");

        String output = call.apply(entity);

        System.out.println(output);

        if(options.output != null) {
            try {
                System.out.println("Writing to " + options.output + "/" + packagePath +"/" + entity.name + subfix + ".java");
                Files.write(options.output.resolve(packagePath +"/" + entity.name + subfix + ".java"), output.getBytes());
            } catch (Exception e) {
                System.out.println("Error writing " + subfix);
                e.printStackTrace();
            }
        }

    }


    private String createDto(EntityInfo entityInfo) {

        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the dto
        //package
        writer.println("package " + dtoPackage + ";\n");

        //imports
        writer.println("import java.util.List;");
        writer.println("import lombok.*;\n");

        //class
        writer.println("@Data @NoArgsConstructor @AllArgsConstructor");
        writer.println("public class " + entityInfo.dtoNname+ " {");

        //fields
        entityInfo.classDec.getFields().stream().flatMap( field ->
                field.getVariables().stream()
        ).forEach(variable -> {
            //checking if the type is an entity
            String type = variable.getTypeAsString();

            if(isTypeEntity(type)){
                type += "Dto";
            } else if (variable.getTypeAsString().contains("List")) {
                String innerType = variable.getTypeAsString().substring(variable.getTypeAsString().indexOf("<")+1, variable.getTypeAsString().indexOf(">"));
                if(isTypeEntity(innerType)){
                    type = "List<"+innerType+"Dto>";
                }
            }

            writer.println("\tprivate " + type + " " + variable.getNameAsString() + ";");
        });

        //end class
        writer.println("}");

        return out.toString();
    }

    private boolean isTypeEntity(String typeAsString) {
        return entities.values().stream()
                .map(it -> it.getType(0).asClassOrInterfaceDeclaration())
                .anyMatch(it ->
                        typeAsString.equals(it.getNameAsString())
                );
    }


    private String createRepository(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the repository
        //package
        writer.println("package " + repositoryPackage + ";\n");

        //imports
        writer.println("import org.springframework.data.jpa.repository.JpaRepository;");
        writer.println("import org.springframework.stereotype.Repository;");
        writer.println("import " + entity.fullyQualifiedName + ";\n");

        //interface
        writer.println("@Repository");
        writer.print("public interface "+entity.name+"Repository ");
        writer.print("extends JpaRepository<"+entity.name+", "+entity.idTypeName+"> {\n\n}");

        return out.toString();
    }
    private String createService(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the service
        //package
        writer.println("package " + servicePackage + ";\n");

        //imports
        writer.println("import " + dtoPackage+"."+entity.dtoNname + ";\n");

        writer.println("import java.util.List;");
        writer.println("import java.util.Optional;\n");

        //interface
        writer.println("public interface "+entity.name+"Service {");

        //methods
        writer.println("\tList<"+entity.dtoNname+"> findAll();\n");
        writer.println("\tOptional<"+entity.dtoNname+"> findById("+entity.idTypeName+" id);\n");
        writer.println("\t"+entity.dtoNname+" save("+entity.dtoNname+" "+entity.lowerName+"DtoToSave);\n");
        writer.println("\tboolean existsById("+entity.idTypeName+" id);\n");
        writer.println("\tvoid delete("+entity.dtoNname+" "+entity.lowerName+"DtoToDelete);");

        //end interface
        writer.println("}");

        return out.toString();
    }

    private String createServiceImpl(EntityInfo entity) {

        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the service implementation
        //package
        writer.println("package " + serviceImplPackage + ";\n");

        //imports
        writer.println("import org.springframework.stereotype.Service;\n");

        writer.println("import " + entity.fullyQualifiedName + ";");
        writer.println("import " + dtoPackage+"."+entity.dtoNname + ";");
        writer.println("import " + servicePackage + "." + entity.name +"Service;");
        writer.println("import " + repositoryPackage + "." + entity.name +"Repository;\n");

        writer.println("import java.util.List;");
        writer.println("import java.util.Optional;");
        writer.println("import java.util.stream.Collectors;\n");

        writer.println("import org.modelmapper.ModelMapper;\n");

        //class
        writer.println("@Service");
        writer.println("public class "+entity.name+"ServiceImpl implements "+entity.name+"Service {\n");

        //autowired fields
        writer.println("\tprivate final "+entity.name+"Repository "+entity.repository+";");
        writer.println("\tprivate final ModelMapper modelMapper;\n");

        //constructor
        writer.println("\tpublic "+entity.name+"ServiceImpl("+entity.name+"Repository "+entity.repository+", ModelMapper modelMapper) {");
        writer.println("\t\tthis."+entity.repository+" = "+entity.repository+";");
        writer.println("\t\tthis.modelMapper = modelMapper;");
        writer.println("\t}\n");

        //methods
        writer.println("\t@Override");
        writer.println("\tpublic List<"+entity.dtoNname+"> findAll() {");
        writer.println("\t\treturn "+entity.repository+".findAll().stream().map(it ->");
        writer.println("\t\t\tmodelMapper.map(it, "+entity.dtoNname+".class)");
        writer.println("\t\t).collect(Collectors.toList());");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic Optional<"+entity.dtoNname+"> findById("+entity.idTypeName+" id) {");
        writer.println("\t\tOptional<"+entity.name+"> "+entity.lowerName+" = "+entity.repository+".findById(id);");
        writer.println("\t\treturn "+entity.lowerName+".map(it -> modelMapper.map(it, "+entity.dtoNname+".class));");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic "+entity.dtoNname+" save("+entity.dtoNname+" "+entity.lowerName+"ToSave) {");
        writer.println("\t\t"+entity.name+" saved = "+entity.repository+".save(modelMapper.map("+entity.lowerName+"ToSave, "+entity.name+".class));");
        writer.println("\t\treturn modelMapper.map(saved, "+entity.dtoNname+".class);");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic boolean existsById("+entity.idTypeName+" id) {");
        writer.println("\t\treturn "+entity.repository+".existsById(id);");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic void delete("+entity.dtoNname+" "+entity.lowerName+"ToDelete) {");
        writer.println("\t\t"+entity.repository+".delete(modelMapper.map("+entity.lowerName+"ToDelete, "+entity.name+".class));");
        writer.println("\t}\n");

        //end class
        writer.println("}");

        return out.toString();
    }

    private String createController(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the controller
        //package
        writer.println("package " + controllerPackage + ";\n");

        //imports
        writer.println("import org.springframework.web.bind.annotation.*;");

        writer.println("import " + dtoPackage+"."+entity.dtoNname + ";");
        writer.println("import " + servicePackage + "." + entity.name +"Service;\n");

        writer.println("import javax.validation.Valid;");
        writer.println("import java.util.List;");
        writer.println("import java.util.Optional;\n");

        //class
        writer.println("@RestController");
        writer.println("@RequestMapping(\"/"+Utils.toKebabCase(entity.name)+"\")");
        writer.println("public class "+entity.name+"Controller {");

        //autowired fields
        writer.println("\tprivate final "+entity.name+"Service "+entity.lowerName+"Service;");

        //constructor
        writer.println("\tpublic "+entity.name+"Controller("+entity.name+"Service "+entity.lowerName+"Service) {");
        writer.println("\t\tthis."+entity.lowerName+"Service = "+entity.lowerName+"Service;");
        writer.println("\t}\n");

        //methods
        writer.println("\t@GetMapping()");
        writer.println("\tpublic List<"+entity.dtoNname+"> readAll() {");
        writer.println("\t\treturn "+entity.lowerName+"Service.findAll();");
        writer.println("\t}\n");

        writer.println("\t@GetMapping(\"/{id}\")");
        writer.println("\tpublic Optional<"+entity.dtoNname+"> readById(@PathVariable "+entity.idTypeName+" id) {");
        writer.println("\t\treturn "+entity.lowerName+"Service.findById(id);");
        writer.println("\t}\n");

        writer.println("\t@PostMapping()");
        writer.println("\tpublic "+entity.dtoNname+" create(@Valid @RequestBody "+entity.dtoNname+" "+entity.lowerName+"ToCreate) {");
        writer.println("\t\treturn "+entity.lowerName+"Service.save("+entity.lowerName+"ToCreate);");
        writer.println("\t}\n");

        writer.println("\t@PutMapping()");
        writer.println("\tpublic "+entity.dtoNname+" update(@Valid @RequestBody "+entity.dtoNname+" "+entity.lowerName+"ToUpdate) {");
        writer.println("\t\treturn "+entity.lowerName+"Service.save("+entity.lowerName+"ToUpdate);");
        writer.println("\t}\n");

        writer.println("\t@DeleteMapping()");
        writer.println("\tpublic void delete(@RequestBody "+entity.dtoNname+" "+entity.lowerName+"ToDelete) {");
        writer.println("\t\t"+entity.lowerName+"Service.delete("+entity.lowerName+"ToDelete);");
        writer.println("\t}\n");

        //end class
        writer.println("}");

        return out.toString();
    }


    static class EntityInfo {
        public final CompilationUnit compilationUnit;
        public final ClassOrInterfaceDeclaration classDec;

        public final String packageName;
        public final String name;
        private final String lowerName;
        public final String fullyQualifiedName;

        public final VariableDeclarator idVariable;
        public final String idTypeName;
        public final String dtoNname;
        public final String repository;

        public EntityInfo(CompilationUnit compilationUnit) {
            //parsing info from this entity
            this.compilationUnit = compilationUnit;
            this.classDec = compilationUnit.getType(0).asClassOrInterfaceDeclaration();

            //name
            this.name = classDec.getNameAsString();
            this.lowerName = name.toLowerCase().charAt(0) + name.substring(1);
            this.packageName = compilationUnit.getPackageDeclaration().get().getNameAsString();
            this.fullyQualifiedName = packageName + "." + name;
            this.dtoNname = name + "Dto";
            this.repository = lowerName+"Repository";

            //id data
            this.idVariable = classDec.getFields().stream().filter(
                    field -> field.isAnnotationPresent("Id")
            ).findFirst().get().getVariable(0);
            this.idTypeName = idVariable.getTypeAsString();

        }

    }

}