package de.cbrachem.staticopenapi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

/**
 * Reads constant default values of Kotlin parameters from the bytecode.
 *
 * <p>Kotlin compiles defaults into a synthetic method or constructor. For each default it emits
 * {@code if (mask & bit != 0) parameter = <expression>}. This class reads the expression when it is one constant.
 */
final class KotlinDefaults {

    private static final Object NOT_CONSTANT = new Object();

    private record EnumConstant(String name) {}

    private KotlinDefaults() {}

    /** Returns the default values by parameter index. */
    static Map<Integer, Object> of(Executable executable) {
        var owner = executable.getDeclaringClass();
        var parameters = Type.getArgumentTypes(executable instanceof Constructor<?> constructor
                ? Type.getConstructorDescriptor(constructor)
                : Type.getMethodDescriptor((Method) executable));
        var syntheticName = executable instanceof Constructor ? "<init>" : executable.getName() + "$default";
        var syntheticDescriptor = executable instanceof Constructor
                ? descriptor(Type.VOID_TYPE, Stream.empty(), parameters, "kotlin/jvm/internal/DefaultConstructorMarker")
                : descriptor(Type.getReturnType((Method) executable), Stream.of(Type.getType(owner)), parameters, "java/lang/Object");

        var bySlot = new HashMap<Integer, Object>();
        var visitor = new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean isSynthetic = name.equals(syntheticName) && descriptor.equals(syntheticDescriptor);
                return isSynthetic ? new DefaultsVisitor(bySlot) : null;
            }
        };
        var resource = owner.getName().substring(owner.getName().lastIndexOf('.') + 1) + ".class";
        try (var bytecode = owner.getResourceAsStream(resource)) {
            if (bytecode == null) {
                return Map.of();
            }
            new ClassReader(bytecode).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var defaults = new HashMap<Integer, Object>();
        int slot = 1;
        for (int index = 0; index < parameters.length; index++) {
            var parameterType = executable.getParameterTypes()[index];
            int parameterIndex = index;
            Optional.ofNullable(bySlot.get(slot))
                    .flatMap(value -> convert(value, parameterType))
                    .ifPresent(value -> defaults.put(parameterIndex, value));
            slot += parameters[index].getSize();
        }
        return Map.copyOf(defaults);
    }

    private static String descriptor(Type returnType, Stream<Type> receiver, Type[] parameters, String markerType) {
        var arguments = Stream.of(receiver, Stream.of(parameters), Stream.of(Type.INT_TYPE, Type.getObjectType(markerType)))
                .flatMap(stream -> stream)
                .toArray(Type[]::new);
        return Type.getMethodDescriptor(returnType, arguments);
    }

    private static Optional<Object> convert(Object value, Class<?> parameterType) {
        if (value instanceof EnumConstant constant) {
            return Optional.of(constant.name()).filter(name -> parameterType.isEnum()).map(Object.class::cast);
        }
        if (parameterType == boolean.class || parameterType == Boolean.class) {
            return Optional.of(!value.equals(0));
        }
        if (parameterType == char.class || parameterType == Character.class) {
            return Optional.of(String.valueOf((char) (int) (Integer) value));
        }
        return Optional.of(value);
    }

    private static final class DefaultsVisitor extends MethodVisitor {

        private final Map<Integer, Object> bySlot;
        private boolean afterMaskTest;
        private List<Object> expression;

        DefaultsVisitor(Map<Integer, Object> bySlot) {
            super(SpringAsmInfo.ASM_VERSION);
            this.bySlot = bySlot;
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.IFEQ && afterMaskTest) {
                expression = new ArrayList<>();
            } else {
                push(NOT_CONSTANT);
            }
            afterMaskTest = false;
        }

        @Override
        public void visitVarInsn(int opcode, int slot) {
            boolean isStore = opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
            if (isStore && expression != null && expression.size() == 1 && expression.get(0) != NOT_CONSTANT) {
                bySlot.put(slot, expression.get(0));
            }
            if (isStore) {
                expression = null;
            } else {
                push(NOT_CONSTANT);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            afterMaskTest = opcode == Opcodes.IAND;
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                push(opcode - Opcodes.ICONST_0);
            } else if (opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1) {
                push((long) (opcode - Opcodes.LCONST_0));
            } else if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2) {
                push((float) (opcode - Opcodes.FCONST_0));
            } else if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1) {
                push((double) (opcode - Opcodes.DCONST_0));
            } else {
                push(NOT_CONSTANT);
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            push(opcode == Opcodes.NEWARRAY ? NOT_CONSTANT : operand);
        }

        @Override
        public void visitLdcInsn(Object value) {
            push(value instanceof Number || value instanceof String ? value : NOT_CONSTANT);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            boolean isOwnConstant = opcode == Opcodes.GETSTATIC && descriptor.equals("L" + owner + ";");
            push(isOwnConstant ? new EnumConstant(name) : NOT_CONSTANT);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            boolean isBoxing = name.equals("valueOf") && owner.startsWith("java/lang/");
            if (!isBoxing) {
                push(NOT_CONSTANT);
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            push(NOT_CONSTANT);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
            push(NOT_CONSTANT);
        }

        private void push(Object value) {
            if (expression != null) {
                expression.add(value);
            }
        }
    }
}
