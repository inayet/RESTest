package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;

import es.us.isa.restest.specification.OpenAPISpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static es.us.isa.restest.inputs.semantic.Predicates.getPredicates;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.executeSPARQLQuery;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.generateQuery;
import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.SEMANTIC_PARAMETER;

public class SemanticInputGenerator {

    private static final Logger log = LogManager.getLogger(SemanticInputGenerator.class);
    private static OpenAPISpecification spec;
    private static String OAISpecPath;
    private static String confPath;
    private static String csvPath = "src/main/resources/TestData/Generated/";

    // TODO: Required and optional parameters in TestConf
    // TODO: Override enums
    // TODO: Take restrictions (regExp, min, max, etc.) into consideration
    // TODO: Take arrays into consideration
    // TODO: Take datatypes into consideration
    public static void main(String[] args) throws IOException {
        setEvaluationParameters(readProperty("evaluation.properties.dir") + "/book.properties");

        TestConfigurationObject conf = loadConfiguration(confPath, spec);


        // DBPedia Endpoint
        String szEndpoint = "http://dbpedia.org/sparql";

        // Key: OperationName       Value: Parameters
        // Change to List<SemanticOperation>
        List<SemanticOperation> semanticOperations = getSemanticOperations(conf);

        // TODO: For loop and convert to class
        for(SemanticOperation semanticOperation: semanticOperations){
            Set<TestParameter> parameters = semanticOperation.getSemanticParameters().keySet();

            Map<TestParameter, List<String>> parametersWithPredicates = getPredicates(parameters);

            String queryString = generateQuery(parametersWithPredicates);
            System.out.println(queryString);

            // Query DBPedia
            Map<String, Set<String>> result = new HashMap<>();
            try{
                result = executeSPARQLQuery(queryString, szEndpoint);
            }catch(Exception ex){
                System.err.println(ex);
            }

            for(TestParameter testParameter: semanticOperation.getSemanticParameters().keySet()){
                Map<TestParameter, Set<String>> map = semanticOperation.getSemanticParameters();
                map.put(testParameter, result.get(testParameter.getName()));
                semanticOperation.setSemanticParameters(map);
            }

        }

        // TODO: Update TestConf
        // TODO: Add log messages

        // Create dir for automatically generated csv files
        createDir(csvPath);
        
        // Generate a csv file for each parameter
        // File name = OperationName_ParameterName
        // Delete file if it exists
        for(SemanticOperation operation: semanticOperations){
            for(TestParameter parameter: operation.getSemanticParameters().keySet()){
                String fileName = "/" + operation.getOperationName() + "_" + parameter.getName() + ".csv";
                String path = csvPath + fileName;
                deleteFile(path);
                createFileIfNotExists(path);

                // Write the Set of values as a csv file
                FileWriter writer = new FileWriter(path);
                String collect = operation.getSemanticParameters().get(parameter)
                        .stream().collect(Collectors.joining("\n"));

                writer.write(collect);
                writer.close();
            }
        }

    }

    private static void setEvaluationParameters(String evalPropertiesFilePath) {
        OAISpecPath = readProperty(evalPropertiesFilePath, "oaispecpath");
        confPath = readProperty(evalPropertiesFilePath, "confpath");
        spec = new OpenAPISpecification(OAISpecPath);
        csvPath = csvPath + spec.getSpecification().getInfo().getTitle();
    }

    public static List<SemanticOperation> getSemanticOperations(TestConfigurationObject testConfigurationObject){
        List<SemanticOperation> semanticOperations = new ArrayList<>();
        // TODO: Take operations without semantic parameters into consideration
        for(Operation operation: testConfigurationObject.getTestConfiguration().getOperations()){
            List<TestParameter> semanticParameters = getSemanticParameters(operation);
            if(semanticParameters.size() > 0){
                semanticOperations.add(new SemanticOperation(operation, semanticParameters));
            }
        }

        return semanticOperations;
    }

    private static List<TestParameter> getSemanticParameters(Operation operation){

        List<TestParameter> res = operation.getTestParameters().stream()
                .filter(x -> x.getGenerator().getType().equalsIgnoreCase(SEMANTIC_PARAMETER))
                .collect(Collectors.toList());

        return res;
    }

}
