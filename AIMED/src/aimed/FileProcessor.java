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
import org.eclipse.gmt.modisco.java.Block;
import org.eclipse.gmt.modisco.java.Statement;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.gmt.modisco.omg.kdm.kdm.KdmPackage;
import org.palladiosimulator.pcm.repository.RepositoryPackage;
import org.palladiosimulator.pcm.repository.Signature;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.somox.sourcecodedecorator.Seff2MethodLink;
import org.somox.sourcecodedecorator.SourceCodeDecoratorPackage;
import org.somox.sourcecodedecorator.SourceCodeDecoratorRepository;

public class FileProcessor {
	private Resource sourceCodeResource;
	
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
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public List<String> getSeffMethods() {
		SourceCodeDecoratorRepository sourceCodeModel =  (SourceCodeDecoratorRepository) sourceCodeResource.getContents().get(0);
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
	
	public ResourceDemandingSEFF getSeff(String methodName) {
		return null;
	}
	
	public String getMethodName(AbstractAction action) {
		return null;
	}
	
	public String getIfMethodName(AbstractAction action) {
		return null;
	}
	
	public String getElseMethodName(AbstractAction action) {
		return null;
	}
}
