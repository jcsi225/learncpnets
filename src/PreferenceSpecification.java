

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import jdk.jshell.spi.ExecutionControl;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;
import java.io.File;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.util.*;

// CP-net or similar ceteris paribus preference model
// Assumes binary variables
class PreferenceSpecification
{
    // Fields

    // Maps a variable name to a list of conditional preferences over that variable
    private HashMap<String,CPTable> varToCPT;
    // Variable name -> {Boolean -> value name}
    // Allows us to treat binary variable values as Booleans but recover their original names
    private HashMap<String,HashMap<Boolean,String>> varToValueNames;

    // Constructors

    // For building from scratch
    public  PreferenceSpecification()
    {
        this.varToCPT = new HashMap<>();
        this.varToValueNames = new HashMap<>();
    }
    // For building from scratch given a predefined feature set
    public PreferenceSpecification(Set<String> varSet)
    {
        this.varToCPT = new HashMap<>();
        this.varToValueNames = new HashMap<>();
        for (String var : varSet)
        {
            this.addVar(var);
        }
    }
    // Reads in conditional preferences from an XML file
    // Uses Santhanam's format: http://www.ece.iastate.edu/~gsanthan/crisner.html
    // But with additional restrictions: Expect a binary-valued, consistent CP-net
    public PreferenceSpecification(String xmlFile)
    {
        this.varToCPT = new HashMap<String,CPTable>();
        this.varToValueNames = new HashMap<String,HashMap<Boolean,String>>();
        try
        {

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();


            // Get preference variables and their domains
            NodeList varList = doc.getElementsByTagName("PREFERENCE-VARIABLE");
            for (int i = 0; i < varList.getLength(); i++)
            {

                Node varNode = varList.item(i);
                if (varNode.getParentNode().getNodeName().equals("PREFERENCE-SPECIFICATION")) // Avoid getting the "PREFERENCE-SPECIFICATION" children of "PREFERENCE-STATEMENT"
                {
                    Element varElement = (Element) varNode;

//                    System.out.println("VARIABLE-NAME : " + varElement.getElementsByTagName("VARIABLE-NAME").item(0).getTextContent());
                    String varName = varElement.getElementsByTagName("VARIABLE-NAME").item(0).getTextContent();

                    NodeList varValList = varElement.getElementsByTagName("DOMAIN-VALUE");
                    // Assume binary-valued variables
                    if (varValList.getLength() != 2)
                    {
                        throw new RuntimeException("PREFERENCE-VARIABLE should have exactly two DOMAIN-VALUEs");
                    }
                    // Arbitrarily choose one value to be "true" and the other to be "false"
                    this.addVar(varName,varValList.item(0).getTextContent(),varValList.item(1).getTextContent());
                }
            }

            // Get CP-tables
            NodeList stmtList = doc.getElementsByTagName("PREFERENCE-STATEMENT");
            for (int i = 0; i < stmtList.getLength(); i++)
            {
                Node stmtNode = stmtList.item(i);
                Element stmtElement = (Element) stmtNode;

                // Get the relevant variable
//                System.out.println("PREFERENCE-VARIABLE : " + stmtElement.getElementsByTagName("PREFERENCE-VARIABLE").item(0).getTextContent());
                String var = stmtElement.getElementsByTagName("PREFERENCE-VARIABLE").item(0).getTextContent();

                // Get the parent assignment
                Assignment parentAssignment = new Assignment();
                NodeList stmtCondList = stmtElement.getElementsByTagName("CONDITION");
                for (int j = 0; j < stmtCondList.getLength(); j++)
                {
                    Node stmtCondNode = stmtCondList.item(j);
                    Element stmtCondElement = (Element) stmtCondNode;
//                    System.out.println("CONDITION : " + stmtCondElement.getTextContent());
                    String cond = stmtCondElement.getTextContent();
                    // Extract variable and value from strings of the form "var=val"
                    String parentVar = cond.split("=")[0];
                    String parentVal = cond.split("=")[1];
                    if (this.varToValueNames.get(parentVar).get(Boolean.TRUE).equals(parentVal))
                        parentAssignment.put(parentVar, Boolean.TRUE);
                    else
                        parentAssignment.put(parentVar, Boolean.FALSE);
                }

                // Get the preference ordering for the relevant variable from strings of the form "better:worse"
                Node stmtPrefNode = stmtElement.getElementsByTagName("PREFERENCE").item(0);
                Element stmtPrefElement = (Element) stmtPrefNode;
//                System.out.println("PREFERENCE : " + stmtPrefElement.getTextContent());
                String pref = stmtPrefElement.getTextContent();
                String preferredValue = pref.split(":")[0];
                // Insert the statement
                if (this.varToValueNames.get(var).get(Boolean.TRUE).equals(preferredValue))
                {
                    this.addPreference(var,parentAssignment,Boolean.TRUE,Boolean.FALSE);
                }
                else
                {
                    this.addPreference(var,parentAssignment,Boolean.FALSE,Boolean.FALSE);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public static PreferenceSpecification random(int degree,  int variables){todo}

    // Methods

    // Get the variables in the CP-net
    public Set<String> getVars()
    {
        return this.varToValueNames.keySet();
    }

    // Declare the existence of a variable, initializing the relevant fields
    public void addVar(String varName, String positiveValName, String negativeValName)
    {
        if (this.varToCPT.containsKey(varName) || this.varToValueNames.containsKey(varName))
        {
            throw new RuntimeException("tried to add a variable to the CP-net that already existed");
        }
        this.varToCPT.put(varName,new CPTable(varName));
        HashMap<Boolean,String> valueNames = new HashMap<Boolean,String>();
        valueNames.put(Boolean.TRUE,positiveValName);
        valueNames.put(Boolean.FALSE,negativeValName);
        this.varToValueNames.put(varName,valueNames);
    }
    // Version that leaves it up to the class to make up names
    public void addVar(String varName)
    {
        this.addVar(varName,varName.concat("_T"),varName.concat("_F"));
    }


    // Attempt to add "condition: preferredValue > !preferredValue" to var's CP-table (replacing any existing
    //  preference conditioned on the given condition)
    // If preserveAcyclicity is true, reject the change if it would introduce a cycle in the parent relation
    // Return whether the CP-net was changed (false if change was rejected or was redundant to the existing preferences)
    public Boolean addPreference(String var, Assignment condition, Boolean preferredValue, Boolean preserveAcyclicity)
    {
        CPTable originalCPT = this.varToCPT.get(var);
        CPTable candidateCPT = originalCPT.altered(condition,preferredValue);

        this.varToCPT.put(var,candidateCPT);
        if (preserveAcyclicity && this.partOfCycle(var))
        {
            this.varToCPT.put(var,originalCPT);
            return Boolean.FALSE;
        }
        else if (candidateCPT.equals(originalCPT))
        {
            this.varToCPT.put(var,originalCPT);
            return Boolean.FALSE;
        }
        else
        {
            return  Boolean.TRUE;
        }
    }
    // Helper function
    // Return whether the given variable is part of a cycle in the parent relation
    private Boolean partOfCycle(String var)
    {
        HashSet<String> explored = new HashSet<String>();
        LinkedList<String> frontier = new LinkedList<String>();

        explored.add(var);
        frontier.addFirst(var);

        while (!frontier.isEmpty())
        {
            String current = frontier.removeFirst();
            for (String ancestor : this.varToCPT.get(current).getParents())
            {
                if (ancestor.equals(var))
                {
                    return Boolean.TRUE;
                }
                else if (!explored.contains(ancestor))
                {
                    explored.add(ancestor);
                    frontier.addFirst(ancestor);
                }
            }
        }
        return  Boolean.FALSE;
    }


    // Generate all preferences o>o' entailed by the CP-net
    // Warning: Exponential-space in the number of preference variablesvariables
    public HashSet<Comparison> allEntailments()
    {
        HashSet<Comparison> entailments = new HashSet<Comparison>();
        HashMap<Assignment,HashSet<Assignment>> preferenceGraph = this.inducedPreferenceGraph();
        // Take each outcome o' and traverse its better descendants o in the preference graph, generating comparisons o>o'
        for (Assignment worse : preferenceGraph.keySet())
        {
            HashSet<Assignment> explored = new HashSet<Assignment>();
            LinkedList<Assignment> frontier = new LinkedList<Assignment>();
            frontier.addFirst(worse);
            do{
                Assignment current = frontier.removeFirst();
                for (Assignment better : inducedPreferenceGraph().get(current))
                {
                    if (!explored.contains(better))
                    {
                        explored.add(better);
                        frontier.addFirst(better);
                        entailments.add(new Comparison(better,worse));
                    }
                }
            }while (!frontier.isEmpty());
        }
        return entailments;
    }
    // Generate the adjacency lists for this CP-net's induced preference graph (edges from worse to better)
    // Warning: Exponential-space in the number of preference variables
    public HashMap<Assignment,HashSet<Assignment>> inducedPreferenceGraph()
    {
        // Don't do this for large CP-nets
        if (this.varToCPT.size() >= 15)
        {
            throw new RuntimeException("attempted to generate a huge induced preference graph");
        }

        // Assignment -> list of more-preferred assignments that differ on one preference variable
        HashMap<Assignment,HashSet<Assignment>> assnToImprovingFlips = new HashMap<Assignment,HashSet<Assignment>>();

        // Assignments we are finished exploring
        HashSet<Assignment> explored = new HashSet<Assignment>();
        // Assignments we have yet to explore
        HashSet<Assignment> remaining = new HashSet<Assignment>();
        Assignment firstBuilt = new Assignment(this.varToCPT.keySet(),Boolean.FALSE).firstLexicographically();
        Assignment currentBuilt = new Assignment(firstBuilt);
        do {
            remaining.add(currentBuilt);
            currentBuilt = currentBuilt.nextLexicographically();
        }while (!currentBuilt.equals(firstBuilt));

        // Construct the graph by traversing from worse to better
        while (!remaining.isEmpty())
        {
            // Find an unexplored starting point
            LinkedList<Assignment> frontier = new LinkedList<Assignment>();
            frontier.addFirst(remaining.iterator().next());
            explored.add(remaining.iterator().next());
            // Explore improvements from that point until we run out of places to explore
            while (!frontier.isEmpty())
            {
                Assignment assn = frontier.removeFirst();

                // Find all improving flips
                HashSet<Assignment> improvingFlips = new HashSet<Assignment>();
                for (String varToFlip : this.varToCPT.keySet())
                {
                    Assignment flippedAssn = assn.flipped(varToFlip);
                    // Check whether the candidate flipped variable's CP-table entails that the flip is preferred
                    if (this.varToCPT.get(varToFlip).preferredValueGiven(flippedAssn) == flippedAssn.get(varToFlip))
                    {
                        improvingFlips.add(flippedAssn);
                    }
                }
                // These are the current assignment's children in the induced preference graph
                assnToImprovingFlips.put(assn,improvingFlips);

                // Put these items in line explore if they haven't been already
                for (Assignment better : improvingFlips)
                {
                    if (!explored.contains(better))
                    {
                        explored.add(better);
                        frontier.addFirst(better);
                    }
                }

            }
            remaining.removeAll(explored);
        }


        return assnToImprovingFlips;
    }


    // Write an XML file of the preferences, similarly to the read-in format
    void writeXML(String filePath)
    {
        try {

            // Build XML tree
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("PREFERENCE-SPECIFICATION");
            doc.appendChild(rootElement);

            // Add preference variables
            for (HashMap.Entry<String,HashMap<Boolean,String>> varEntry : this.varToValueNames.entrySet())
            {
                Element varElement = doc.createElement("PREFERENCE-VARIABLE");
                rootElement.appendChild(varElement);

                Element varNameElement = doc.createElement("VARIABLE-NAME");
                varNameElement.appendChild(doc.createTextNode(varEntry.getKey())); // the variable name itself
                varElement.appendChild(varNameElement);

                for (HashMap.Entry<Boolean,String> valEntry : varEntry.getValue().entrySet())
                {
                    Element valNameElement = doc.createElement("DOMAIN-VALUE");
                    valNameElement.appendChild(doc.createTextNode(valEntry.getValue())); // the variable name itself
                    varElement.appendChild(valNameElement);
                }
            }

            // Add CP-statements
            Integer stmtID = 0;
            // Iterate through tables
            for (HashMap.Entry<String,CPTable> varEntry : this.varToCPT.entrySet())
            {
                // Iterate through table entries
                for (CPTable.Entry<Assignment,Boolean> stmtEntry : varEntry.getValue().entrySet()) {

                    Element stmtElement = doc.createElement("PREFERENCE-STATEMENT");
                    rootElement.appendChild(stmtElement);

                    Element stmtIDElement = doc.createElement("STATEMENT-ID"); // assign an ID arbitrarily
                    stmtIDElement.appendChild(doc.createTextNode(stmtID.toString()));
                    stmtElement.appendChild(stmtIDElement);
                    stmtID++;

                    Element stmtVarElement = doc.createElement("PREFERENCE-VARIABLE");
                    String var = varEntry.getKey();
                    stmtVarElement.appendChild(doc.createTextNode(var));
                    stmtElement.appendChild(stmtVarElement);

                    // Iterate through parent values in an entry
                    for (Map.Entry<String,Boolean> assnEntry : stmtEntry.getKey().entrySet())
                    {
                        Element stmtCondElement = doc.createElement("CONDITION");
                        String condVal = this.varToValueNames.get(var).get(assnEntry.getValue());
                        stmtCondElement.appendChild(doc.createTextNode(assnEntry.getKey().concat("=").concat(condVal)));
                        stmtElement.appendChild(stmtCondElement);
                    }

                    Element stmtPrefElement = doc.createElement("PREFERENCE");
                    String nameOfBetterVal = this.varToValueNames.get(var).get(stmtEntry.getValue());
                    String nameOfWorseVal = this.varToValueNames.get(var).get(!(stmtEntry.getValue()));
                    stmtPrefElement.appendChild(doc.createTextNode(nameOfBetterVal.concat(":").concat(nameOfWorseVal)));
                    stmtElement.appendChild(stmtPrefElement);
                }
            }

            // Write XML
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        }
    }
}

// Specification of the conditional preferences for one variable
// Maps a partial assignment giving conditions to a bool giving the preferred value of the variable
class CPTable extends HashMap<Assignment,Boolean>
{
    // The variable over which preferences are being specified
    public String var;

    // Constructor
    public CPTable(String var)
    {
        this.var = var;
    }

    // Return the variable's parents
    public HashSet<String> getParents()
    {
        HashSet<String> parents = new HashSet<String>();
        for (Assignment parentAssignment : this.keySet())
        {
            for (String parent : parentAssignment.keySet())
            {
                parents.add(parent);
            }
        }
        return parents;
    }

    // Return the preferred value of the variable given the condition
    public Boolean preferredValueGiven(Assignment condition)
    {
        // Assume the condition is an assignment to a variable set that contains the parent set
        if (!condition.keySet().containsAll(this.getParents()))
        {
            throw new RuntimeException("invalid CP-table lookup");
        }

        // Try to find the relevant table entry
        for (Entry<Assignment,Boolean> entry : this.entrySet())
        {
            if (condition.subsumes(entry.getKey()))
            {
                return entry.getValue();
            }
        }

        // If we reach this point, the table is incomplete and we're looking for a missing preference
        return null;
    }

    // Return a new CPTable like the current one except the given statement is added
    // (Replaces an old statement if it has the same parent assignment)
    public CPTable altered(Assignment parentAssignment, Boolean preferredValue)
    {
        CPTable mod = new CPTable(this.var);

        // If the new and existing statements have different parent sets, modify statements to include all parents
        // e.g., if we have Entree (Fish or Chicken) and Wine (Red or White) as parents,
        //  then "Fish: Soup>Salad" becomes "Fish,Red: Soup>Salad" and "Fish,White: Soup>Salad"
        HashSet<String> originalParents = this.getParents();
        HashSet<String> additionalParents = new HashSet<String>(parentAssignment.keySet());
        // First update the existing statements to use the new parent set
        for (Entry<Assignment,Boolean> currentStatement : this.entrySet())
        {
            HashSet<Assignment> expandedCurrentStatement = currentStatement.getKey().expandedByVars(additionalParents);
            for (Assignment newStmt : expandedCurrentStatement)
            {
                mod.put(newStmt,currentStatement.getValue());
            }
        }
        // Then expand and insert the new statement
        HashSet<Assignment> expandedNewStatement = parentAssignment.expandedByVars(originalParents);
        for (Assignment newStmt : expandedNewStatement)
        {
            mod.put(newStmt,preferredValue);
        }

        return mod.simplified();
    }
    // Helper function for modifying the table
    // Detect and remove superfluous parents (i.e., variables in the conditions that the preferences do not really depend on)
    // Assumes all parents are present in all statements initially
    private CPTable simplified()
    {
        // Identify parents to remove
        HashSet<String> superfluousParents = new HashSet<String>();
        for (String parent : this.getParents())
        {
            // Try to find a pair of statements differing only in this parent where the preferred value differs
            // If none exists, the parent is superfluous
            Boolean superfluous = new Boolean(Boolean.TRUE);
            for (Entry<Assignment,Boolean> stmt : this.entrySet())
            {
                Boolean preferredValueForStmt = stmt.getValue();
                Boolean preferredValueForFlippedStmt = this.get(stmt.getKey().flipped(parent));
                if (!preferredValueForStmt.equals(preferredValueForFlippedStmt))
                {
                    superfluous = Boolean.FALSE;
                }
            }
            if (superfluous)
            {
                superfluousParents.add(parent);
            }
        }

        // Make a CP-table without them
        CPTable mod = new CPTable(this.var);
        for (Entry<Assignment,Boolean> stmt : this.entrySet())
        {
            Assignment newAssn = stmt.getKey().withVarsRemoved(superfluousParents);
            mod.put(newAssn,stmt.getValue());
        }
        return mod;
    }

    // Return a new CPTable like the current one except entailing the opposite preference for the given assignment
    // Assumes that the variable set of the input is a (non-strict) superset of the existing parent set
    public CPTable flipped(Assignment assnToFlip)
    {
        Boolean preferredValue = null;

        for (Assignment currAssn : this.keySet())
        {
            // Determine what the opposite entailed preference is
            if (assnToFlip.subsumes(currAssn))
            {
                preferredValue = !this.get(currAssn);
            }
            // Enforce the requirement that the conditions in question must be at least as specific as the existing conditions
            else if (currAssn.subsumes(assnToFlip))
            {
                throw new RuntimeException("illegal change to CP-table");
            }
        }

        return this.altered(assnToFlip,preferredValue);
    }

    // Consider two CPTables equal if they have the same keys, values, and variable
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CPTable other = (CPTable) obj;
        if (!this.var.equals(other.var))
        {
            return false;
        }
        for (Assignment assn : this.keySet())
        {
            if (other.getOrDefault(assn,null) != this.get(assn))
            {
                return false;
            }
        }
        for (Assignment assn : other.keySet())
        {
            if (this.getOrDefault(assn,null) != other.get(assn))
            {
                return false;
            }
        }
        return true;
    }
}

// Assignment of preference variables to values
class Assignment extends TreeMap<String,Boolean>
{
    // Default constructor
    public Assignment(){}
    // Copy constructor
    public Assignment(Assignment original)
    {
        this.putAll(original);
    }
    // Constructor assigning all of the given preference variables to the given value
    public Assignment(Set<String> varSet, Boolean defaultValue)
    {
        for (String var : varSet)
        {
            this.put(var, defaultValue);
        }
    }

    // Return true iff this assignment is a (non-strict) superset of the other assignment in question
    // e.g., <Entree=Fish,Wine=White> subsumes <>, <Entree=Fish>, <Wine=White>, and <Entree=Fish,Wine=White>
    public Boolean subsumes(Assignment other)
    {
        if (this.entrySet().size() < other.entrySet().size())
            return  false;
        for (SortedMap.Entry<String,Boolean> entry : other.entrySet())
        {
            if (this.getOrDefault(entry.getKey(),null) != entry.getValue())
                return false;
        }
        return true;
    }

    // Return a new Assignment like the current one, but with the given variable assigned to the given value
    public Assignment altered(String varToAddOrChange, Boolean newValueOfVar)
    {
        Assignment mod = new Assignment(this);
        mod.put(varToAddOrChange,newValueOfVar);
        return mod;
    }
    // ... but with the given variable assigned to the opposite of its current value
    public Assignment flipped(String varToFlip)
    {
        Boolean newValue = !this.get(varToFlip);
        return this.altered(varToFlip,newValue);
    }

    // Create a set of Assignments like the current one, but with additional variables
    // (One new Assignment for each combination of settings to new variables)
    // e.g., if we have Entree (Fish or Chicken) and Wine (Red or White) as parents,
    //  then "Fish: Soup>Salad" becomes "Fish,Red: Soup>Salad" and "Fish,White: Soup>Salad"
    public HashSet<Assignment> expandedByVars(HashSet<String> vars)
    {
        HashSet<Assignment> expanded = new HashSet<Assignment>();

        LinkedList<Assignment> expansionQueue = new LinkedList<Assignment>();
        expansionQueue.addLast(this);

        while (!expansionQueue.isEmpty())
        {
            Assignment curr = expansionQueue.removeFirst();

            // See which variables are new to the Assignment
            LinkedList<String> missingVars = new LinkedList<String>();
            for (String var : vars)
            {
                if (!curr.containsKey(var))
                {
                    missingVars.add(var);
                }
            }

            if (missingVars.isEmpty())
            {
                expanded.add(curr);
            }
            // Select and expand over a variable
            else
            {
                String missingVar = missingVars.remove();
                expansionQueue.addLast(curr.altered(missingVar,Boolean.TRUE));
                expansionQueue.addLast(curr.altered(missingVar,Boolean.FALSE));
            }
        }

        return expanded;
    }
    // Create a version of the assignment without the given variables
    public Assignment withVarsRemoved(HashSet<String> vars)
    {
        Assignment mod = new Assignment();
        for (Map.Entry<String,Boolean> assn : this.entrySet())
        {
            if (!vars.contains(assn.getKey()))
            {
                mod.put(assn.getKey(),assn.getValue());
            }
        }
        return mod;
    }


    // For iterating through Assignments over the same variable set
    // Let the ordering start with all falses, lowest digit is first variable by alphabetical name, wrap around
    // e.g., (a=F,b=F), (a=T,b=F), (a=F,b=T), (a=T,b=T), (a=F,b=F), ...
    public Assignment nextLexicographically()
    {
        Assignment mod = new Assignment(this);

        // Flip values until one goes from false to true
        for (String var : mod.keySet())
        {
            mod.put(var,!mod.get(var));
            if (mod.get(var).equals(Boolean.TRUE))
            {
                return mod;
            }
        }
        // If the loop terminates, we've wrapped around to all falses
        return mod;
    }
    public Assignment firstLexicographically()
    {
        return new Assignment(this.keySet(),Boolean.FALSE);
    }

    // Some stuff to let us use Assignment objects as dictionary keys
    // https://stackoverflow.com/questions/2265503/why-do-i-need-to-override-the-equals-and-hashcode-methods-in-java
    // Mash the contents of the map into string form and use that as the hash key
    @Override
    public int hashCode()
    {
        return this.toString().hashCode();
    }
    // Pretty string "(var1=val1,var2=val2,...)"
    @Override
    public String toString() {
        String mapAsString = "(";
        for (SortedMap.Entry<String,Boolean> entry : this.entrySet())
        {
            mapAsString = mapAsString.concat(entry.getKey())
                .concat("=")
                .concat(entry.getValue().toString());
            // If this is not the last entry, add a comma
            if (!this.tailMap(entry.getKey(),false).isEmpty())
            {
                mapAsString = mapAsString.concat(",");
            }
        }
        mapAsString = mapAsString.concat(")");
        return mapAsString;
    }
    // Consider two Assignments equal if they assign the same variables to the same values
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Assignment other = (Assignment) obj;
        return (this.subsumes(other) && other.subsumes(this));
    }
}

// Comparison o>o' for two outcomes o=better, o'=worse
class Comparison
{
    private Assignment better;
    private Assignment worse;

    // Getters
    public Assignment better()
    {
        return this.better;
    }
    public Assignment worse()
    {
        return this.worse;
    }

    // Constructor
    public Comparison(Assignment better, Assignment worse)
    {
        this.better = better;
        this.worse = worse;
    }

    // Return the opposite of this comparision
    public Comparison flipped()
    {
        return new Comparison(this.worse,this.better);
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
    // Pretty string "better>worse"
    @Override
    public String toString() {
        return this.better.toString().concat(">").concat(this.worse.toString());
    }
    // For hashing --- define two Comparisons as equal if they relate the same two assignments in the same way
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Comparison other = (Comparison) obj;
        return (this.better.equals(other.better) && this.worse.equals(other.worse));
    }
}