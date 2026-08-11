// scripts/asm-offsets/OffsetsDriver.java
// Bytecode-offset driver for scripts/bytecode.sh --offsets.
//
// Textifies a class file with ASM's TraceClassVisitor(Textifier) and names
// every label by its NUMERIC bytecode offset instead of Textifier's
// visit-order "L0/L1..." names, so instructions and jumps can be reasoned
// about at byte offset precision (the same precision javap -v uses for its
// "offset:" column).
//
// Modern ASM no longer stores the offset on Label.info (the field is
// writer-reserved), so the driver subclasses ClassReader and overrides the
// protected readLabel(int, Label[]) hook — every label the reader creates
// (label table, jump targets, try-catch, frames, debug) funnels through it —
// to record label -> bytecodeOffset. Textifier spawns a per-method printer
// via its protected createTextifier() factory, so the offset-naming
// Textifier subclass overrides both visitLabel and createTextifier.
//
// Compile once into the tools cache (scripts/bytecode.sh does this on
// demand); needs asm-9.10.1.jar + asm-util-9.10.1.jar on the classpath:
//   javac -cp asm.jar:asm-util.jar -d <tools-cache> OffsetsDriver.java
//
// Usage: java -cp <tools-cache>:asm.jar:asm-util.jar OffsetsDriver [-nodebug] <classfile>

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class OffsetsDriver {

    /** ClassReader that records the bytecode offset of every label it creates. */
    static final class OffsetReader extends ClassReader {
        final Map<Label, Integer> labelOffsets = new HashMap<>();

        OffsetReader(byte[] classFile) {
            super(classFile);
        }

        @Override
        protected Label readLabel(final int bytecodeOffset, final Label[] labels) {
            Label label = super.readLabel(bytecodeOffset, labels);
            labelOffsets.put(label, bytecodeOffset);
            return label;
        }
    }

    /** Textifier that names every label L%04X by its bytecode offset. */
    static final class OffsetTextifier extends Textifier {
        private final OffsetReader reader;

        OffsetTextifier(OffsetReader reader) {
            super(Opcodes.ASM9);
            this.reader = reader;
        }

        @Override
        protected Textifier createTextifier() {
            // Per-method printers (spawned by visitMethod) must share the naming.
            return new OffsetTextifier(reader);
        }

        @Override
        public void visitLabel(Label label) {
            nameIfKnown(label);
            super.visitLabel(label);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            // Exception-table labels are emitted before their visitLabel, so
            // pre-register the names or getLabelName falls back to visit-order.
            nameIfKnown(start);
            nameIfKnown(end);
            nameIfKnown(handler);
            super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            if (local != null) {
                for (Object o : local) {
                    if (o instanceof Label) {
                        nameIfKnown((Label) o);
                    }
                }
            }
            if (stack != null) {
                for (Object o : stack) {
                    if (o instanceof Label) {
                        nameIfKnown((Label) o);
                    }
                }
            }
            super.visitFrame(type, nLocal, local, nStack, stack);
        }

        private void nameIfKnown(Label label) {
            if (labelNames == null) {
                // Textifier 9.10.1 lazily creates labelNames in getLabelName;
                // our pre-registration runs before that on fresh method printers.
                labelNames = new HashMap<>();
            }
            Integer offset = reader.labelOffsets.get(label);
            if (offset != null) {
                labelNames.put(label, String.format("L%04X", offset));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean nodebug = false;
        String file = null;
        for (String a : args) {
            if (a.equals("-nodebug")) {
                nodebug = true;
            } else {
                file = a;
            }
        }
        if (file == null) {
            System.err.println("usage: OffsetsDriver [-nodebug] <classfile>");
            System.exit(1);
        }
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        OffsetReader reader = new OffsetReader(bytes);
        OffsetTextifier textifier = new OffsetTextifier(reader);
        int flags = nodebug ? ClassReader.SKIP_DEBUG : 0;
        reader.accept(new TraceClassVisitor(null, textifier, new PrintWriter(System.out, true)), flags);
    }
}
