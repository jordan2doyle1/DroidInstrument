package phd.research.main;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import phd.research.core.InstrumentUtil;
import phd.research.Timer;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jordan Doyle
 */

public class FrameworkMain {
    private static final Logger logger = LoggerFactory.getLogger(FrameworkMain.class);

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("a").longOpt("apk").required().hasArg().numberOfArgs(1).argName("FILE")
                .desc("APK file to analyse.").build());
        options.addOption(Option.builder("p").longOpt("android-platform").hasArg().numberOfArgs(1).argName("DIRECTORY")
                .desc("Android SDK platform directory.").build());
        options.addOption(Option.builder("o").longOpt("output-directory").hasArg().numberOfArgs(1).argName("DIRECTORY")
                .desc("Directory for output files.").build());
        options.addOption(Option.builder("c").longOpt("clean-directory").desc("Clean output directory.").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Display help.").build());

        CommandLine cmd = null;
        try {
            CommandLineParser parser = new DefaultParser();
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            final PrintWriter writer = new PrintWriter(System.out);
            formatter.printUsage(writer, 80, "DroidInstrument", options);
            writer.flush();
            System.exit(0);
        }

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("DroidInstrument", options);
            System.exit(0);
        }

        phd.research.Timer timer = new Timer();
        logger.info("Start time: " + timer.start());

        File apk = new File((cmd.hasOption("a") ? cmd.getOptionValue("a") : ""));
        if (!apk.exists()) {
            logger.error("Error: APK file does not exist (" + apk + ").");
            System.exit(20);
        }

        File androidPlatform = new File((cmd.hasOption("p") ? cmd.getOptionValue("p") : System.getenv("ANDROID_HOME") + "/platforms/"));
        if (!androidPlatform.isDirectory()) {
            logger.error("Error: Android platform directory does not exist (" + androidPlatform + ").");
            System.exit(10);
        }

        File outputDirectory = new File((cmd.hasOption("o") ? cmd.getOptionValue("o") : System.getProperty("user.dir") + "/output/"));
        if (cmd.hasOption("o") && !outputDirectory.isDirectory()) {
            logger.warn("Output directory doesn't exist, using default directory instead.");
            outputDirectory = new File(System.getProperty("user.dir") + "/output/");
        }
        if (!outputDirectory.isDirectory()) {
            if (!outputDirectory.mkdir()) {
                logger.error("Output directory does not exist.");
                System.exit(10);
            }
        }

        if (cmd.hasOption("c")) {
            try {
                FileUtils.cleanDirectory(outputDirectory);
            } catch (IOException e) {
                logger.error("Error cleaning output directory: " + e.getMessage());
            }
        }

        logger.info("Processing: " + apk);
        InstrumentUtil.setupSoot(androidPlatform, apk, outputDirectory);

        PackManager.v().getPack("jtp").add(new Transform("jtp.instrument", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                if (InstrumentUtil.isAndroidMethod(b.getMethod())) {
                    return;
                }

                JimpleBody body = (JimpleBody) b;
                UnitPatchingChain units = b.getUnits();
                List<Unit> generatedUnits = new ArrayList<>();

                Value printMessage;
                if (body.getMethod().getParameterCount() >= 1 &&
                        body.getMethod().getParameterType(0).equals(RefType.v("android.view.View"))) {
                    Local idLocal = InstrumentUtil.generateGetIdStatements(body, generatedUnits);
                    Value stringValue = StringConstant.v(
                            String.format("%s%s Method: %s View: ", InstrumentUtil.C_TAG, InstrumentUtil.I_TAG,
                                    body.getMethod().getSignature()
                                         ));
                    printMessage = InstrumentUtil.appendTwoValues(body, stringValue, idLocal, generatedUnits);
                } else {
                    printMessage = StringConstant.v(
                            String.format("%s Method: %s", InstrumentUtil.C_TAG, body.getMethod().getSignature()));
                }

                InstrumentUtil.generatePrintStatements(body, printMessage, generatedUnits);
                units.insertBefore(generatedUnits, body.getFirstNonIdentityStmt());
                b.validate();
            }
        }));

        PackManager.v().runPacks();

        try {
            PackManager.v().writeOutput();
        } catch (RuntimeException e) {
            logger.error("Problem writing instrumented code to APK (" + apk + "): ");
            e.printStackTrace();
        }

        logger.info("End time: " + timer.end());
        logger.info("Execution time: " + timer.secondsDuration() + " second(s).");
    }
}