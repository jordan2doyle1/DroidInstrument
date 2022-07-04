package phd.research;

import soot.*;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.*;
import soot.options.Options;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InstrumentUtil {
    public static final String C_TAG = "<COVERAGE_TEST>";
    public static final String I_TAG = "<CALLBACK_ID>";

    public static void setupSoot(String androidPlatform, String apk, String outputDirectory) {
        G.reset();
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);
        // Read (APK Dex-to-Jimple) Options
        Options.v().set_android_jars(androidPlatform);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(apk));
        Options.v().set_process_multiple_dex(true);
        Options.v().set_include_all(true);
        // Write (APK Generation) Options
        Options.v().set_output_format(Options.output_format_dex);
        Options.v().set_output_dir(outputDirectory);
        // Resolve required classes
        Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();
    }

    public static boolean isAndroidMethod(SootMethod sootMethod) {
        String classSignature = sootMethod.getDeclaringClass().getName();
        List<String> androidPrefixPkgNames =
                Arrays.asList("java.", "javax.", "sun.", "com.sun.", "com.ibm.", "org.xml.", "org.w3c.", "apple.awt.",
                        "com.apple.", "org.apache.", "org.eclipse.", "soot.", "android.", "com.google.android",
                        "androidx."
                             );
        return androidPrefixPkgNames.stream().anyMatch(classSignature::startsWith);
    }

    public static Local generateNewLocal(Body body, Type type) {
        LocalGenerator lg = new DefaultLocalGenerator(body);
        return lg.generateLocal(type);
    }

    public static void generatePrintStatements(JimpleBody body, Value message, List<Unit> generatedUnits) {
        Local printLocal = generateNewLocal(body, RefType.v("java.io.PrintStream"));
        SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
        Value sysOutStaticFieldRef = Jimple.v().newStaticFieldRef(sysOutField.makeRef());
        AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(printLocal, sysOutStaticFieldRef);
        generatedUnits.add(sysOutAssignStmt);

        SootMethod printlnMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
        VirtualInvokeExpr printlnMethodExpr =
                Jimple.v().newVirtualInvokeExpr(printLocal, printlnMethod.makeRef(), message);
        InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(printlnMethodExpr);
        generatedUnits.add(printlnMethodCallStmt);
    }

    public static Local generateGetIdStatements(JimpleBody body, List<Unit> generatedUnits) {
        Local paramLocal = body.getParameterLocal(0);
        SootMethod getIdMethod = Scene.v().grabMethod("<android.view.View: int getId()>");
        VirtualInvokeExpr idMethodCallExpr = Jimple.v().newVirtualInvokeExpr(paramLocal, getIdMethod.makeRef());
        Local idLocal = InstrumentUtil.generateNewLocal(body, IntType.v());
        AssignStmt idAssignStmt = Jimple.v().newAssignStmt(idLocal, idMethodCallExpr);
        generatedUnits.add(idAssignStmt);

        return idLocal;
    }

    public static Local appendTwoValues(Body body, Value value1, Value value2, List<Unit> generatedUnits) {
        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        Local appendedString = generateNewLocal(body, stringType);

        SootClass stringBuilderClass = Scene.v().getSootClass("java.lang.StringBuilder");
        RefType stringBuilderType = stringBuilderClass.getType();
        NewExpr newStringBuilderExpr = Jimple.v().newNewExpr(stringBuilderType);
        Local stringBuilderLocal = generateNewLocal(body, stringBuilderType);
        generatedUnits.add(Jimple.v().newAssignStmt(stringBuilderLocal, newStringBuilderExpr));

        SpecialInvokeExpr initStringBuilderExpr = Jimple.v().newSpecialInvokeExpr(stringBuilderLocal,
                stringBuilderClass.getMethod("void <init>(java.lang.String)").makeRef(), value1
                                                                                 );
        InvokeStmt initStringBuilderStmt = Jimple.v().newInvokeStmt(initStringBuilderExpr);
        generatedUnits.add(initStringBuilderStmt);

        VirtualInvokeExpr stringBuilderAppendExpr = Jimple.v().newVirtualInvokeExpr(stringBuilderLocal,
                stringBuilderClass.getMethod("java.lang.StringBuilder append(java.lang.String)").makeRef(),
                toString(body, value2, generatedUnits)
                                                                                   );
        Local tmpLocal = generateNewLocal(body, stringBuilderType);
        AssignStmt stringBuilderAppendStmt = Jimple.v().newAssignStmt(tmpLocal, stringBuilderAppendExpr);
        generatedUnits.add(stringBuilderAppendStmt);

        VirtualInvokeExpr stringBuilderToStringExpr = Jimple.v().newVirtualInvokeExpr(stringBuilderLocal,
                stringBuilderClass.getMethod("java.lang.String toString()").makeRef()
                                                                                     );
        AssignStmt stringBuilderToStringStmt = Jimple.v().newAssignStmt(appendedString, stringBuilderToStringExpr);
        generatedUnits.add(stringBuilderToStringStmt);

        return appendedString;
    }

    private static Value toString(Body b, Value value, List<Unit> generatedUnits) {
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");
        if (value.getType().equals(stringClass.getType())) {
            return value;
        }

        Type type = value.getType();
        if (type instanceof PrimType) {
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            StaticInvokeExpr staticInvokeExpr = Jimple.v()
                    .newStaticInvokeExpr(stringClass.getMethod("java.lang" + ".String valueOf(" + type + ")").makeRef(),
                            value
                                        );
            AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, staticInvokeExpr);
            generatedUnits.add(assignStmt);
            return tmpLocal;
        } else if (value instanceof Local) {
            Local base = (Local) value;
            SootMethod toStrMethod =
                    Scene.v().getSootClass("java.lang.Object").getMethod("java.lang.String toString" + "()");
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            VirtualInvokeExpr invokeExpr = Jimple.v().newVirtualInvokeExpr(base, toStrMethod.makeRef());
            AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, invokeExpr);
            generatedUnits.add(assignStmt);
            return tmpLocal;
        } else {
            throw new RuntimeException(
                    String.format("The value %s should be primitive or local but it's %s", value, value.getType()));
        }
    }
}
