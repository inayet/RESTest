package es.us.isa.rester.configuration.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.Auth;
import es.us.isa.rester.configuration.pojos.GenParameter;
import es.us.isa.rester.configuration.pojos.Generator;
import es.us.isa.rester.configuration.pojos.HeaderParam;
import es.us.isa.rester.configuration.pojos.Operation;
import es.us.isa.rester.configuration.pojos.QueryParam;
import es.us.isa.rester.configuration.pojos.TestConfiguration;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.configuration.pojos.TestParameter;
import es.us.isa.rester.configuration.pojos.TestPath;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.util.TestConfigurationFilter;
import io.swagger.models.HttpMethod;
import io.swagger.models.Path;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.AbstractSerializableParameter;

public class DefaultTestConfigurationGenerator {

	
	/**
	 * Generate a default test configuration file for a given Open API specification
	 * @param spec Open API specification
	 * @param destination Path of the output test configuration file
	 * @param filters Set the paths and HTTP methods to be included in the test configuration file
	 * @return
	 */
	public TestConfigurationObject generate (OpenAPISpecification spec, String destination, Collection<TestConfigurationFilter> filters) {
		
		TestConfigurationObject conf = new TestConfigurationObject();
		
		// Authentication configuration (not required by default)
		conf.setAuth(generateDefaultAuthentication());
		// TODO: Read authentication settings from specification (https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securitySchemeObject)
		
		// Paths
		TestConfiguration testConf = new TestConfiguration();
		testConf.setTestPaths(generatePaths(spec,filters));
			
		conf.setTestConfiguration(testConf);
		
		// Write configuration to file
		TestConfigurationIO.toFile(conf, destination);
		
		return conf;
	}
	
	// Generate the test configuration data for paths
	private List<TestPath> generatePaths(OpenAPISpecification spec, Collection<TestConfigurationFilter> filters) {
		
		List<TestPath> confPaths = new ArrayList<TestPath>();
		
		for (TestConfigurationFilter filter: filters) {
			Map<String,Path> paths = spec.getSpecification().getPaths();
			for(Entry<String,Path> path: paths.entrySet())
				if (filter.getPath()==null || path.getKey().equalsIgnoreCase(filter.getPath()))
					confPaths.add(generatePath(path,filter.getMethods()));
		}
		return confPaths;
	}

	// Generate the test configuration data for a specific input path
	private TestPath generatePath(Entry<String, Path> path, Collection<HttpMethod> methods) {
		
		TestPath confPath = new TestPath();
		confPath.setTestPath(path.getKey());
		
		List<Operation> testOperations = new ArrayList<Operation>();
		
		for (Entry<HttpMethod, io.swagger.models.Operation> operationEntry : path.getValue().getOperationMap().entrySet())
			if (methods.contains(operationEntry.getKey())) // Generate only filtered methods
				testOperations.add(generateOperation(operationEntry));
		
		confPath.setOperations(testOperations);
		
		return confPath;
	}

	// Generate test configuration data for a GET operation
	private Operation generateOperation(Entry<HttpMethod,io.swagger.models.Operation> operationEntry) {
		Operation testOperation = new Operation();
		
		// Set operation id (if defined)
		if (operationEntry.getValue().getOperationId()!=null)
			testOperation.setOperationId(operationEntry.getValue().getOperationId());
		else
			testOperation.setOperationId("<SET OPERATION ID>");
		
		// Set HTTP method
		testOperation.setMethod(operationEntry.getKey().name().toLowerCase());
		
		// Set parameters
		testOperation.setTestParameters(generateTestParameters(operationEntry.getValue().getParameters()));

		// Set expected output
		testOperation.setExpectedResponse("200");
		
		return testOperation;
	}

	// Generate test configuration data for input parameters
	private List<TestParameter> generateTestParameters(List<Parameter> parameters) {
		
		List<TestParameter> testParameters = new ArrayList<>();
		for(Parameter param: parameters) {
			TestParameter testParam = new TestParameter();
			testParam.setName(param.getName());
			
			// Set default weight for optional parameters
			if (!param.getRequired())
				testParam.setWeight(0.5f);

			// Set generator for the parameter
			Generator gen = new Generator();
			List<GenParameter> genParams = new ArrayList<>();
			GenParameter genParam1 = new GenParameter();
			GenParameter genParam2 = new GenParameter();
			GenParameter genParam3 = new GenParameter();
			List<String> genParam1Values = new ArrayList<>();
			List<String> genParam2Values = new ArrayList<>();
			List<String> genParam3Values = new ArrayList<>();

			// If it's a path or query parameter, get type to set a useful generator
			if (param.getIn() == "query" || param.getIn() == "path") {
				String paramType = ((AbstractSerializableParameter) param).getType();
				List<String> paramEnumValues = ((AbstractSerializableParameter) param).getEnum();

				// If the param type is array, get its item type
				if (paramType == "array") {
					paramType = ((AbstractSerializableParameter) param).getItems().getType();
				}

				// If the param is enum, set generator to input value iterator with the values defined in the enum
				if (paramEnumValues != null) {
					paramType = "enum";
				}

				switch (paramType) {
					case "string":
						gen.setType("RandomEnglishWord");     // English words generator
						genParam1.setName("maxWords");        // maxWords generator parameter
						genParam1Values.add("1");
						genParam1.setValues(genParam1Values);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					case "number":
					case "integer":
						gen.setType("RandomNumber");          // Random number generator
						genParam1.setName("type");            // Type parameter
						genParam1Values.add("integer");       // Integer works for integers and floats
						genParam1.setValues(genParam1Values);
						genParams.add(genParam1);
						genParam2.setName("min");             // Min parameter
						genParam2Values.add("1");
						genParam2.setValues(genParam2Values);
						genParams.add(genParam2);
						genParam3.setName("max");             // Max parameter
						genParam3Values.add("100");
						genParam3.setValues(genParam3Values);
						genParams.add(genParam3);
						gen.setGenParameters(genParams);
						break;
					case "boolean":
						gen.setType("RandomBoolean");         // Random number generator
						gen.setGenParameters(genParams);
						break;
					case "enum":
						gen.setType("RandomInputValue");
						genParam1.setName("values");
						genParam1.setValues(paramEnumValues);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					default:
						throw new IllegalArgumentException("The parameter type " + paramType + " is not allowed in query or path");
				}
			}
			// TODO: set smarter generators for body parameters (and maybe others like headers or form-data)
			else {
				// Set default generator (String)
				gen.setType("RandomInputValue");
				genParam1.setName("values");
				genParam1Values.add("value 1");
				genParam1Values.add("value 2");
				genParam1.setValues(genParam1Values);
				genParams.add(genParam1);
				gen.setGenParameters(genParams);
			}

			testParam.setGenerator(gen);
			testParameters.add(testParam);
		}
		
		return testParameters;
	}

	// Default authentication setting (required = false)
	private Auth generateDefaultAuthentication() {
		Auth auth = new Auth();
		auth.setRequired(true);
		auth.setHeaderParams(new ArrayList<HeaderParam>());
		auth.setQueryParams(new ArrayList<QueryParam>());
		return auth;
	}
	
}
