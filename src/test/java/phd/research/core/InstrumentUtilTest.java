package phd.research.core;

import org.junit.Before;
import org.junit.Test;
import soot.*;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;
import soot.options.Options;
import soot.util.HashChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jordan Doyle
 */

public class InstrumentUtilTest {

    private String androidPlatform;
    private String apk;
    private String outputDirectory;
    private List<Unit> units;
    private JimpleBody body;

    @Before
    public void setUp() {
        this.androidPlatform = System.getenv("ANDROID_HOME") + "/platforms/";
        this.apk = System.getProperty("user.dir") + "/ActivityLifecycle.apk";
        this.outputDirectory = System.getProperty("user.dir") + "/output/";

        InstrumentUtil.setupSoot(this.androidPlatform, this.apk, this.outputDirectory);

        this.units = new ArrayList<>();
        this.body = mock(JimpleBody.class);
        when(body.getLocals()).thenReturn(new HashChain<>());
    }

    @Test
    public void testSetupSoot() {
        assertTrue("Allow phantom references is false.", Options.v().allow_phantom_refs());
        assertTrue("Whole program mode is false.", Options.v().whole_program());
        assertTrue("Prepend classpath is false.", Options.v().prepend_classpath());
        assertTrue("Process multiple dex files is false.", Options.v().process_multiple_dex());
        assertTrue("Include all files is false.", Options.v().include_all());

        assertTrue("Basic class java.io.PrintStream not added.", Scene.v().isBasicClass("java.io.PrintStream"));
        assertTrue("Basic class java.lang.System not added.", Scene.v().isBasicClass("java.lang.System"));

        assertEquals("Source object should be APK.", Options.src_prec_apk, Options.v().src_prec());
        assertEquals("Output format should be dex", Options.output_format_dex, Options.v().output_format());
        assertEquals("Android jars not set properly.", this.androidPlatform, Options.v().android_jars());
        assertEquals("APK's not set properly.", Collections.singletonList(this.apk), Options.v().process_dir());
        assertEquals("Output directory not set properly.", this.outputDirectory, Options.v().output_dir());
    }

    @Test
    public void testGeneratePrintStatements() {
        InstrumentUtil.generatePrintStatements(this.body, StringConstant.v("This is a test."), this.units);

        assertEquals("Wrong number of generated units.", 2, this.units.size());
        assertEquals("Value of first unit is wrong.", "$r0 = <java.lang.System: java.io.PrintStream out>",
                this.units.get(0).toString()
                    );
        assertEquals("Value of second unit is wrong.",
                "virtualinvoke $r0.<java.io.PrintStream: void println(java.lang.String)>(\"This is a test.\")",
                this.units.get(1).toString()
                    );
    }

    @Test
    public void testGenerateGetIdStatements() {
        when(this.body.getParameterLocal(0)).thenReturn(Jimple.v().newLocal("v", RefType.v("android.view.View")));
        InstrumentUtil.generateGetIdStatements(this.body, this.units);

        assertEquals("Wrong number of generated units.", 1, this.units.size());
        assertEquals("Value of unit is wrong.", "$i0 = virtualinvoke v.<android.view.View: int getId()>()",
                this.units.get(0).toString()
                    );
    }

    @Test
    public void testAppendTwoValues() {
        InstrumentUtil.appendTwoValues(this.body, StringConstant.v("This is "), StringConstant.v("a test."),
                this.units
                                      );

        assertEquals("Wrong number of generated units.", 4, this.units.size());
        assertEquals("Value of first unit is wrong.", "$r1 = new java.lang.StringBuilder",
                this.units.get(0).toString()
                    );
        assertEquals("Value of second unit is wrong.",
                "specialinvoke $r1.<java.lang.StringBuilder: void <init>(java.lang.String)>(\"This is \")",
                this.units.get(1).toString()
                    );
        assertEquals("Value of third unit is wrong.",
                "$r2 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>" +
                        "(\"a test.\")", this.units.get(2).toString()
                    );
        assertEquals("Value of fourth unit is wrong.",
                "$r0 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.String toString()>()",
                this.units.get(3).toString()
                    );
    }

    @Test
    public void testAppendValueAndLocalPrimitive() {
        InstrumentUtil.appendTwoValues(this.body, StringConstant.v("This is a number "),
                Jimple.v().newLocal("number", IntType.v()), this.units
                                      );

        assertEquals("Wrong number of generated units.", 5, this.units.size());
        assertEquals("Value of first unit is wrong.", "$r1 = new java.lang.StringBuilder",
                this.units.get(0).toString()
                    );
        assertEquals("Value of second unit is wrong.",
                "specialinvoke $r1.<java.lang.StringBuilder: void <init>(java.lang.String)>(\"This is a number \")",
                this.units.get(1).toString()
                    );
        assertEquals("Value of third unit is wrong.",
                "$r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(number)",
                this.units.get(2).toString()
                    );
        assertEquals("Value of fourth unit is wrong.",
                "$r3 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>" +
                        "($r2)", this.units.get(3).toString()
                    );
        assertEquals("Value of fifth unit is wrong.",
                "$r0 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.String toString()>()",
                this.units.get(4).toString()
                    );
    }

    @Test
    public void testAppendValueAndLocalObject() {
        InstrumentUtil.appendTwoValues(this.body, StringConstant.v("This is a view "),
                Jimple.v().newLocal("view", RefType.v("android.view.View")), this.units
                                      );

        assertEquals("Wrong number of generated units.", 5, this.units.size());
        assertEquals("Value of first unit is wrong.", "$r1 = new java.lang.StringBuilder",
                this.units.get(0).toString()
                    );
        assertEquals("Value of second unit is wrong.",
                "specialinvoke $r1.<java.lang.StringBuilder: void <init>(java.lang.String)>(\"This is a view \")",
                this.units.get(1).toString()
                    );
        assertEquals("Value of third unit is wrong.",
                "$r2 = virtualinvoke view.<java.lang.Object: java.lang.String toString()>()",
                this.units.get(2).toString()
                    );
        assertEquals("Value of fourth unit is wrong.",
                "$r3 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>" +
                        "($r2)", this.units.get(3).toString()
                    );
        assertEquals("Value of fifth unit is wrong.",
                "$r0 = virtualinvoke $r1.<java.lang.StringBuilder: java.lang.String toString()>()",
                this.units.get(4).toString()
                    );
    }

    @Test(expected = RuntimeException.class)
    public void testAppendException() {
        SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
        InstrumentUtil.appendTwoValues(this.body, StringConstant.v("This is a print "),
                Jimple.v().newStaticFieldRef(sysOutField.makeRef()), this.units
                                      );
    }
}