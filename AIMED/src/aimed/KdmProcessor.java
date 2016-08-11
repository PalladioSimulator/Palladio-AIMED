package aimed;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class KdmProcessor {
	private File xmiFile = null;

	private Document xmlDocument = null;

	public KdmProcessor() {

	}

	public void parse(File xmiFile) {
		this.xmiFile = xmiFile;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			xmlDocument = dBuilder.parse(xmiFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<String> getTrace1MethodPatterns(String method, List<String> traceMethods) {
		method = removeBrackets(method);
		String investigatedNodePath = findMethodXMLPath(method);
		List<String> foundMethodPatterns = findDirectChildMethods(investigatedNodePath, traceMethods);
		return foundMethodPatterns;
	}
	
	private Node findMethodXMLNode(String method) {
		List<String> methodParts = Arrays.asList(method.split("\\."));
		Node javaModel = xmlDocument.getFirstChild();
		Node parentNode = javaModel;
		Node currentNode = javaModel.getFirstChild();
		NodeList nodeList;
		for (int methodPartsIndex = 0; methodPartsIndex < methodParts.size(); methodPartsIndex++) {
			nodeList = parentNode.getChildNodes();
			for (int nodeCount = 0; currentNode != null; nodeCount++) {
				if (currentNode.hasAttributes()) {
					if (methodPartsIndex != methodParts.size()) {
						if (hasNameAttributeWithText(currentNode, methodParts.get(methodPartsIndex))) {
							parentNode = currentNode;
							break;
						}
					}
				}
				currentNode = nodeList.item(nodeCount);
			}
		}
		return parentNode;
	}
	
	private String findMethodXMLPath(String method) {
		List<String> methodParts = Arrays.asList(method.split("\\."));
		Node javaModel = xmlDocument.getFirstChild();
		Node parentNode = javaModel;
		Node currentNode = javaModel.getFirstChild();
		NodeList nodeList;
		String path = "/";
		for (int methodPartsIndex = 0; methodPartsIndex < methodParts.size(); methodPartsIndex++) {
			nodeList = parentNode.getChildNodes();
			for (int nodeCount = 0; currentNode != null; nodeCount++) {
				if (currentNode.hasAttributes()) {
					if (methodPartsIndex != methodParts.size()) {
						if (hasNameAttributeWithText(currentNode, methodParts.get(methodPartsIndex))) {
							if (currentNode.getNodeName() != null) {
								path += "/@" + currentNode.getNodeName() + "." + String.valueOf(currentNode.getNodeType() - 1);
							}
							parentNode = currentNode;
							break;
						}
					}
				}
				currentNode = nodeList.item(nodeCount);
			}
		}
		return path;
	}
	
	private boolean hasNameAttributeWithText(Node currentNode, String methodPart) {
		NamedNodeMap attributes = currentNode.getAttributes();
		Node node = attributes.getNamedItem("name");
		if (node == null) {
			return false;
		}
		return methodPart.equals(node.getTextContent());
	}
	
	private List<String> findDirectChildMethods (String investigatedNodePath, List<String> traceMethods) {
		List<String> result = new ArrayList<>();
		Node node;
		for (String method : traceMethods) {
			method = removeBrackets(method);
			node = findMethodXMLNode(method);
			if (containsMethodPath(node, investigatedNodePath)) {
				result.add(method + "*");
			}
		}
		return result;
	}
	
	private boolean containsMethodPath(Node node, String methodPath) {
		if (node == null || !node.hasAttributes()) {
			return false;
		}
		NamedNodeMap attributes = node.getAttributes();
		Node attributeNode = attributes.getNamedItem("usages");
		if (attributeNode == null) {
			return false;
		}
		return attributeNode.getTextContent().contains(methodPath);
	}
	
	private String removeBrackets(String method) {
		int index = method.indexOf("(");
		if (index >= 0) {
			return method.substring(0, index);
		} else {
			return method;
		}
	}
	
	//EMF-API load resource
	//Maven: Tycho Addon
}
