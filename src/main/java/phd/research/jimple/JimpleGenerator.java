package phd.research.jimple;

import soot.*;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jordan Doyle
 */

public class JimpleGenerator {

    public static final String M_TAG = "<METHOD>";
    public static final String C_TAG = "<CONTROL>";

    private final JimpleBody body;
    private final List<Unit> units;

    public JimpleGenerator(JimpleBody body) {
        this.body = body;
        this.units = new ArrayList<>();
    }

    public List<Unit> getUnits() {
        return this.units;
    }

    public void generateInstrumentUnits() {
        String instrumentMessage = C_TAG + " Method: " + this.body.getMethod().getSignature() + " View: ";
        Value stringValue = StringConstant.v(instrumentMessage);

        Value printMessage;
        if (this.body.getMethod().getParameterCount() >= 1 &&
                this.body.getMethod().getParameterType(0).equals(RefType.v("android.view.View"))) {
            Local idLocal = this.generateGetId();
            printMessage = this.generateAppend(stringValue, idLocal);
        } else if (this.body.getMethod().getParameterCount() >= 1 &&
                this.body.getMethod().getParameterType(0).equals(RefType.v("android.view.MenuItem"))) {
            Local idLocal = this.generateGetItemId();
            printMessage = this.generateAppend(stringValue, idLocal);
        } else {
            printMessage = StringConstant.v(JimpleGenerator.M_TAG + " Method: " + this.body.getMethod().getSignature());
        }

        this.generatePrint(printMessage);
    }

    private void generatePrint(Value message) {
        Local printLocal = this.generateNewLocal(RefType.v("java.io.PrintStream"));
        SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
        Value sysOutStaticFieldRef = Jimple.v().newStaticFieldRef(sysOutField.makeRef());
        AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(printLocal, sysOutStaticFieldRef);
        this.units.add(sysOutAssignStmt);

        SootMethod printMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
        VirtualInvokeExpr printMethodExpr = Jimple.v().newVirtualInvokeExpr(printLocal, printMethod.makeRef(), message);
        InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(printMethodExpr);
        this.units.add(printlnMethodCallStmt);
    }

    private Local generateGetId() {
        Local paramLocal = this.body.getParameterLocal(0);
        SootMethod getIdMethod = Scene.v().grabMethod("<android.view.View: int getId()>");
        VirtualInvokeExpr idMethodCallExpr = Jimple.v().newVirtualInvokeExpr(paramLocal, getIdMethod.makeRef());
        Local idLocal = this.generateNewLocal(IntType.v());
        AssignStmt idAssignStmt = Jimple.v().newAssignStmt(idLocal, idMethodCallExpr);
        this.units.add(idAssignStmt);
        return idLocal;
    }

    private Local generateGetItemId() {
        Local paramLocal = this.body.getParameterLocal(0);
        SootMethod getIdMethod = Scene.v().grabMethod("<android.view.MenuItem: int getItemId()>");
        InterfaceInvokeExpr idMethodCallExpr = Jimple.v().newInterfaceInvokeExpr(paramLocal, getIdMethod.makeRef());
        Local idLocal = this.generateNewLocal(IntType.v());
        AssignStmt idAssignStmt = Jimple.v().newAssignStmt(idLocal, idMethodCallExpr);
        this.units.add(idAssignStmt);
        return idLocal;
    }

    private Local generateAppend(Value value1, Value value2) {
        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        Local appendedString = this.generateNewLocal(stringType);

        SootClass builderClass = Scene.v().getSootClass("java.lang.StringBuilder");
        RefType builderType = builderClass.getType();
        NewExpr newBuilderExpr = Jimple.v().newNewExpr(builderType);
        Local builderLocal = this.generateNewLocal(builderType);
        this.units.add(Jimple.v().newAssignStmt(builderLocal, newBuilderExpr));

        SootMethod initMethod = builderClass.getMethod("void <init>(java.lang.String)");
        SpecialInvokeExpr initBuilderExpr = Jimple.v().newSpecialInvokeExpr(builderLocal, initMethod.makeRef(), value1);
        InvokeStmt initBuilderStmt = Jimple.v().newInvokeStmt(initBuilderExpr);
        this.units.add(initBuilderStmt);

        SootMethodRef appendRef = builderClass.getMethod("java.lang.StringBuilder append(java.lang.String)").makeRef();
        VirtualInvokeExpr appendExpr =
                Jimple.v().newVirtualInvokeExpr(builderLocal, appendRef, generateToString(value2));
        Local tmpLocal = generateNewLocal(builderType);
        AssignStmt builderAppendStmt = Jimple.v().newAssignStmt(tmpLocal, appendExpr);
        this.units.add(builderAppendStmt);

        SootMethod toStringMethod = builderClass.getMethod("java.lang.String toString()");
        VirtualInvokeExpr builderToStringExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, toStringMethod.makeRef());
        AssignStmt builderToStringStmt = Jimple.v().newAssignStmt(appendedString, builderToStringExpr);
        this.units.add(builderToStringStmt);

        return appendedString;
    }

    private Local generateNewLocal(Type type) {
        LocalGenerator localGenerator = new DefaultLocalGenerator(this.body);
        return localGenerator.generateLocal(type);
    }

    private Value generateToString(Value value) {
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");
        if (value.getType().equals(stringClass.getType())) {
            return value;
        }

        Type type = value.getType();
        if (type instanceof PrimType) {
            Local tmpLocal = this.generateNewLocal(stringClass.getType());
            SootMethod valueOfMethod = stringClass.getMethod("java.lang.String valueOf(" + type + ")");
            StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
            AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, staticInvokeExpr);
            this.units.add(assignStmt);
            return tmpLocal;
        } else if (value instanceof Local) {
            Local base = (Local) value;
            SootClass objectClass = Scene.v().getSootClass("java.lang.Object");
            SootMethod toStrMethod = objectClass.getMethod("java.lang.String toString()");
            Local tmpLocal = this.generateNewLocal(stringClass.getType());
            VirtualInvokeExpr invokeExpr = Jimple.v().newVirtualInvokeExpr(base, toStrMethod.makeRef());
            AssignStmt assignStmt = Jimple.v().newAssignStmt(tmpLocal, invokeExpr);
            this.units.add(assignStmt);
            return tmpLocal;
        } else {
            throw new RuntimeException("Value " + value + " should be primitive or local but it's " + value.getType());
        }
    }
}
