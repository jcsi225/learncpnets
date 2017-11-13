import java.util.*;

class CPNetLearningFromOptimalExamples
{

    // Learn a binary-valued acyclic CP-net from a set of OptimalExamples (see below)
    public static PreferenceSpecification learn(Set<String> allVars, Set<OptimalExample> exampleSet, Integer inDegreeBound)
    {
        // CP-net under construction
        PreferenceSpecification learned = new PreferenceSpecification(allVars);
        // Features that have so far added to the CP-net
        HashSet<String> addedVars = new HashSet<String>();

        // Add variables to the CP-net one at a time
        // Consider increasing sizes of candidate parent sets
            boolean doneWithThisRound = false;
            while (!doneWithThisRound)
            {
                doneWithThisRound = true;
                for (String candidateAddition : allVars)
                {
                    if (!addedVars.contains(candidateAddition))
                    {
                        boolean doneWithThisVar = false;
                        // Consider size-i subsets as parent sets
                        for (int i = 0; i <= inDegreeBound; i++)
                        {
                            for (Set<String> candidateParentSet : RecursivePowerKSet.computeKPowerSet(addedVars, i))
                            {
                                CPTable createdCPT = CPNetLearningFromOptimalExamples.createCPTFromOptima(candidateAddition, candidateParentSet, exampleSet);
                                if (createdCPT != null)
                                {
                                    learned.setCPT(candidateAddition, createdCPT);
                                    addedVars.add(candidateAddition);
                                    // The newly-added variable may become a parent for one that could not previously be added
                                    doneWithThisRound = false;
                                    doneWithThisVar = true;
                                    break;
                                }
                            }
                            if (doneWithThisVar)
                            {
                                break;
                            }
                        }
                    }
                }
            }


        if (addedVars.equals(allVars))
        {
            return  learned;
        }
        else
        {
            return null; // no appropriate CP-net found
        }
    }
    private static CPTable createCPTFromOptima(String var, Set<String> candidateParents, Set<OptimalExample> exampleSet)
    {
        CPTable created = new CPTable(var);

        // See if the preferred value is consistent for each parent assignment
        // e.g., if we're testing Entree and Wine as parents for Side, then each optimum with
        //      conditions containing (Entree=Fish,Wine=Red) should have the same value for Side
        //      (unless Side is also preassigned in the condition)
        for (Assignment candidateParentAssignment : Assignment.allAssignments(candidateParents))
        {
            Boolean chosenVal = null;
            for (OptimalExample example : exampleSet)
            {
                // Relevant examples are those that are not conditioned on the variable in question...
                if (example.condition.keySet().contains(var))
                {
                    continue;
                }
                // ...and the example contains the parent assignment
                if (!example.optimum.subsumes(candidateParentAssignment))
                {
                    continue;
                }

                // Get the hypothetical preferred value of var given candidateParentAssignment
                Boolean valInOptimum = example.optimum.get(var);
                if (chosenVal == null)
                {
                    chosenVal = valInOptimum;
                }
                // Check whether the examples are consistent with this being the preferred value
                else if (!chosenVal.equals(valInOptimum))
                {
                    return null;
                }
            }
            if (chosenVal != null)
            {
                created = created.altered(candidateParentAssignment,chosenVal);
            }
        }

        return created;
        //todo: test
    }

}

// Indication that an outcome is a most-preferred (undominated) one, possibly given some preset variables
// e.g., one can indicate (Entree=Fish,Wine=White,Side=Pasta) is the best meal if Entree is required to be Fish
class OptimalExample
{
    // The restrictions, e.g., (Entree=Fish)
    Assignment condition;
    // The most-preferred outcome, e.g., (Entree=Fish,Wine=White,Side=Pasta)
    Assignment optimum;

    // Constructor
    public OptimalExample(Assignment condition, Assignment optimum)
    {
        // The optimal outcome must contain the assigned values of the condition
        if (!optimum.subsumes(condition))
        {
            throw new RuntimeException("tried to construct an invalid OptimalExample");
        }
        this.condition = condition;
        this.optimum = optimum;
    }

