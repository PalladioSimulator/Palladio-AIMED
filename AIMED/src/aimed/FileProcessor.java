package aimed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.gmt.modisco.java.AbstractMethodInvocation;
import org.eclipse.gmt.modisco.java.Block;
import org.eclipse.gmt.modisco.java.BodyDeclaration;
import org.eclipse.gmt.modisco.java.Statement;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.gmt.modisco.omg.kdm.kdm.KdmPackage;
import org.palladiosimulator.pcm.repository.RepositoryPackage;
import org.palladiosimulator.pcm.repository.Signature;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.somox.sourcecodedecorator.MethodLevelSourceCodeLink;
import org.somox.sourcecodedecorator.Seff2MethodLink;
import org.somox.sourcecodedecorator.SourceCodeDecoratorPackage;
import org.somox.sourcecodedecorator.SourceCodeDecoratorRepository;

public class FileProcessor {
	private Resource sourceCodeResource;
	private SourceCodeDecoratorRepository sourceCodeModel;
	
	
	public FileProcessor() {
		EPackage.Registry.INSTANCE.put(KdmPackage.eINSTANCE.getNsURI(), KdmPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(JavaPackage.eINSTANCE.getNsURI(), JavaPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(SourceCodeDecoratorPackage.eINSTANCE.getNsURI(), SourceCodeDecoratorPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(RepositoryPackage.eINSTANCE.getNsURI(), RepositoryPackage.eINSTANCE);
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
	}
	
	public void loadResources(String sourceCodeDecoratorFilePath) {
		ResourceSet rs = new ResourceSetImpl();
		URI sourceCodeDecoratorUri = URI.createFileURI(sourceCodeDecoratorFilePath);	
		try {
			sourceCodeResource = rs.getResource(sourceCodeDecoratorUri, true);
			sourceCodeModel =  (SourceCodeDecoratorRepository) sourceCodeResource.getContents().get(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public List<String> getSeffMethods() {
		EList<Seff2MethodLink> smls = sourceCodeModel.getSeff2MethodLink();
		List<String> result = new ArrayList<>();
		StringBuilder sb;
		for (Seff2MethodLink sml : smls) {
			sb = new StringBuilder();
			ResourceDemandingSEFF seff = (ResourceDemandingSEFF) sml.getSeff();
			sb.append(extractEntityDefinition(seff.getBasicComponent_ServiceEffectSpecification().getEntityName()));
			sb.append(".");
			sb.append(seff.getDescribedService__SEFF().getEntityName());
			result.add(sb.toString());
		}
		return result;
	}
	
	private String extractEntityDefinition(String entityName) {
		int stringEnd = entityName.length();
		if (entityName.endsWith(">")) {
			stringEnd -= 1;
		}
		int defStart = entityName.lastIndexOf(" ");
		if (defStart == -1) {
			if (entityName.startsWith("<")) {
				defStart = 1;
			} else {
				defStart = 0;
			}
		} else {
			defStart += 1;
		}
		return entityName.substring(defStart, stringEnd);
	}
	
	private String extractClassName(String completeMethodName) {
		int lastDotIndex = completeMethodName.lastIndexOf(".");
		if (lastDotIndex == -1) {
			return "";
		} else {
			return completeMethodName.substring(0, lastDotIndex);
		}
	}
	
	private String extractMethodName(String completeMethodName) {
		if (completeMethodName.endsWith("*")) {
			completeMethodName = completeMethodName.substring(0, completeMethodName.length() - 1);
		}
		int lastDotIndex = completeMethodName.lastIndexOf(".");
		if (lastDotIndex == -1) {
			return completeMethodName;
		} else {
			return completeMethodName.substring(lastDotIndex + 1, completeMethodName.length());
		}		
	}
	
	public ResourceDemandingSEFF getSeff(String completeMethodName) {
		String className = extractClassName(completeMethodName);
		String methodName = extractMethodName(completeMethodName);
		EList<Seff2MethodLink> smls = sourceCodeModel.getSeff2MethodLink();
		String entityName;
		for (Seff2MethodLink sml : smls) {
			ResourceDemandingSEFF seff = (ResourceDemandingSEFF) sml.getSeff();
			entityName = seff.getBasicComponent_ServiceEffectSpecification().getEntityName();
			if (extractEntityDefinition(entityName).contains(className)) {
				if (seff.getDescribedService__SEFF().getEntityName().contains(methodName)) {
					return seff;
				}
			}
		}
		throw new IndexOutOfBoundsException(String.format("Seff for method %s not found.", completeMethodName));
	}
	
	public List<String> getTrace1Methods(String completeMethodName) {
		//Current does not work. usage.getMethod() returns not the investigated method, but the current method itself
		// caused by resolving the XML-Path again.
		List<String> result = new ArrayList<>();
		String methodName = extractMethodName(completeMethodName);
		String className = extractClassName(completeMethodName);
		List<MethodLevelSourceCodeLink> mlscls = sourceCodeModel.getMethodLevelSourceCodeLink();
		for (MethodLevelSourceCodeLink mlscl : mlscls) {
			System.out.println(mlscl.getRepositoryComponent().getEntityName());
			System.out.println(mlscl.getFunction().getName());
			List<AbstractMethodInvocation> usages = mlscl.getFunction().getUsages();
			for (AbstractMethodInvocation usage : usages) {
				BodyDeclaration bd = (BodyDeclaration) usage;
				System.out.println(bd.getName());
				if (usage.getMethod().getName().contains(methodName)) {
					result.add(extractEntityDefinition(mlscl.getRepositoryComponent().getEntityName()) + "."
							+ mlscl.getFunction().getName() + "*");
				}
			}
		}
		return result;
	}
}
