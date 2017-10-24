import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThesisExperiments
{
    private final static Logger LOGGER = Logger.getLogger(ThesisExperiments.class.getName());

    public static void main(String[] args)
    {
        try
        {

            int numTrials = 100;
            Integer inDegreeBound = 2;
            Integer numVars = 10;
            int numExamples = 10; // todo: vary the number of examples
            String rootExperimentDirectory = "experiments";
            String truePreferencesDirectory = Paths.get(rootExperimentDirectory).resolve("cpnetTruePreferences").toString();
            String baselineDirectory = Paths.get(rootExperimentDirectory).resolve("cpnetBaseline").toString();

            for (Integer trial = 0; trial < numTrials; trial++)
            {
                // Generate examples from a random CP-net
                String cpnetCode = String.format("%04d",trial);
                String cpFile = "cpnet_n".concat(numVars.toString())
                        .concat("c").concat(inDegreeBound.toString())
                        .concat("d2_").concat(cpnetCode)
                        .concat(".xml");
                String cpPath = Paths.get(truePreferencesDirectory).resolve("cpFile").toString();
                PreferenceSpecification truePreferences = new PreferenceSpecification(cpFile);
                HashSet<OptimalExample> exampleSet = new HashSet<OptimalExample>();
                for (int example = 0; example < numExamples; example++)
                {
                    exampleSet.add(OptimalExample.uniformlyRandomExample(truePreferences));
                }
                LOGGER.log(Level.FINE,"trial ".concat(trial.toString()).concat(": examples generated"));

                // Learn a CP-net from the examples
                PreferenceSpecification learnedPreferences = CPNetLearningFromOptimalExamples.learn(truePreferences.getVars(), exampleSet, inDegreeBound);
                LOGGER.log(Level.FINE,"trial ".concat(trial.toString()).concat(": CP-net learned"));

                // See how much of the original preference ordering is found by the learning process
                Set<Comparison> trueEntailments = truePreferences.allEntailments();
                Set<Comparison> learnedEntailments = learnedPreferences.allEntailments();
                int learnedOverlap = ThesisExperiments.setOverlap(trueEntailments,learnedEntailments);
                // Same for a completed version of the learned CP-net...
                PreferenceSpecification completedPreferences = ThesisExperiments.randomCompletion(learnedPreferences);
                Set<Comparison> completedEntailments = completedPreferences.allEntailments();
                int completedOverlap = ThesisExperiments.setOverlap(trueEntailments,completedEntailments);
                // ... and a random CP-net as a baseline
                PreferenceSpecification baselinePreferences = new PreferenceSpecification(baselineDirectory);
                Set<Comparison> baselineEntailments = baselinePreferences.allEntailments();
                int baselineOverlap = ThesisExperiments.setOverlap(trueEntailments,baselineEntailments);


                // todo: output data
                LOGGER.log(Level.FINE,"trial ".concat(trial.toString()).concat(": data logged"));
            }

        }
        catch (RuntimeException err)
        {
            LOGGER.log(Level.SEVERE,err.getMessage());
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
}
