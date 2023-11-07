package org.wildfly.experimental.api.classpath.index;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.experimental.api.classpath.index.classes.AnnotationWithExperimental;
import org.wildfly.experimental.api.classpath.index.classes.AnnotationWithExperimentalMethods;
import org.wildfly.experimental.api.classpath.index.classes.ClassWithExperimental;
import org.wildfly.experimental.api.classpath.index.classes.ClassWithExperimentalConstructors;
import org.wildfly.experimental.api.classpath.index.classes.ClassWithExperimentalFields;
import org.wildfly.experimental.api.classpath.index.classes.ClassWithExperimentalMethods;
import org.wildfly.experimental.api.classpath.index.classes.Experimental;
import org.wildfly.experimental.api.classpath.index.classes.InterfaceWithExperimental;
import org.wildfly.experimental.api.classpath.index.classes.InterfaceWithExperimentalMethods;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassArrayUsageAsField;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassArrayUsageAsMethodParameter;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassArrayUsageAsMethodReturnType;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassArrayUsageInMethodBody;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassExtendsUsage;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassImplementsUsage;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassUsageAsField;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassUsageAsMethodParameter;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassUsageAsMethodReturnType;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassUsageInMethodBody;
import org.wildfly.experimental.api.classpath.index.classes.usage.ClassUsageSetter;
import org.wildfly.experimental.api.classpath.index.classes.usage.ConstructorReference;
import org.wildfly.experimental.api.classpath.index.classes.usage.FieldReference;
import org.wildfly.experimental.api.classpath.index.classes.usage.MethodReference;
import org.wildfly.experimental.api.classpath.index.classes.usage.NoUsage;
import org.wildfly.experimental.api.classpath.index.classes.usage.StaticFieldReference;
import org.wildfly.experimental.api.classpath.index.classes.usage.StaticMethodReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotatedClassUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotatedFieldReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotatedMethodReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.ExtendsAnnotatedClass;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.ImplementsAnnotatedInterface;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.CLASS_USAGE;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.EXTENDS_CLASS;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.FIELD_REFERENCE;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.IMPLEMENTS_INTERFACE;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.METHOD_REFERENCE;

/**
 * Tests non-annotation usage of references annotated with experimental annotations
 */
public class RuntimeReferenceTestCase {
    private static final String EXPERIMENTAL_ANNOTATION = Experimental.class.getName();
    RuntimeIndex runtimeIndex;
    @Before
    public void createRuntimeIndex() throws IOException {
        OverallIndex overallIndex = new OverallIndex();
        File file = TestUtils.createJar(
                AnnotationWithExperimental.class,
                ClassWithExperimental.class,
                InterfaceWithExperimental.class,
                ClassWithExperimentalMethods.class,
                InterfaceWithExperimentalMethods.class,
                AnnotationWithExperimentalMethods.class,
                ClassWithExperimentalConstructors.class,
                ClassWithExperimentalFields.class);
        overallIndex.scanJar(file, EXPERIMENTAL_ANNOTATION, Collections.emptySet());

        Path p = Paths.get("target/index/runtime-test");
        overallIndex.save(p);

        runtimeIndex = RuntimeIndex.load(p);
    }

    @Test
    public void testNoUsage() throws Exception {
        ClassBytecodeInspector inspector = new ClassBytecodeInspector(runtimeIndex);
        boolean ok = scanClass(inspector, NoUsage.class);
        Assert.assertTrue(ok);
        Assert.assertEquals(0, inspector.getUsages().size());
    }

