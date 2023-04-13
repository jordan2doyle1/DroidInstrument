package phd.research.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;
import soot.SootMethod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Jordan Doyle
 */

public class Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Filter.class);

    private static final List<String> PACKAGE_BLACKLIST = Filter.loadBlacklist("package_blacklist");
    private static final List<String> CLASS_BLACKLIST = Filter.loadBlacklist("class_blacklist");

    public static boolean isValidMethod(SootMethod method) {
        if (Filter.isValidClass(method.getDeclaringClass())) {
            return !method.getName().startsWith("access$");
        }
        return false;
    }

    public static boolean isValidClass(SootClass clazz) {
        if (!Filter.isValidPackage(clazz.getPackageName())) {
            return false;
        }

        if (clazz.getShortName().equals("R")) {
            return false;
        }

        return Filter.CLASS_BLACKLIST.stream()
                .noneMatch(blacklistedClass -> clazz.getShortName().startsWith(blacklistedClass));
    }

    private static boolean isValidPackage(String packageName) {
        return Filter.PACKAGE_BLACKLIST.stream().noneMatch(
                blacklistedPackage -> blacklistedPackage.startsWith(".") ? packageName.contains(blacklistedPackage) :
                        packageName.startsWith(blacklistedPackage));
    }

    private static List<String> loadBlacklist(String fileName) {
        LOGGER.info("Loading blacklist from resource file '" + fileName + "'");
        InputStream resourceStream = Filter.class.getClassLoader().getResourceAsStream(fileName);
        return resourceStream != null ?
                new BufferedReader(new InputStreamReader(resourceStream)).lines().collect(Collectors.toList()) :
                new ArrayList<>();
    }
}
