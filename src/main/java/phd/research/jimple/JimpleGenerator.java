package phd.research.jimple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jordan Doyle
 */

public class JimpleGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JimpleGenerator.class);

    public static final String A_TAG = "<ACTIVITY>";
    public static final String F_TAG = "<FRAGMENT>";
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
        if (this.body.getMethod().getName().equals("onCreate")) {
            Value messageValue = StringConstant.v(A_TAG + " Activity: ");
            Local nameLocal = this.generateGetName();
            Value appendedMessage = this.generateAppend(messageValue, nameLocal);
            this.generatePrint(appendedMessage);
        }

        if (this.body.getMethod().getName().equals("onCreateView")) {
            this.generateFragmentLink();
        }

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

    private void generateFragmentLink() {
        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        Local appendedString = this.generateNewLocal(stringType);
        SootClass builderClass = Scene.v().getSootClass("java.lang.StringBuilder");
        RefType builderType = builderClass.getType();
        NewExpr newBuilderExpr = Jimple.v().newNewExpr(builderType);
        Local builderLocal = this.generateNewLocal(builderType);
        this.units.add(Jimple.v().newAssignStmt(builderLocal, newBuilderExpr));

        Value message = StringConstant.v(F_TAG + " Fragment: ");
        SootMethod initMethod = builderClass.getMethod("void <init>(java.lang.String)");
        SpecialInvokeExpr initExpr = Jimple.v().newSpecialInvokeExpr(builderLocal, initMethod.makeRef(), message);
        InvokeStmt initBuilderStmt = Jimple.v().newInvokeStmt(initExpr);
        this.units.add(initBuilderStmt);

        Local nameLocal = this.generateGetName();
        SootMethodRef appendRef = builderClass.getMethod("java.lang.StringBuilder append(java.lang.String)").makeRef();
        VirtualInvokeExpr appendExpr =
                Jimple.v().newVirtualInvokeExpr(builderLocal, appendRef, generateToString(nameLocal));
        Local tmpLocal = this.generateNewLocal(builderType);
        AssignStmt builderAppendStmt = Jimple.v().newAssignStmt(tmpLocal, appendExpr);
        this.units.add(builderAppendStmt);

        SootClass superClass = null;
        SootMethod getActivityMethod = null;
        int ifStmtIndex = 0;
        Local activityLocal = null;

        SootClass fragment = this.body.getMethod().getDeclaringClass();
        if (fragment.hasSuperclass()) {
            superClass = fragment.getSuperclass();
            outer:
                while (superClass.hasSuperclass()) {
                    for (SootMethod method : superClass.getMethods()) {
                        if (method.getName().equals("getActivity")) {
                            getActivityMethod = method;
                            break outer;
                        }
                    }
                    superClass = superClass.getSuperclass();
                }
        } else {
            LOGGER.warn("Fragment class '" + fragment.getName() + "' does not have superclass.");
        }

        if (getActivityMethod == null) {
            LOGGER.warn("No 'getActivity' method found for fragment '" + fragment.getName() + ".");
        }

        if (superClass != null && getActivityMethod != null) {
            Local thisLocal = this.body.getThisLocal();
            VirtualInvokeExpr getActivityMethodCallExpr =
                    Jimple.v().newVirtualInvokeExpr(thisLocal, getActivityMethod.makeRef());
            RefType activityType = superClass.getType();
            activityLocal = this.generateNewLocal(activityType);
            AssignStmt activityAssignStmt = Jimple.v().newAssignStmt(activityLocal, getActivityMethodCallExpr);
            this.units.add(activityAssignStmt);

            ifStmtIndex = this.units.size();

            Value messageAddition = StringConstant.v(" Activity: ");
            appendExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, appendRef, generateToString(messageAddition));
            tmpLocal = this.generateNewLocal(builderType);
            builderAppendStmt = Jimple.v().newAssignStmt(tmpLocal, appendExpr);
            this.units.add(builderAppendStmt);

            SootMethod getClassMethod = Scene.v().grabMethod("<java.lang.Object: java.lang.Class getClass()>");
            VirtualInvokeExpr classCallExpr = Jimple.v().newVirtualInvokeExpr(activityLocal, getClassMethod.makeRef());
            RefType classType = Scene.v().getSootClass("java.lang.Class").getType();
            Local classLocal = this.generateNewLocal(classType);
            AssignStmt classAssignStmt = Jimple.v().newAssignStmt(classLocal, classCallExpr);
            this.units.add(classAssignStmt);

            nameLocal = this.generateNewLocal(stringType);
            SootMethod getNameMethod = Scene.v().grabMethod("<java.lang.Class: java.lang.String getName()>");
            VirtualInvokeExpr nameMethodCallExpr = Jimple.v().newVirtualInvokeExpr(classLocal, getNameMethod.makeRef());
            AssignStmt nameAssignStmt = Jimple.v().newAssignStmt(nameLocal, nameMethodCallExpr);
            this.units.add(nameAssignStmt);

            appendExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, appendRef, generateToString(nameLocal));
            tmpLocal = this.generateNewLocal(builderType);
            builderAppendStmt = Jimple.v().newAssignStmt(tmpLocal, appendExpr);
            this.units.add(builderAppendStmt);
        }

        SootMethod toStringMethod = builderClass.getMethod("java.lang.String toString()");
        VirtualInvokeExpr builderToStringExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, toStringMethod.makeRef());
        AssignStmt builderToStringStmt = Jimple.v().newAssignStmt(appendedString, builderToStringExpr);
        this.units.add(builderToStringStmt);

        this.generatePrint(appendedString);

        if (superClass != null && getActivityMethod != null && activityLocal != null) {
            Value nullValue = NullConstant.v();
            EqExpr equalExpression = Jimple.v().newEqExpr(activityLocal, nullValue);
            IfStmt ifStatement = Jimple.v().newIfStmt(equalExpression, builderToStringStmt);
            units.add(ifStmtIndex, ifStatement);
        }
    }

    private Local generateGetName() {
        Local thisLocal = this.body.getThisLocal();
        SootMethod getClassMethod = Scene.v().grabMethod("<java.lang.Object: java.lang.Class getClass()>");
        VirtualInvokeExpr classMethodCallExpr = Jimple.v().newVirtualInvokeExpr(thisLocal, getClassMethod.makeRef());
        RefType classType = Scene.v().getSootClass("java.lang.Class").getType();
        Local classLocal = this.generateNewLocal(classType);
        AssignStmt classAssignStmt = Jimple.v().newAssignStmt(classLocal, classMethodCallExpr);
        this.units.add(classAssignStmt);

        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        Local nameLocal = this.generateNewLocal(stringType);
        SootMethod getNameMethod = Scene.v().grabMethod("<java.lang.Class: java.lang.String getName()>");
        VirtualInvokeExpr nameMethodCallExpr = Jimple.v().newVirtualInvokeExpr(classLocal, getNameMethod.makeRef());
        AssignStmt nameAssignStmt = Jimple.v().newAssignStmt(nameLocal, nameMethodCallExpr);
        this.units.add(nameAssignStmt);
        return nameLocal;
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
