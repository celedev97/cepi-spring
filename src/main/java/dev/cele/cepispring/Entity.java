package dev.cele.cepispring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.util.List;

public class Entity {
    public Entity(CompilationUnit cu){

        //getting the entity class
        ClassOrInterfaceDeclaration entity = cu.getTypes().stream()
                .filter(BodyDeclaration::isClassOrInterfaceDeclaration)
                .map(it -> (ClassOrInterfaceDeclaration) it)
                .filter(it -> !it.isInterface() && it.isAnnotationPresent("Entity"))
                .findFirst().orElseThrow(() -> new RuntimeException("No entity class found"));


        //getting the fields
        List<FieldDeclaration> fields = entity.getFields();

        //getting the id field
        FieldDeclaration idField = fields.stream()
                .filter(it -> it.isAnnotationPresent("Id"))
                .findFirst().orElseThrow(() -> new RuntimeException("No id field found"));

        System.out.println();
    }
}
