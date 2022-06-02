package phd.research;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;

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

        Options options = new Options();
        options.addOption(Option.builder("a").longOpt("apk").required().hasArg().numberOfArgs(1).argName("FILE")
                .desc("APK file to analyse.").build());
        options.addOption(Option.builder("p").longOpt("android-platform").hasArg().numberOfArgs(1).argName("DIRECTORY")
                .desc("Android SDK platform directory.").build());
        options.addOption(Option.builder("o").longOpt("output-directory").hasArg().numberOfArgs(1).argName("DIRECTORY")
                .desc("Directory for output files.").build());
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
            androidPlatform =
                    (cmd.hasOption("p") ? cmd.getOptionValue("p") : System.getenv("ANDROID_HOME") + "/platforms/");
            if (!directoryExists(androidPlatform)) {
                logger.error("Error: Android platform directory does not exist (" + androidPlatform + ").");
                System.exit(10);
            }

            apk = cmd.getOptionValue("a");
            if (!fileExists(apk)) {
                logger.error("Error: APK file does not exist (" + apk + ").");
                System.exit(30);
            }

            outputDirectory =
                    (cmd.hasOption("o") ? cmd.getOptionValue("o") : System.getProperty("user.dir") + "/output/");
            if (!directoryExists(outputDirectory)) {
                outputDirectory = System.getProperty("user.dir") + "/output/";
                if (createDirectory(outputDirectory)) {
                    if (cmd.hasOption("o")) {
                        logger.warn("Warning: Output directory doesn't exist, using default directory instead.");
                    }
                } else {
                    logger.error("Error: Output directory does not exist.");
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
        PackManager.v().writeOutput();

        LocalDateTime endDate = LocalDateTime.now();
        logger.info("End time: " + dateFormatter.format(endDate));
        Duration duration = Duration.between(startDate, endDate);
        logger.info("Execution time: " + duration.getSeconds() + " second(s).");
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
        }

        if (cmd != null) {
            return cmd.hasOption("h");
        }

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