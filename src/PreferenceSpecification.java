

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
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
                    HashMap<Boolean,String> varValNames = new HashMap<Boolean,String>();
                    varValNames.put(Boolean.TRUE,varValList.item(0).getTextContent());
                    varValNames.put(Boolean.FALSE,varValList.item(1).getTextContent());
                    this.varToValueNames.put(varName,varValNames);
                    // Initialize the CPT for the variable
                    this.varToCPT.put(varName,new CPTable(varName));
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
                if (this.varToValueNames.get(var).get(Boolean.TRUE).equals(preferredValue))
                    this.varToCPT.get(var).put(parentAssignment,Boolean.TRUE);
                else
                    this.varToCPT.get(var).put(parentAssignment,Boolean.FALSE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Methods

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
                        // I am slightly proud but mostly ashamed of the following monstrous statement
                        // It just means to add "parent=val" to the condition
                        stmtCondElement.appendChild(doc.createTextNode(assnEntry.getKey().concat("=").concat(this.varToValueNames.get(varEntry.getKey()).get(assnEntry.getValue()))));
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
// Maps a partisal assignment giving conditions to a bool giving the preferred value of the variable
class CPTable extends HashMap<Assignment,Boolean>
{
    // The variable over which preferences are being specified
    public String var;

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

    // Have each statement include all parent variables
    // e.g., if we have Entree (Fish or Chicken) and Wine (Red or White) as parents,
    //  then "Fish: Soup>Salad" becomes "Fish,Red: Soup>Salad" and "Fish,White: Soup>Salad"
    // Assume all statements are already consistent with one another
//    private void expandParents();
//    private void removeSuperfluousParents();

}

// Assignment of preference variables to values
class Assignment extends TreeMap<String,Boolean>
{
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


    // Some stuff to let us use Assignment objects as dictionary keys
    // https://stackoverflow.com/questions/2265503/why-do-i-need-to-override-the-equals-and-hashcode-methods-in-java
    // Mash the contents of the map into string form and use that as the hash key
    @Override
    public int hashCode()
    {
        String mapAsString = "";
        for (SortedMap.Entry<String,Boolean> entry : this.entrySet())
        {
            mapAsString = mapAsString.concat(entry.getKey()).concat(entry.getValue().toString());
        }
        return mapAsString.hashCode();
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