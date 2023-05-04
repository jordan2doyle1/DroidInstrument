package phd.research.singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author Jordan Doyle
 */

public class InstrumentSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentSettings.class);

    private static InstrumentSettings instance = null;

    private File androidPlatformDirectory;
    private File outputDirectory;
    private File apkFile;
    private boolean loggerActive;

    private InstrumentSettings() {
        this.loggerActive = true;
        this.androidPlatformDirectory = new File(System.getenv("ANDROID_HOME") + File.separator + "platforms");
        this.outputDirectory = new File(System.getProperty("user.dir") + File.separator + "output");
    }

    public static InstrumentSettings v() {
        if (instance == null) {
            instance = new InstrumentSettings();
        }
        return instance;
    }

    public void validate() throws IOException {
        this.loggerActive = false;
        setPlatformDirectory(this.androidPlatformDirectory);
        setOutputDirectory(this.outputDirectory);
        setApkFile(this.apkFile);
        this.loggerActive = true;
    }

    public File getApkFile() {
        return this.apkFile;
    }

    public void setApkFile(File apkFile) throws IOException {
        if (apkFile == null || !apkFile.isFile()) {
            throw new IOException("Apk file does not exist or is not a file (" + apkFile + ").");
        }

        this.apkFile = apkFile;

        if (this.loggerActive) {
            LOGGER.info("Apk file set as '" + apkFile.getAbsolutePath() + "'.");
        }
    }

    public File getPlatformDirectory() {
        return this.androidPlatformDirectory;
    }

    public void setPlatformDirectory(File androidPlatformDirectory) throws IOException {
        if (!androidPlatformDirectory.isDirectory()) {
            throw new IOException("Platform does not exist or is not a directory (" + androidPlatformDirectory + ").");
        }

        this.androidPlatformDirectory = androidPlatformDirectory;

        if (this.loggerActive) {
            LOGGER.info("Android platform directory set as '" + androidPlatformDirectory.getAbsolutePath() + "'");
        }
    }

    public File getOutputDirectory() {
        return this.outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) throws IOException {
        if (!outputDirectory.isDirectory()) {
            throw new IOException("Output directory does not exist or is not a directory (" + outputDirectory + ").");
        }

        this.outputDirectory = outputDirectory;

        if (this.loggerActive) {
            LOGGER.info("Output directory set as '" + outputDirectory.getAbsolutePath() + ".");
        }
    }
}
