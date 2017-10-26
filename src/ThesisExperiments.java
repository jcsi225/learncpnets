import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThesisExperiments
{
    private final static Logger LOGGER = Logger.getLogger(ThesisExperiments.class.getName());

    public static void main(String[] args)
    {
        int numTrials = 100;
        Integer inDegreeBound = 2;
        Integer numVars = 10;
//        int numExamples = 10; //  vary the number of examples
        String rootExperimentDirectory = "experiments";
        String truePreferencesDirectory = Paths.get(rootExperimentDirectory).resolve("cpnetTruePreferences").toString();
        String baselineDirectory = Paths.get(rootExperimentDirectory).resolve("cpnetBaseline").toString();

        // Produce a CSV file with data from the trials:
        List<List<String>> csvRecords = new LinkedList<List<String>>();
        List<String> csvHeader = new LinkedList<String>();
        csvHeader.add("trial"); // the CP-net used for generation
        csvHeader.add("numExamples");   // how many examples were generated
        csvHeader.add("exampleDistribution");   // how the examples were chosen
        csvHeader.add("numTrueEntailments");    // paired comparisons entailed by generator CP-net
        csvHeader.add("numLearnedEntailments"); // ... entailed by learned CP-net
        csvHeader.add("learnedOverlap");    // ... entailed by both (conjunctively)
        csvHeader.add("numCompletedEntailments");   // ... entailed by completed learned CP-net
        csvHeader.add("completedOverlap");  // ... entailed by both generator and completed
        csvHeader.add("numBaselineEntailments"); // ... entailed by a random CP-net
        csvHeader.add("baselineOverlap"); // ... entailed by both generator and a random CP-net
        csvRecords.add(csvHeader);

        try
        {
            LOGGER.addHandler(new FileHandler("out.log"));
            LOGGER.setLevel(Level.FINEST);

            for (Integer trial = 0; trial < numTrials; trial++)
            {
                // Generate examples from a random CP-net
                String cpnetCode = String.format("%04d",trial);
                String cpFile = "cpnet_n".concat(numVars.toString())
                        .concat("c").concat(inDegreeBound.toString())
                        .concat("d2_").concat(cpnetCode)
                        .concat(".xml");
                String cpPath = Paths.get(truePreferencesDirectory).resolve(cpFile).toString();
                PreferenceSpecification truePreferences = new PreferenceSpecification(cpPath);

                // Try different biases for example generation
                LinkedList<String> distributions = new LinkedList<String>();
                distributions.add("uniform");
                distributions.add("biased");

                for (String exampleDistribution : distributions)
                {
                    for (Integer numExamples = 5; numExamples <= 80; numExamples *= 2)
                    {
                        HashSet<OptimalExample> exampleSet = new HashSet<OptimalExample>();
                        for (int example = 0; example < numExamples; example++)
                        {
                            if (exampleDistribution.equals("biased"))
                            {
                                float prob = (float)1 / (float)numVars; // probability of including a given variable in the condition
                                exampleSet.add(OptimalExample.biasedRandomExample(truePreferences,prob));
                            }
                            else // exampleDistribution.equals("uniform")
                            {
                                exampleSet.add(OptimalExample.uniformlyRandomExample(truePreferences));
                            }
                        }
                        LOGGER.log(Level.FINE, "trial ".concat(trial.toString()).concat(": examples generated"));

                        // Learn a CP-net from the examples
                        PreferenceSpecification learnedPreferences = CPNetLearningFromOptimalExamples.learn(truePreferences
                                .getVars(), exampleSet, inDegreeBound);
                        LOGGER.log(Level.FINE, "trial ".concat(trial.toString()).concat(": CP-net learned"));

                        // See how much of the original preference ordering is found by the learning process
                        Set<Comparison> trueEntailments = truePreferences.allEntailments();
                        Set<Comparison> learnedEntailments = learnedPreferences.allEntailments();
                        int learnedOverlap = ThesisExperiments.setOverlap(trueEntailments, learnedEntailments);
                        // Same for a completed version of the learned CP-net...
                        PreferenceSpecification completedPreferences = ThesisExperiments.randomCompletion(learnedPreferences);
                        Set<Comparison> completedEntailments = completedPreferences.allEntailments();
                        int completedOverlap = ThesisExperiments.setOverlap(trueEntailments, completedEntailments);
                        // ... and a random CP-net as a baseline
                        PreferenceSpecification baselinePreferences = new PreferenceSpecification(Paths.get(baselineDirectory)
                                .resolve(cpFile)
                                .toString());
                        Set<Comparison> baselineEntailments = baselinePreferences.allEntailments();
                        int baselineOverlap = ThesisExperiments.setOverlap(trueEntailments, baselineEntailments);


                        List<String> csvData = new LinkedList<String>();
                        csvData.add(trial.toString()); // the CP-net used for generation
                        csvData.add(Integer.toString(exampleSet.size()));   // how many examples were generated
                        csvData.add(exampleDistribution);   // how the examples were chosen
                        csvData.add(Integer.toString(trueEntailments.size()));    // paired comparisons entailed by generator CP-net
                        csvData.add(Integer.toString(learnedEntailments.size())); // ... entailed by learned CP-net
                        csvData.add(Integer.toString(learnedOverlap));    // ... entailed by both (conjunctively)
                        csvData.add(Integer.toString(completedEntailments.size()));   // ... entailed by completed learned CP-net
                        csvData.add(Integer.toString(completedOverlap));  // ... entailed by both generator and completed
                        csvData.add(Integer.toString(baselineEntailments.size())); // ... entailed by a random CP-net
                        csvData.add(Integer.toString(baselineOverlap)); // ... entailed by both generator and a random CP-net
                        csvRecords.add(csvData);

                        LOGGER.log(Level.FINE, "trial ".concat(trial.toString())
                                .concat(": trial complete for ").concat(cpnetCode)
                                .concat(" with example count ").concat(numExamples.toString())
                                .concat(" and distribution ").concat(exampleDistribution));
                    }
                }
            }

            String dataFilePath = Paths.get(rootExperimentDirectory).resolve("experiments.csv").toString();
            ThesisExperiments.writeCSVFile(csvRecords,dataFilePath);
        }
        catch (Exception err)
        {
            err.printStackTrace();
            LOGGER.log(Level.SEVERE,err.getMessage());
        }
        finally
        {
            LOGGER.log(Level.FINE,"terminating");
        }
    }
    // Helper function for finding how many elements two sets have in common
    private static int setOverlap(Set first, Set second)
    {
        Set overlap = new HashSet();
        for (Object o : first)
        {
            if (second.contains(o))
            {
                overlap.add(o);
            }
        }
        return overlap.size();
    }
    // Generate a completed version of a learned incomplete CP-net
    private static PreferenceSpecification randomCompletion(PreferenceSpecification original)
    {
        PreferenceSpecification mod = new PreferenceSpecification(original);
        Random rng = new Random();

        // Scan each CPT for incompleteness
        for (String var : mod.getVars())
        {
            CPTable cpt = mod.getCPT(var);
            // Look for parent assignment combinations without a preferred value
            for (Assignment parentAssignment : Assignment.allAssignments(cpt.getParents()))
            {
                if (!cpt.containsKey(parentAssignment))
                {
                    // Assign the preferred value randomly
                    mod.addPreference(var,parentAssignment,rng.nextBoolean(),false);
                }
            }
        }

        return mod;
    }
    // Simple CSV writer to avoid external dependencies
    private static void writeCSVFile(List<List<String>> allLines, String filePath)
    {
        // Assemble the CSV text
        String fileContents = "";
        for (List<String> record : allLines)
        {
            fileContents = fileContents.concat(ThesisExperiments.csvLine(record)).concat("\n");
        }

        // Write the file
        try
        {
            Files.write(Paths.get(filePath), fileContents.getBytes(), StandardOpenOption.CREATE);
        }
        catch (IOException err)
        {
            LOGGER.log(Level.SEVERE,err.getMessage());
        }
    }
    private static String csvLine(List<String> record)
    {
        String line = "";
        Iterator<String> iterator = record.iterator();
        while (iterator.hasNext())
        {
            // Insert next element
            line = line.concat(iterator.next());
            // Comma separation
            if (iterator.hasNext())
            {
                line = line.concat(",");
            }
        }
        return line;
    }
}
