package com.bigcorp.common.rules;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads rule definitions from an XML configuration file.
 * 
 * Reads rules.xml from the classpath and instantiates Rule objects
 * via reflection (Class.forName + newInstance). This externalizes what
 * was previously hardcoded in OrderMessageListener.initRules().
 * 
 * If the config file is not found on the classpath, returns an empty
 * list so the caller can fall back to hardcoded registration.
 * 
 * NOTE: The "active" attribute in the XML is parsed but NOT applied
 * to the Rule object's isActive() method. Each Rule returns its own
 * hardcoded isActive() value. This is the intentional bug documented
 * in RuleEngine — do not "fix" it here.
 * 
 * @author architect
 * @since 2003 Q1
 */
public class RuleConfigLoader {

    /** Default config file name on the classpath */
    private static final String CONFIG_FILE = "rules.xml";

    /**
     * Load rules from the default XML configuration file (rules.xml)
     * on the classpath.
     * 
     * @return List of Rule objects, or empty list if config not found
     */
    public static List loadRules() {
        return loadRules(CONFIG_FILE);
    }

    /**
     * Load rules from a named XML configuration file on the classpath.
     * 
     * @param configFile the config file name to load from classpath
     * @return List of Rule objects, or empty list if config not found
     */
    public static List loadRules(String configFile) {
        List rules = new ArrayList();

        InputStream is = RuleConfigLoader.class.getClassLoader()
                .getResourceAsStream(configFile);

        if (is == null) {
            System.out.println("WARN: Rule config file '" + configFile 
                + "' not found on classpath. Falling back to hardcoded rules.");
            return rules;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            NodeList ruleNodes = doc.getElementsByTagName("rule");
            System.out.println("RuleConfigLoader: found " + ruleNodes.getLength() 
                + " rule definitions in " + configFile);

            for (int i = 0; i < ruleNodes.getLength(); i++) {
                Element ruleElement = (Element) ruleNodes.item(i);
                String className = ruleElement.getAttribute("class");
                // NOTE: the "active" attribute from XML is read but NOT applied
                // to the Rule object's isActive(). This is the intentional bug
                // documented in RuleEngine. The Rule objects return their own
                // hardcoded isActive() value.
                String activeStr = ruleElement.getAttribute("active");

                try {
                    Class ruleClass = Class.forName(className);
                    Rule rule = (Rule) ruleClass.newInstance();
                    rules.add(rule);
                    System.out.println("  Loaded rule: " + rule.getName() 
                        + " (priority=" + rule.getPriority() 
                        + ", active=" + rule.isActive()
                        + ", xml-active=" + activeStr + ")");
                } catch (ClassNotFoundException e) {
                    System.err.println("  ERROR: Rule class not found: " + className);
                } catch (InstantiationException e) {
                    System.err.println("  ERROR: Cannot instantiate rule: " 
                        + className + " - " + e.getMessage());
                } catch (IllegalAccessException e) {
                    System.err.println("  ERROR: Cannot access rule constructor: " 
                        + className + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to parse rule config file: " + e.getMessage());
            e.printStackTrace();
            rules.clear();
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                // ignore close errors
            }
        }

        return rules;
    }
}