    @Test
    public void testClassExtendsUsage() throws Exception {
        ExtendsAnnotatedClass usage =
                scanAndGetSingleAnnotationUsage(ClassExtendsUsage.class, EXTENDS_CLASS)
                        .asExtendsAnnotatedClass();

        Assert.assertEquals(convertClassNameToVmFormat(ClassExtendsUsage.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getSuperClass());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testClassImplementsUsage() throws Exception {
        ImplementsAnnotatedInterface usage =
                scanAndGetSingleAnnotationUsage(ClassImplementsUsage.class, IMPLEMENTS_INTERFACE)
                        .asImplementsAnnotatedInterface();

        Assert.assertEquals(convertClassNameToVmFormat(ClassImplementsUsage.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(InterfaceWithExperimental.class), usage.getInterface());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testConstructorReference() throws Exception {
        AnnotatedMethodReference usage =
                scanAndGetSingleAnnotationUsage(ConstructorReference.class, METHOD_REFERENCE)
                        .asAnnotatedMethodReference();

        Assert.assertEquals(convertClassNameToVmFormat(ConstructorReference.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimentalConstructors.class), usage.getMethodClass());
        Assert.assertEquals(ClassBytecodeInspector.BYTECODE_CONSTRUCTOR_NAME, usage.getMethodName());
        Assert.assertEquals("(Ljava/lang/String;)V", usage.getDescriptor());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testFieldReference() throws Exception {
        AnnotatedFieldReference usage =
                scanAndGetSingleAnnotationUsage(FieldReference.class, FIELD_REFERENCE)
                        .asAnnotatedFieldReference();

        Assert.assertEquals(convertClassNameToVmFormat(FieldReference.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimentalFields.class), usage.getFieldClass());
        Assert.assertEquals("fieldA", usage.getFieldName());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testStaticFieldReference() throws Exception {
        AnnotatedFieldReference usage =
                scanAndGetSingleAnnotationUsage(StaticFieldReference.class, FIELD_REFERENCE)
                        .asAnnotatedFieldReference();

        Assert.assertEquals(convertClassNameToVmFormat(StaticFieldReference.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimentalFields.class), usage.getFieldClass());
        Assert.assertEquals("fieldB", usage.getFieldName());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testMethodReference() throws Exception {
        AnnotatedMethodReference usage =
                scanAndGetSingleAnnotationUsage(MethodReference.class, METHOD_REFERENCE)
                        .asAnnotatedMethodReference();

        Assert.assertEquals(convertClassNameToVmFormat(MethodReference.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimentalMethods.class), usage.getMethodClass());
        Assert.assertEquals("test", usage.getMethodName());
        Assert.assertEquals("()V", usage.getDescriptor());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    @Test
    public void testStaticMethodReference() throws Exception {
        AnnotatedMethodReference usage =
                scanAndGetSingleAnnotationUsage(StaticMethodReference.class, METHOD_REFERENCE)
                        .asAnnotatedMethodReference();

        Assert.assertEquals(convertClassNameToVmFormat(StaticMethodReference.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimentalMethods.class), usage.getMethodClass());
        Assert.assertEquals("test", usage.getMethodName());
        Assert.assertEquals("(Ljava/lang/String;)V", usage.getDescriptor());
        // TODO is it weird that we are using JVM format for everything else but not here?
        Assert.assertEquals(Collections.singleton(Experimental.class.getName()), usage.getAnnotations());
    }

    //TODO If this turns out to be important, we might want to look at the UTF8Infos where the classname appears
    // If it is a match, temporarily record it. If the not registered by the normal means then we will need to search fields + methods
    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassUsageAsField() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassUsageAsField.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassUsageAsField.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassArrayUsageAsField() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassArrayUsageAsField.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassArrayUsageAsField.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(InterfaceWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassUsageAsMethodParameter() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassUsageAsMethodParameter.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassUsageAsMethodParameter.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassArrayUsageAsMethodParameter() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassArrayUsageAsMethodParameter.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassArrayUsageAsMethodParameter.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(InterfaceWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassUsageAsMethodReturnType() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassUsageAsMethodReturnType.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassUsageAsMethodReturnType.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassArrayUsageAsMethodReturnType() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassArrayUsageAsMethodReturnType.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassArrayUsageAsMethodReturnType.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(InterfaceWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    @Ignore("Just referencing a class in a declaration doesn't seem to add it unless it is actually used, as in testClassUsageAsMethodBody()")
    public void testClassUsageSetter() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassUsageSetter.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassUsageSetter.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    public void testClassUsageAsMethodBody() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassUsageInMethodBody.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassUsageInMethodBody.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(ClassWithExperimental.class), usage.getReferencedClass());
    }

    @Test
    public void testClassArrayUsageAsMethodBody() throws Exception {
        AnnotatedClassUsage usage =
                scanAndGetSingleAnnotationUsage(ClassArrayUsageInMethodBody.class, CLASS_USAGE)
                        .asAnnotatedClassUsage();
        Assert.assertEquals(convertClassNameToVmFormat(ClassArrayUsageInMethodBody.class), usage.getSourceClass());
        Assert.assertEquals(convertClassNameToVmFormat(InterfaceWithExperimental.class), usage.getReferencedClass());
    }

    AnnotationUsage scanAndGetSingleAnnotationUsage(
            Class<?> clazz,
            ClassBytecodeInspector.AnnotationUsageType type) throws IOException {
        ClassBytecodeInspector inspector = new ClassBytecodeInspector(runtimeIndex);
        boolean ok = scanClass(inspector, clazz);
        Assert.assertFalse(ok);
        Assert.assertEquals(1, inspector.getUsages().size());
        AnnotationUsage usage = inspector.getUsages().iterator().next();
        Assert.assertEquals(type, usage.getType());
        return usage;
    }

    String convertClassNameToVmFormat(Class<?> clazz) {
        return RuntimeIndex.convertClassNameToVmFormat(clazz.getName());
    }

    private boolean scanClass(ClassBytecodeInspector inspector, Class<?> clazz) throws IOException {
        String classLocation = clazz.getName().replaceAll("\\.", "/") + ".class";
        URL url = RuntimeReferenceTestCase.class.getClassLoader().getResource(classLocation);
        try (InputStream in = url.openStream()) {
            return inspector.scanClassFile(clazz.getName(), in);
        }
    }
}