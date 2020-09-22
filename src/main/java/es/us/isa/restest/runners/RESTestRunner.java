package es.us.isa.restest.runners;

import java.util.Collection;

import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.*;
import es.us.isa.restest.util.ClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.IWriter;

import static es.us.isa.restest.util.Timer.TestStep.*;

/**
 * This class a basic test workflow: test generation -> test writing -> class compilation and loading -> test execution -> test report generation -> test coverage report generation
 * @author Sergio Segura
 *
 */
public class RESTestRunner {

	private String targetDir;							// Directory where tests will be generated
	private String testClassName;						// Name of the class to be generated
	private String packageName;							// Package name
	private AbstractTestCaseGenerator generator;   		// Test case generator
	private RESTAssuredWriter writer;								// RESTAssured writer
	private AllureReportManager allureReportManager;	// Allure report manager
	private StatsReportManager statsReportManager;		// Stats report manager
	private int numTestCases = 0;						// Number of test cases generated so far
	private static final Logger logger = LogManager.getLogger(RESTestRunner.class.getName());

	public RESTestRunner(String testClassName, String targetDir, String packageName, AbstractTestCaseGenerator generator, RESTAssuredWriter writer, AllureReportManager reportManager, StatsReportManager statsReportManager) {
		this.targetDir = targetDir;
		this.packageName = packageName;
		this.testClassName = testClassName;
		this.generator = generator;
		this.writer = writer;
		this.allureReportManager = reportManager;
		this.statsReportManager = statsReportManager;
	}

	public void run(Collection<TestCase> testCases) {
		logger.info("Exporting test cases coverage to CSV");
		String csvTcPath = statsReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.file");
		testCases.forEach(tc -> tc.exportToCSV(csvTcPath));
		statsReportManager.getCoverageMeter().setTestSuite(testCases);

		// Write test cases
		String filePath = targetDir + "/" + testClassName + ".java";
		logger.info("Writing {} test cases to test class {}", testCases.size(), filePath);
		writer.write(testCases);

		// Test execution
		logger.info("Running tests");
		System.setProperty("allure.results.directory", allureReportManager.getResultsDirPath());
		testExecution(getTestClass());

		// Report generation
		generateReports();
	}
	
	public void run() {

		// Test generation and writing (RESTAssured)
		testGeneration();

		// Test execution
		logger.info("Running tests");
		System.setProperty("allure.results.directory", allureReportManager.getResultsDirPath());
		testExecution(getTestClass());

		// Print number of faulty and nominal test cases
		logger.info("Nominal test cases generated: {}", generator.getnNominal());
		logger.info("Faulty test cases generated: {}", generator.getnFaulty());


		if (statsReportManager.getEnableCSVStats()) {
			logger.info("Exporting number of faulty and nominal test cases to CSV");
			String csvNFPath = statsReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.nominalfaulty.file");
			generator.exportNominalFaultyToCSV(csvNFPath, testClassName);
		}

		// Report generation
		generateReports();
	}

	private void generateReports() {
		// Generate test report
		logger.info("Generating test report");
		allureReportManager.generateReport();

		// Generate coverage report
		logger.info("Generating coverage report");
		statsReportManager.generateReport();
	}

	private Class<?> getTestClass() {
		// Load test class
		String filePath = targetDir + "/" + testClassName + ".java";
		String className = packageName + "." + testClassName;
		logger.info("Compiling and loading test class {}.java", className);
		return ClassLoader.loadClass(filePath, className);
	}

	private void testGeneration() {
	    
		// Generate test cases
		logger.info("Generating tests");
		generator.setnCurrentFaulty(0);
		generator.setnCurrentNominal(0);
		Timer.startCounting(TEST_SUITE_GENERATION);
		Collection<TestCase> testCases = generator.generate();
		Timer.stopCounting(TEST_SUITE_GENERATION);
        this.numTestCases += testCases.size();

        // Export test cases and nFaulty and nNominal to CSV if enableStats is true
		if (statsReportManager.getEnableCSVStats()) {
			logger.info("Exporting test cases coverage to CSV");
			String csvTcPath = statsReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.file");
			testCases.forEach(tc -> tc.exportToCSV(csvTcPath));

//			// Generate input coverage data if enableStats and enableInputCoverage is true.
//			if(statsReportManager.getEnableInputCoverage()) {
//				String csvTcCoveragePath = statsReportManager.getCoverageDataDir() + "/" + PropertyManager.readProperty("data.coverage.testcases.file");
//				testCases.forEach(tc -> CoverageMeter.exportCoverageOfTestCaseToCSV(csvTcCoveragePath, tc));
//			}
		}

      // Update CoverageMeter with recently created test suite (if coverage is enabled).
		if (statsReportManager.getEnableInputCoverage() || statsReportManager.getEnableOutputCoverage()) {
			statsReportManager.getCoverageMeter().addTestSuite(testCases);
		}
        
        // Write test cases
        String filePath = targetDir + "/" + testClassName + ".java";
        logger.info("Writing {} test cases to test class {}", testCases.size(), filePath);
        writer.write(testCases);

	}

	private void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		Timer.startCounting(TEST_SUITE_EXECUTION);
		Result result = junit.run(testClass);
		Timer.stopCounting(TEST_SUITE_EXECUTION);
		int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
		logger.info("{} tests run in {} seconds. Successful: {}, Failures: {}, Ignored: {}", result.getRunCount(), result.getRunTime()/1000, successfulTests, result.getFailureCount(), result.getIgnoreCount());

	}
	
	public String getTargetDir() {
		return targetDir;
	}
	
	public void setTargetDir(String targetDir) {
		this.targetDir = targetDir;
	}
	
	public String getTestClassName() {
		return testClassName;
	}
	
	public void setTestClassName(String testClassName) {
		this.testClassName = testClassName;
	}

	public int getNumTestCases() {
		return numTestCases;
	}
	
	public void resetNumTestCases() {
		this.numTestCases=0;
	}
}
