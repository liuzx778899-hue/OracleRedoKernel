import java.lang.instrument.*;
import java.security.*;
import java.nio.file.*;
import java.util.HexFormat;
import jdk.internal.org.objectweb.asm.*;

/** Lab-only agent adds blocking calls immediately after the two existing fsync instructions. */
public class OracleCheckpointAgent {
    public static void premain(String ignored,Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] original) {
                if (!name.equals("com/streamhouse/engines/oracle/OracleConsumerCheckpoint")) return null;
                var reader=new ClassReader(original);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8,writer) {
                    public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                        MethodVisitor delegate=super.visitMethod(access,method,descriptor,signature,exceptions);
                        if (!method.equals("persist")) return delegate;
                        return new MethodVisitor(Opcodes.ASM8,delegate) {
                            int forces;
                            public void visitMethodInsn(int opcode,String owner,String call,String descriptor,boolean iface) {
                                super.visitMethodInsn(opcode,owner,call,descriptor,iface);
                                if (owner.equals("java/nio/channels/FileChannel") && call.equals("force")) {
                                    visitVarInsn(Opcodes.ALOAD,0);
                                    visitLdcInsn(++forces==1?"checkpoint-temp":"checkpoint-forced");
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"OracleCheckpointPause","pause","(Ljava/nio/file/Path;Ljava/lang/String;)V",false);
                                }
                            }
                        };
                    }
                },0);
                byte[] changed=writer.toByteArray();
                try {
                    var sha=MessageDigest.getInstance("SHA-256");
                    Files.writeString(Path.of(System.getProperty("labTransform")),HexFormat.of().formatHex(sha.digest(original))+"\n"+HexFormat.of().formatHex(sha.digest(changed))+"\n");
                } catch (Exception e) {throw new IllegalStateException(e);}
                return changed;
            }
        });
    }
}
