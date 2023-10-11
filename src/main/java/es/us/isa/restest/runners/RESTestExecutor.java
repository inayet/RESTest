package es.us.isa.restest.runners;


import es.us.isa.restest.util.ClassLoader;

import es.us.isa.restest.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_EXECUTION;

public class RESTestExecutor {

    private static final Logger logger = LogManager.getLogger(RESTestExecutor.class.getName());

    RESTestLoader loader;

    public RESTestExecutor(RESTestLoader loader) {
        this.loader = loader;
    }

    public void execute() {
        // Cargar y ejecutar la clase de prueba
        Class<?> testClass = loadTestClass();
        runTests(testClass);
    }

    private Class<?> loadTestClass() {
        // Load test class
        String filePath = loader.targetDirJava + "/" + loader.testClassName + ".java";
        String className = loader.packageName + "." + loader.testClassName;
        logger.info("Compiling and loading test class {}.java", className);
        return ClassLoader.loadClass(filePath, className);
    }

    private void runTests(Class<?> testClass) {

        JUnitCore junit = new JUnitCore();
        //junit.addListener(new TextListener(System.out));
        junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
        Timer.startCounting(TEST_SUITE_EXECUTION);
        Result result = junit.run(testClass);
        Timer.stopCounting(TEST_SUITE_EXECUTION);
        int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
        logger.info("{} tests run in {} seconds. Successful: {}, Failures: {}, Ignored: {}", result.getRunCount(), result.getRunTime()/1000, successfulTests, result.getFailureCount(), result.getIgnoreCount());

    }

}