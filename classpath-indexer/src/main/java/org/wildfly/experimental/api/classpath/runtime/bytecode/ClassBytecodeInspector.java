package org.wildfly.experimental.api.classpath.runtime.bytecode;

import org.wildfly.experimental.api.classpath.index.RuntimeIndex;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ConstantPool.AbstractRefInfo;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ConstantPool.NameAndTypeInfo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.CLASS_EXTENDS;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.CLASS_IMPLEMENTS;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.FIELD_REFERENCE;
import static org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsageType.METHOD_REFERENCE;

public class ClassBytecodeInspector {

    private final RuntimeIndex runtimeIndex;

    private final Set<AnnotationUsage> usages = new LinkedHashSet<>();


    public ClassBytecodeInspector(RuntimeIndex runtimeIndex) {
        this.runtimeIndex = runtimeIndex;
    }

    public Set<AnnotationUsage> getUsages() {
        return usages;
    }

    /**
     * Scans a class file and looks for usage of things annotated by the experimental annotations
     *
     * @param className The name of the class we are scanning
     * @param classInputStream An input stream with the class bytes. A plain input stream may be used. This method will
     *                         wrap in a BufferedInputStream
     * @return {@code true} if no usage was found
     * @throws IOException
     */
    public boolean scanClassFile(String className, InputStream classInputStream) throws IOException {
        boolean noAnnotationUsage = true;
        DataInputStream in = new DataInputStream(new BufferedInputStream(classInputStream));

        // Parse the stuff before the ConstantPool
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("Not a valid class file (no CAFEBABE header)");
        }
        //Minor Version, we don't need this
        in.readUnsignedShort();
        // Major version, we don't need this
        in.readUnsignedShort();

        ////////////////////////////////////
        // Check the constant pool for method (includes constructors) and field references
        ConstantPool constantPool = ConstantPool.read(in);

        for (ConstantPool.Info info : constantPool.pool) {
            if (info == null) {
                continue;
            }
            switch (info.tag()) {
                case ConstantPool.CONSTANT_Fieldref:
                case ConstantPool.CONSTANT_InterfaceMethodref:
                case ConstantPool.CONSTANT_Methodref: {
                    AbstractRefInfo ref = (AbstractRefInfo)info;
                    String declaringClass = constantPool.className(ref.class_index);
                    NameAndTypeInfo nameAndTypeInfo = (NameAndTypeInfo)constantPool.pool[ref.name_and_type_index];
                    String refName = constantPool.utf8(nameAndTypeInfo.name_index);
                    if (info.tag() == ConstantPool.CONSTANT_Fieldref) {
                        Set<String> annotations = runtimeIndex.getAnnotationsForField(declaringClass, refName);
                        if (annotations != null) {
                            recordFieldUsage(annotations, className, declaringClass, refName);
                            noAnnotationUsage = false;
                        }
                    } else {
                        String descriptor = constantPool.utf8(nameAndTypeInfo.descriptor_index);
                        Set<String> annotations = runtimeIndex.getAnnotationsForMethod(declaringClass, refName, descriptor);
                        if (annotations != null) {
                            recordMethodUsage(annotations, className, declaringClass, refName, descriptor);
                            noAnnotationUsage = false;
                        }
                    }
                    break;
                }
                // TODO might need to look into MethodHandle etc.
            }
        }

        //////////////////////////////////////////
        // Read and check the superclass and interfaces

        // Access flags, we don't need this
        int access_flags = in.readUnsignedShort();

        // This class index, we don't need this
        in.readUnsignedShort();

        int super_class_index = in.readUnsignedShort();
        if (super_class_index != 0) {
            String superClass = constantPool.className(super_class_index);
            Set<String> annotations = runtimeIndex.getAnnotationsForClass(superClass);
            if (annotations != null) {
                recordSuperClassUsage(annotations, className, superClass);
                noAnnotationUsage = false;
            }
        }


        int interfacesCount = in.readUnsignedShort();
        for (int i = 0; i < interfacesCount; i++) {
            int interfaceIndex = in.readUnsignedShort();
            String iface = constantPool.className(interfaceIndex);
            Set<String> annotations = runtimeIndex.getAnnotationsForClass(iface);
            if (annotations != null) {
                recordImplementsInterfaceUsage(annotations, className, iface);
                noAnnotationUsage = false;
            }
        }
        return noAnnotationUsage;
    }

    private void recordSuperClassUsage(Set<String> annotations, String clazz, String superClass) {
        usages.add(new ExtendsAnnotatedClass(annotations, clazz, superClass));
    }

    private void recordImplementsInterfaceUsage(Set<String> annotations, String className, String iface) {
        usages.add(new ImplementsAnnotatedInterface(annotations, className, iface));
    }

    private void recordFieldUsage(Set<String> annotations, String className, String fieldClass, String fieldName) {
        usages.add(new AnnotatedFieldReference(annotations, className, fieldClass, fieldName));
    }

    private void recordMethodUsage(Set<String> annotations, String className, String methodClass, String methodName, String descriptor) {
        usages.add(new AnnotatedMethodReference(annotations, className, methodClass, methodName, descriptor));
    }

    public static abstract class AnnotationUsage {
        private final Set<String> annotations;
        private final AnnotationUsageType type;
        private final String sourceClass;

        public AnnotationUsage(Set<String> annotations, AnnotationUsageType type, String sourceClass) {
            this.annotations = annotations;
            this.type = type;
            this.sourceClass = sourceClass;
        }

        AnnotationUsageType getType() {
            return type;
        }

        public <T extends AnnotationUsage> T as(Class<T> clazz) {
            return clazz.cast(this);
        }
    }

    public static class ExtendsAnnotatedClass extends AnnotationUsage {
        private final String superClass;

        protected ExtendsAnnotatedClass(Set<String> annotations, String clazz, String superClass) {
            super(annotations, CLASS_EXTENDS, clazz);
            this.superClass = superClass;
        }
    }

    public static class ImplementsAnnotatedInterface extends AnnotationUsage {
        private final String iface;
        public ImplementsAnnotatedInterface(Set<String> annotations, String clazz, String iface) {
            super(annotations, CLASS_IMPLEMENTS, clazz);
            this.iface = iface;

        }
    }

    public static class AnnotatedFieldReference extends AnnotationUsage {
        private final String fieldClass;
        private final String fieldName;

        public AnnotatedFieldReference(Set<String> annotations, String className, String fieldClass, String fieldName) {
            super(annotations, FIELD_REFERENCE, className);
            this.fieldClass = fieldClass;
            this.fieldName = fieldName;
        }
    }

    public static class AnnotatedMethodReference extends AnnotationUsage {
        private final String methodClass;
        private final String methodName;
        private final String descriptor;

        public AnnotatedMethodReference(Set<String> annotations, String className, String methodClass, String methodName, String descriptor) {
            super(annotations, METHOD_REFERENCE, className);
            this.methodClass = methodClass;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }
    }

    public enum AnnotationUsageType {
        CLASS_EXTENDS(ExtendsAnnotatedClass.class),
        CLASS_IMPLEMENTS(ImplementsAnnotatedInterface.class),
        METHOD_REFERENCE(AnnotatedMethodReference.class),
        FIELD_REFERENCE(AnnotatedFieldReference.class);

        private final Class<? extends AnnotationUsage> type;

        AnnotationUsageType(Class<? extends AnnotationUsage> type) {
            this.type = type;

        }
        
        public Class<? extends AnnotationUsage> getType() {
            return type;
        }
    }
}
