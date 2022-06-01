package dev.cele.cepispring.crud;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;
import dev.cele.cepispring.CLIOptions;
import dev.cele.cepispring.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
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


    public CrudMaker(CLIOptions options) {
        this.options = options;


        //scanning the directory
        Map<File, CompilationUnit> javaFiles = Utils.parseDirectory(options.directory);

        //region filtering the classes in groups
        //finding the entities
        entities = Utils.filterByHasAnnotation(javaFiles, "Entity");

        //finding repositories
        repositories = Utils.filterByHasAnnotation(javaFiles, "Repository");

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

        //finding the repository package
        repositoryPackage = repositories.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();

        //finding the service package
        servicePackage = services.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();

        //finding the serviceImpl package
        serviceImplPackage = servicesImpl.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();

        //finding the controller package
        controllerPackage = controllers.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();

        //finding the dto package
        dtoPackage = dtos.values().stream().findFirst().get().getPackageDeclaration().get().getNameAsString();
        //endregion

    }

    @Override
    public void run() {
        //getting unmapped entities
        Map<File, CompilationUnit> unmappedEntities = entities.entrySet().stream().filter(entry -> {
            return repositories.values().stream().noneMatch(repo -> {
                NodeList<Type> typeArguments = repo.getType(0).asClassOrInterfaceDeclaration().getExtendedTypes().get(0).asClassOrInterfaceType().getTypeArguments().get();
                return typeArguments.get(0).asClassOrInterfaceType().getNameAsString().equals(entry.getValue().getType(0).getNameAsString());
            });
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        //creating cruds for unmapped entities
        unmappedEntities.forEach((file, entity) -> createCrud(entity));
    }

    private void createCrud(CompilationUnit entityCu) {
        EntityInfo entity = new EntityInfo(entityCu);

        //create DTO???
        //ONLY IF IT DOESN'T EXIST

        System.out.println("\n\nCreating Repository for " + entity.name);
        System.out.println("==============================================================");
        String repository = createRepository(entity);
        System.out.println(repository);
        System.out.println("==============================================================");

        System.out.println("\n\nCreating Service for " + entity.name);
        System.out.println("==============================================================");
        String service = createService(entity);
        System.out.println(service);
        System.out.println("==============================================================");

        System.out.println("\n\nCreating Service Implementation for " + entity.name);
        System.out.println("==============================================================");
        String serviceImpl = createServiceImpl(entity);
        System.out.println(serviceImpl);
        System.out.println("==============================================================");

        System.out.println("\n\nCreating Controller for " + entity.name);
        System.out.println("==============================================================");
        String controller = createController(entity);
        System.out.println(controller);
        System.out.println("==============================================================");
    }


    private String createRepository(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the repository
        writer.println("package " + repositoryPackage + ";\n");

        writer.println("import org.springframework.data.jpa.repository.JpaRepository;");
        writer.println("import org.springframework.stereotype.Repository;");
        writer.println("import " + entity.fullyQualifiedName + ";\n");

        writer.print("public interface "+entity.name+"Repository ");
        writer.print("extends JpaRepository<"+entity.name+", "+entity.idTypeName+"> {\n\n}");

        return out.toString();
    }
    private String createService(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the service
        writer.println("package " + servicePackage + ";\n");

        writer.println("import org.springframework.stereotype.Service;");
        writer.println("import " + entity.fullyQualifiedName + ";\n");

        writer.println("import java.util.List;");
        writer.println("import java.util.Optional;\n");

        writer.println("public interface "+entity.name+"Service {");
        writer.println("\tList<"+entity.name+"> findAll();\n");
        writer.println("\tOptional<"+entity.name+"> findById("+entity.idTypeName+" id);\n");
        writer.println("\t"+entity.name+" save("+entity.name+" "+entity.name.toLowerCase()+"ToSave);\n");
        writer.println("\tboolean existsById("+entity.idTypeName+" id);\n");
        writer.println("\tvoid delete("+entity.name+" "+entity.name.toLowerCase()+"ToDelete);");
        writer.println("}");

        return out.toString();
    }

    private String createServiceImpl(EntityInfo entity) {

        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the service implementation
        writer.println("package " + serviceImplPackage + ";\n");

        writer.println("import org.springframework.beans.factory.annotation.Autowired;");
        writer.println("import org.springframework.stereotype.Service;");
        writer.println("import " + entity.fullyQualifiedName + ";");
        writer.println("import " + servicePackage + "." + entity.name +"Service;\n");

        writer.println("import java.util.List;");
        writer.println("import java.util.Optional;\n");

        writer.println("@Service");
        writer.println("public class "+entity.name+"ServiceImpl implements "+entity.name+"Service {\n");

        writer.println("\tprivate final "+entity.name+"Repository "+entity.name.toLowerCase()+"Repository;\n");

        writer.println("\tpublic "+entity.name+"ServiceImpl("+entity.name+"Repository "+entity.name.toLowerCase()+"Repository) {");
        writer.println("\t\tthis."+entity.name.toLowerCase()+"Repository = "+entity.name.toLowerCase()+"Repository;");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic List<"+entity.name+"> findAll() {");
        writer.println("\t\treturn "+entity.name.toLowerCase()+"Repository.findAll();");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic Optional<"+entity.name+"> findById("+entity.idTypeName+" id) {");
        writer.println("\t\treturn "+entity.name.toLowerCase()+"Repository.findById(id);");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic "+entity.name+" save("+entity.name+" "+entity.name.toLowerCase()+"ToSave) {");
        writer.println("\t\treturn "+entity.name.toLowerCase()+"Repository.save("+entity.name.toLowerCase()+"ToSave);");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic boolean existsById("+entity.idTypeName+" id) {");
        writer.println("\t\treturn "+entity.name.toLowerCase()+"Repository.existsById(id);");
        writer.println("\t}\n");

        writer.println("\t@Override");
        writer.println("\tpublic void delete("+entity.name+" "+entity.name.toLowerCase()+"ToDelete) {");
        writer.println("\t\t"+entity.name.toLowerCase()+"Repository.delete("+entity.name.toLowerCase()+"ToDelete);");
        writer.println("\t}\n");

        writer.println("}");

        return out.toString();
    }

    private String createController(EntityInfo entity) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        //writing the controller
        writer.println("package " + controllerPackage + ";\n");

        writer.println("import org.springframework.beans.factory.annotation.Autowired;");
        writer.println("import org.springframework.web.bind.annotation.GetMapping;");
        writer.println("import org.springframework.web.bind.annotation.PathVariable;");
        writer.println("import org.springframework.web.bind.annotation.PostMapping;");
        writer.println("import org.springframework.web.bind.annotation.RequestBody;");
        writer.println("import org.springframework.web.bind.annotation.RequestMapping;");
        writer.println("import org.springframework.web.bind.annotation.RestController;");

        writer.println("import " + entity.fullyQualifiedName + ";");
        writer.println("import " + services.values().stream().findFirst().get().getType(0).getFullyQualifiedName().get() + "." + entity.name +"Service;\n");

        return  null;
    }


    static class EntityInfo {
        public final CompilationUnit compilationUnit;
        public final ClassOrInterfaceDeclaration classDec;

        public final String packageName;
        public final String name;
        public final String fullyQualifiedName;

        public final VariableDeclarator idVariable;
        public final String idTypeName;

        public EntityInfo(CompilationUnit compilationUnit) {
            //parsing info from this entity
            this.compilationUnit = compilationUnit;
            this.classDec = compilationUnit.getType(0).asClassOrInterfaceDeclaration();

            //name
            this.name = classDec.getNameAsString();
            this.packageName = compilationUnit.getPackageDeclaration().get().getNameAsString();
            this.fullyQualifiedName = packageName + "." + name;

            //id data
            this.idVariable = classDec.getFields().stream().filter(
                    field -> field.isAnnotationPresent("Id")
            ).findFirst().get().getVariable(0);
            this.idTypeName = idVariable.getTypeAsString();

        }

    }

}