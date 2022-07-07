package phd.research.core;

import org.junit.Test;
import phd.research.core.InstrumentUtil;
import soot.SootClass;
import soot.SootMethod;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jordan Doyle
 */

public class InstrumentUtilWithoutSootTest {

    @Test
    public void testIsAndroidMethod() {
        List<String> androidClasses =
                Arrays.asList("java.test", "javax.test", "sun.test", "com.sun.test", "com.ibm.test", "org.xml.test",
                        "org.w3c.test", "apple.awt.test", "com.apple.test", "org.apache.test", "org.eclipse.test",
                        "soot.test", "android.test", "com.google.android.test", "androidx.test"
                             );

        SootClass testClass = mock(SootClass.class);
        SootMethod testMethod = mock(SootMethod.class);
        when(testMethod.getDeclaringClass()).thenReturn(testClass);

        for (String androidClass : androidClasses) {
            when(testClass.getName()).thenReturn(androidClass);
            assertTrue(androidClass + " should be an Android class.", InstrumentUtil.isAndroidMethod(testMethod));
        }

        when(testClass.getName()).thenReturn("com.example.test");
        assertFalse("com.test should not be an Android class.", InstrumentUtil.isAndroidMethod(testMethod));
    }

}