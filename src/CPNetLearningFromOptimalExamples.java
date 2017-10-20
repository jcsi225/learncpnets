import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class CPNetLearningFromOptimalExamples
{

    // Learn a CP-net from a set of OptimalExamples (see below)
    public static PreferenceSpecification learn(Set<String> allVars, Set<OptimalExample> exampleSet, Integer inDegreeBound)
    {
        // CP-net under construction
        PreferenceSpecification learned = new PreferenceSpecification(allVars);
        // Features that have so far added to the CP-net
        HashSet<String> addedVars = new HashSet<String>();

        // Add variables to the CP-net one at a time
        // Consider increasing sizes of candidate parent sets
        for (int i = 0; i <= inDegreeBound; i++)
        {
            for (String candidateAddition : allVars)
            {
                if (!addedVars.contains(candidateAddition))
                {
                    // Consider size-i subsets as parent sets
                    for (Set<String> candidateParentSet : RecursivePowerKSet.computeKPowerSet(allVars, i))
                    {
                        // Don't make a a variable its own parent
                        if (candidateParentSet.contains(candidateAddition))
                        {
                            continue;
                        }
                        CPTable createdCPT = CPNetLearningFromOptimalExamples.createCPTFromOptima(candidateAddition, candidateParentSet, exampleSet);
                        if (createdCPT != null)
                        {
                            learned.setCPT(candidateAddition, createdCPT);
                            addedVars.add(candidateAddition);
                        }
                    }
                }
            }
        }

        if (addedVars.equals(allVars)) // todo: make sure this works as expected
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
                // ...but are conditioned on the parent assignment
                if (!example.condition.subsumes(candidateParentAssignment))
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
            created = created.altered(candidateParentAssignment,chosenVal);
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