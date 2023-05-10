package phd.research.main;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import phd.research.Timer;
import phd.research.jimple.JimpleGenerator;
import phd.research.singleton.InstrumentSettings;
import phd.research.singleton.SootAnalysis;
import phd.research.utility.Filter;
import soot.*;
import soot.jimple.JimpleBody;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * @author Jordan Doyle
 */

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

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
            System.exit(10);
        }

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("DroidInstrument", options);
            System.exit(0);
        }

        Timer timer = new Timer();
        LOGGER.info("Start time: " + timer.start());

        InstrumentSettings settings = InstrumentSettings.v();
        try {
            settings.setApkFile(new File(cmd.getOptionValue("a")));
        } catch (IOException e) {
            LOGGER.error("Files missing: " + e.getMessage());
            System.exit(20);
        }

        if (cmd.hasOption("p")) {
            try {
                settings.setPlatformDirectory(new File(cmd.getOptionValue("p")));
            } catch (IOException e) {
                LOGGER.error("Files missing: " + e.getMessage());
                System.exit(30);
            }
        }

        if (cmd.hasOption("o")) {
            try {
                settings.setOutputDirectory(new File(cmd.getOptionValue("o")));
            } catch (IOException e) {
                LOGGER.error("Files missing: " + e.getMessage());
                System.exit(40);
            }
        }

        try {
            settings.validate();
        } catch (IOException e) {
            LOGGER.error("Files missing: " + e.getMessage());
            System.exit(50);
        }

        if (cmd.hasOption("c")) {
            try {
                FileUtils.cleanDirectory(settings.getOutputDirectory());
            } catch (IOException e) {
                LOGGER.error("Failed to clean output directory." + e.getMessage());
            }
        }

        LOGGER.info("Processing: " + InstrumentSettings.v().getApkFile());
        if (!SootAnalysis.v().isSootInitialised()) {
            SootAnalysis.v().initialiseSoot();
        }

        PackManager.v().getPack("jtp").add(new Transform("jtp.instrument", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                JimpleBody body = (JimpleBody) b;
                if (!Filter.isValidMethod(body.getMethod())) {
                    return;
                }

                LOGGER.debug("Instrumenting " + body.getMethod().getSignature());
                JimpleGenerator jimpleGenerator = new JimpleGenerator(body);
                jimpleGenerator.generateInstrumentUnits();
                List<Unit> units = jimpleGenerator.getUnits();
                body.getUnits().insertBefore(units, body.getFirstNonIdentityStmt());
                body.validate();
            }
        }));

        PackManager.v().runPacks();

        try {
            PackManager.v().writeOutput();
        } catch (RuntimeException e) {
            LOGGER.error("Problem writing instrumented code to APK (" + InstrumentSettings.v().getApkFile() + "): " +
                    e.getMessage(), e);
            System.exit(60);
        }

        LOGGER.info("End time: " + timer.end());
        LOGGER.info("Execution time: " + timer.secondsDuration() + " second(s).");
    }
}