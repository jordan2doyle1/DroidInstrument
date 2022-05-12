package phd.research;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jordan Doyle
 */
public class FrameworkMain {
    private static final Logger logger = LoggerFactory.getLogger(FrameworkMain.class);
    private static String androidPlatform;
    private static String apk;
    private static String outputDirectory;

    public static void main(String[] args) {
        LocalDateTime startDate = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm:ss");
        logger.info("Start time: " + dateFormatter.format(startDate));
        System.out.println("Start time: " + dateFormatter.format(startDate));

        Options options = new Options();
        options.addOption(Option.builder("ap").longOpt("android-platform").desc("Android SDK platform directory.").required().hasArg().numberOfArgs(1).argName("DIRECTORY").build());
        options.addOption(Option.builder("a").longOpt("apk").desc("APK file to analyse.").required().hasArg().numberOfArgs(1).argName("FILE").build());
        options.addOption(Option.builder("od").longOpt("output-directory").desc("Directory for output files.").hasArg().numberOfArgs(1).argName("DIRECTORY").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Display help.").build());

        CommandLine cmd = null;
        try {
            if (checkForHelp(args)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("DroidInstrument", options);
                System.exit(0);
            }

            CommandLineParser parser = new DefaultParser();
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            final PrintWriter writer = new PrintWriter(System.out);
            formatter.printUsage(writer, 80, "DroidInstrument", options);
            writer.flush();
            System.exit(0);
        }

        if (cmd != null) {
            androidPlatform = cmd.getOptionValue("ap");
            if (!directoryExists(androidPlatform)) {
                logger.error("Error: Android platform directory does not exist (" + androidPlatform + ").");
                System.err.println("Error: Android platform directory does not exist (" + androidPlatform + ").");
                System.exit(10);
            }

            apk = cmd.getOptionValue("a");
            if (!fileExists(apk)) {
                logger.error("Error: APK file does not exist (" + apk + ").");
                System.err.println("Error: APK file does not exist (" + apk + ").");
                System.exit(30);
            }

            outputDirectory = (cmd.hasOption("od") ? cmd.getOptionValue("od") : System.getProperty("user.dir") + "/output/");
            if (!directoryExists(outputDirectory)) {
                outputDirectory = System.getProperty("user.dir") + "/output/";
                if (createDirectory(outputDirectory)) {
                    if (cmd.hasOption("od")) {
                        logger.warn("Warning: Output directory doesn't exist, using default directory instead.");
                        System.err.println("Warning: Output directory doesn't exist, using default directory instead.");
                    }
                } else {
                    logger.error("Error: Output directory does not exist.");
                    System.err.println("Error: Output directory does not exist.");
                }
            }
        }

        try {
            FileUtils.cleanDirectory(new File(outputDirectory));
        } catch (IOException e) {
            logger.error("Error cleaning output directory: " + e.getMessage());
        }

        InstrumentUtil.setupSoot(androidPlatform, apk, outputDirectory);

        PackManager.v().getPack("jtp").add(new Transform("jtp.instrument", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                if (InstrumentUtil.isAndroidMethod(b.getMethod())) return;

                JimpleBody body = (JimpleBody) b;
                UnitPatchingChain units = b.getUnits();

                List<Unit> generatedUnits = new ArrayList<>();
                // The message that we want to log
                String content = String.format("%s Executing Method: %s", InstrumentUtil.TAG, body.getMethod().getSignature());
                // In order to call "System.out.println" we need to create a local containing "System.out" value
                Local psLocal = InstrumentUtil.generateNewLocal(body, RefType.v("java.io.PrintStream"));

                // Now we assign "System.out" to psLocal
                SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
                AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(psLocal, Jimple.v().newStaticFieldRef(sysOutField.makeRef()));
                generatedUnits.add(sysOutAssignStmt);

                // Create println method call and provide its parameter
                SootMethod printlnMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
                Value printlnParameter = StringConstant.v(content);
                InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod.makeRef(), printlnParameter));
                generatedUnits.add(printlnMethodCallStmt);
                // Insert the generated statement before the first  non-identity stmt
                units.insertBefore(generatedUnits, body.getFirstNonIdentityStmt());
                // Validate the body to ensure that our code injection does not introduce any problem (at least statically)
                b.validate();
            }
        }));

        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();

        LocalDateTime endDate = LocalDateTime.now();
        logger.info("End time: " + dateFormatter.format(endDate));
        System.out.println("End time: " + dateFormatter.format(endDate));
        Duration duration = Duration.between(startDate, endDate);
        logger.info("Execution time: " + duration.getSeconds() + " second(s).");
        System.out.println("Execution time: " + duration.getSeconds() + " second(s).");
    }

    private static boolean checkForHelp(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("h").longOpt("help").desc("Display help.").build());

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args, true);
        } catch (ParseException e) {
            logger.error("Error Parsing Command Line Arguments: " + e.getMessage());
            System.err.println("Error Parsing Command Line Arguments: " + e.getMessage());
        }

        if (cmd != null) return cmd.hasOption("h");

        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean directoryExists(String directoryName) {
        File directory = new File(directoryName);
        return directory.isDirectory();
    }

    private static boolean createDirectory(String directoryName) {
        File directory = new File(directoryName);
        return directory.mkdir();
    }

    private static boolean fileExists(String fileName) {
        File file = new File(fileName);
        return file.exists();
    }
}