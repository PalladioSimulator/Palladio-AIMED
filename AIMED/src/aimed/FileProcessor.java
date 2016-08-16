package aimed;

import java.io.IOException;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.gmt.modisco.java.Model;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.gmt.modisco.omg.kdm.kdm.KdmPackage;
import org.palladiosimulator.pcm.repository.Repository;
import org.palladiosimulator.pcm.repository.RepositoryPackage;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.palladiosimulator.pcm.seff.ServiceEffectSpecification;
import org.palladiosimulator.pcm.seff.seff_performance.util.SeffPerformanceResourceImpl;
import org.somox.sourcecodedecorator.Seff2MethodLink;
import org.somox.sourcecodedecorator.SourceCodeDecoratorPackage;
import org.somox.sourcecodedecorator.SourceCodeDecoratorRepository;
import org.somox.sourcecodedecorator.impl.SourceCodeDecoratorRepositoryImpl;
import org.somox.sourcecodedecorator.util.SourceCodeDecoratorResourceImpl;

public class FileProcessor {
	private XMIResource kdmResource;
	private XMIResource sourceCodeResource;
	private XMIResource repositoryResource;
	
	public FileProcessor() {
		EPackage.Registry.INSTANCE.put(KdmPackage.eINSTANCE.getNsURI(), KdmPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(JavaPackage.eINSTANCE.getNsURI(), JavaPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(SourceCodeDecoratorPackage.eINSTANCE.getNsURI(), SourceCodeDecoratorPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(RepositoryPackage.eINSTANCE.getNsURI(), RepositoryPackage.eINSTANCE);
	}
	
	public void loadKdmResource(String filePath) {
		URI kdmUri= URI.createFileURI("C:/Users/Cel/Studium/Bachelor/Vorbereitung/Eclipse/workspace/responsetimesServlet/responsetimesServlet_java.xmi");
		kdmResource = new XMIResourceImpl(kdmUri);
		try {
			kdmResource.load(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void loadSourceCodeDecoratorResource(String filePath) {
		URI sourceCodeDecoratorUri = URI.createFileURI("C:/Users/Cel/Studium/Bachelor/Vorbereitung/Eclipse/workspace/responsetimesServlet/model/internal_architecture_model.sourcecodedecorator");	
		sourceCodeResource = new SourceCodeDecoratorResourceImpl(sourceCodeDecoratorUri);
		try {
			sourceCodeResource.load(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void loadRepositoryResource(String filePath) {
		URI repositoryUri = URI.createFileURI("C:/Users/Cel/Studium/Bachelor/Vorbereitung/Eclipse/workspace/responsetimesServlet/model/internal_architecture_model.repository");
		repositoryResource = new SeffPerformanceResourceImpl(repositoryUri);
		try {
			repositoryResource.load(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void getSeffMethods() {
		Model kdmModel = (Model) kdmResource.getContents().get(0);
		SourceCodeDecoratorRepository sourceCodeModel =  (SourceCodeDecoratorRepository) sourceCodeResource.getContents().get(0);
		Repository repo = (Repository) repositoryResource.getContents().get(0);
		EList<Seff2MethodLink> smls = sourceCodeModel.getSeff2MethodLink();
		for (Seff2MethodLink sml : smls) {
			ResourceDemandingSEFF seff = (ResourceDemandingSEFF) sml.getSeff();
			
		}
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