    // Generate an example consistent with the given CP-net
    // Samples uniformly at random from the space of all optimal examples
    // Assumes that the input is a complete acyclic CP-net
    static OptimalExample uniformlyRandomExample(PreferenceSpecification acyclicCPnet)
    {
        // Preset the condition by assigning each preference variable to true, false, or not-conditioned
        Assignment condition = new Assignment();
        Random rng = new Random();
        for (String var : acyclicCPnet.getVars())
        {
            int choice = rng.nextInt(3);
            if (choice == 0)
            {
                condition.put(var,Boolean.TRUE);
            }
            else if (choice == 1)
            {
                condition.put(var,Boolean.FALSE);
            }
            // otherwise (choice == 2), do not condition on this variable
        }

        // Find the best outcome given the conditoin
        return optimumGiven(acyclicCPnet,condition);
    }
    // Random generation from a different distribution --- specify probability that each variable appears in the condition
    static OptimalExample biasedRandomExample(PreferenceSpecification acyclicCPnet, float prob)
    {
        // Preset the condition by assigning each preference variable to true, false, or not-conditioned
        Assignment condition = new Assignment();
        Random rng = new Random();
        for (String var : acyclicCPnet.getVars())
        {
            float roll = rng.nextFloat();
            if (roll < prob)
            {
                condition.put(var,rng.nextBoolean());
            }
            // otherwise, do not condition on this variable
        }
        // Find the best outcome given the conditoin
        return optimumGiven(acyclicCPnet,condition);
    }
    // Helper function
    private static OptimalExample optimumGiven(PreferenceSpecification acyclicCPnet, Assignment condition)
    {
        Assignment optimumSoFar = new Assignment(condition);
        HashSet<String> remainingVars = new HashSet<String>();
        remainingVars.addAll(acyclicCPnet.getVars());
        remainingVars.removeAll(optimumSoFar.keySet());
        while (!remainingVars.isEmpty())
        {
            int numVarsAdded = remainingVars.size(); // for catching infinite loops
            // See if one of the unassigned variables has a preferred value given the assigned value
            for (String var : remainingVars)
            {
                // That should be the case if the variable's parent values have already been chosen
                if (optimumSoFar.keySet().containsAll(
                        acyclicCPnet.getCPT(var).getParents()))
                {
                    Boolean preferredValue = acyclicCPnet.getCPT(var).preferredValueGiven(optimumSoFar);
                    if (preferredValue==null)
                    {
                        throw new RuntimeException("missing preference data; complete CP-net input expected");
                    }
                    else
                    {
                        optimumSoFar.put(var,preferredValue);
                    }
                }
            }
            remainingVars.removeAll(optimumSoFar.keySet());
            if (remainingVars.size()==numVarsAdded) // infinite loop prevention
            {
                    throw new RuntimeException("input CP-net must be acyclic");
            }
        }

        return new OptimalExample(condition,optimumSoFar);
    }

    // For using as HashSet entries
    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
    // Pretty string "better>worse"
    @Override
    public String toString() {
        return this.condition.toString().concat(":").concat(this.optimum.toString());
    }
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final OptimalExample other = (OptimalExample) obj;
        return (this.condition.equals(other.condition) && this.optimum.equals(other.optimum));
    }
}



// Helper functions for computing all size-k subsets of a set
// From https://stackoverflow.com/questions/4098248/how-to-generate-all-k-elements-subsets-from-an-n-elements-set-recursively-in-jav
class RecursivePowerKSet
{
    static public <E> Set<Set<E>> computeKPowerSet(final Set<E> source, final int k)
    {
        if (k==0 || source.size() < k) {
            Set<Set<E>> set = new HashSet<Set<E>>();
            set.add(Collections.EMPTY_SET);
            return set;
        }

        if (source.size() == k) {
            Set<Set<E>> set = new HashSet<Set<E>>();
            set.add(source);
            return set;
        }

        Set<Set<E>> toReturn = new HashSet<Set<E>>();

        // distinguish an element
        for(E element : source) {
            // compute source - element
            Set<E> relativeComplement = new HashSet<E>(source);
            relativeComplement.remove(element);

            // add the powerset of the complement
            Set<Set<E>> completementPowerSet = computeKPowerSet(relativeComplement,k-1);
            toReturn.addAll(withElement(completementPowerSet,element));
        }

        return toReturn;
    }

    /** Given a set of sets S_i and element k, return the set of sets {S_i U {k}} */
    static private <E> Set<Set<E>> withElement(final Set<Set<E>> source, E element)
    {

        Set<Set<E>> toReturn = new HashSet<Set<E>>();
        for (Set<E> setElement : source) {
            Set<E> withElementSet = new HashSet<E>(setElement);
            withElementSet.add(element);
            toReturn.add(withElementSet);
        }

        return toReturn;
    }
}