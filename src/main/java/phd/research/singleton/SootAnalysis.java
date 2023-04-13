package phd.research.singleton;

import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.options.Options;

import java.util.Collections;

/**
 * @author Jordan Doyle
 */

public class SootAnalysis {

    private static SootAnalysis instance = null;

    private boolean sootInitialised;

    private SootAnalysis() {
        this.sootInitialised = false;
    }

    public static SootAnalysis v() {
        if (instance == null) {
            instance = new SootAnalysis();
        }
        return instance;
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSootInitialised() {
        return this.sootInitialised;
    }

    public void initialiseSoot() {
        G.reset();

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);

        // Read (APK Dex-to-Jimple) Options
        Options.v().set_android_jars(InstrumentSettings.v().getPlatformDirectory().getAbsolutePath());
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(InstrumentSettings.v().getApkFile().getAbsolutePath()));
        Options.v().set_process_multiple_dex(true);
        Options.v().set_include_all(true);

        // Write (APK Generation) Options
        Options.v().set_output_format(Options.output_format_dex);
        Options.v().set_output_dir(InstrumentSettings.v().getOutputDirectory().getAbsolutePath());

        // Resolve required classes
        Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();

        this.sootInitialised = true;
    }
}
