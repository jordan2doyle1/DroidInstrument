package phd.research;

import soot.*;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.*;
import soot.options.Options;

import java.util.ArrayList;
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
        Options.v().set_validate(true);
        // Resolve required classes
        Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();
    }

    public static boolean isAndroidMethod(SootMethod sootMethod) {
        String classSignature = sootMethod.getDeclaringClass().getName();
        List<String> androidPrefixPkgNames = Arrays.asList("android.", "com.google.android", "androidx.");
        return androidPrefixPkgNames.stream().map(classSignature::startsWith).reduce(false, (res, curr) -> res || curr);
    }

    public static List<Unit> generateLogStatements(JimpleBody b, String msg) {
        return generateLogStatements(b, msg, null);
    }

    public static List<Unit> generateLogStatements(JimpleBody b, String msg, Value value) {
        List<Unit> generated = new ArrayList<>();
        Value logMessage = StringConstant.v(msg);
        Value logType = StringConstant.v(C_TAG);
        Value logMsg = logMessage;
        if (value != null) logMsg = InstrumentUtil.appendTwoStrings(b, logMessage, value, generated);
        SootMethod sm = Scene.v().getMethod("<android.util.Log: int i(java.lang.String,java.lang.String)>");
        StaticInvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(sm.makeRef(), logType, logMsg);
        generated.add(Jimple.v().newInvokeStmt(invokeExpr));
        return generated;
    }

    private static Local appendTwoStrings(Body b, Value s1, Value s2, List<Unit> generated) {
        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        SootClass builderClass = Scene.v().getSootClass("java.lang.StringBuilder");
        RefType builderType = builderClass.getType();
        NewExpr newBuilderExpr = Jimple.v().newNewExpr(builderType);
        Local builderLocal = generateNewLocal(b, builderType);
        generated.add(Jimple.v().newAssignStmt(builderLocal, newBuilderExpr));
        Local tmpLocal = generateNewLocal(b, builderType);
        Local resultLocal = generateNewLocal(b, stringType);

        VirtualInvokeExpr appendExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, builderClass.getMethod("java.lang.StringBuilder append(java.lang.String)").makeRef(), toString(b, s2, generated));
        VirtualInvokeExpr toStrExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, builderClass.getMethod("java.lang.String toString()").makeRef());

        generated.add(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(builderLocal, builderClass.getMethod("void <init>(java.lang.String)").makeRef(), s1)));
        generated.add(Jimple.v().newAssignStmt(tmpLocal, appendExpr));
        generated.add(Jimple.v().newAssignStmt(resultLocal, toStrExpr));

        return resultLocal;
    }

    public static Value toString(Body b, Value value, List<Unit> generated) {
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");
        if (value.getType().equals(stringClass.getType())) return value;
        Type type = value.getType();

        if (type instanceof PrimType) {
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            generated.add(Jimple.v().newAssignStmt(tmpLocal, Jimple.v().newStaticInvokeExpr(stringClass.getMethod("java.lang.String valueOf(" + type + ")").makeRef(), value)));
            return tmpLocal;
        } else if (value instanceof Local) {
            Local base = (Local) value;
            SootMethod toStrMethod = Scene.v().getSootClass("java.lang.Object").getMethod("java.lang.String toString()");
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            generated.add(Jimple.v().newAssignStmt(tmpLocal, Jimple.v().newVirtualInvokeExpr(base, toStrMethod.makeRef())));
            return tmpLocal;
        } else {
            throw new RuntimeException(String.format("The value %s should be primitive or local but it's %s", value, value.getType()));
        }
    }

    public static Local generateNewLocal(Body body, Type type) {
        LocalGenerator lg = new DefaultLocalGenerator(body);
        return lg.generateLocal(type);
    }
}
