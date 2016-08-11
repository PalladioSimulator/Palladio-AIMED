package aimed;

import java.io.IOException;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

public class ResultCalculator {
		
	
	public ResultCalculator() {
		
	}
	private void function() {
		/*EPackage.Registry.INSTANCE.put(KdmPackage.eINSTANCE.getNsURI(), KdmPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(JavaPackage.eINSTANCE.getNsURI(), JavaPackage.eINSTANCE);*/
		final Resource resource = new XMIResourceImpl(URI.createFileURI(
				"/Users/snowball/Documents/workspaces/workspace_dynamicspotter/ExtensionTest/ExtensionTest_java.xmi"));
		Resource bla = new XMIResourceImpl(URI.createFileURI(pathName));
		try {
			resource.load(null);
		} catch (final IOException e1) {
			e1.printStackTrace();
		}
		final Model kdmModel = (Model) resource.getContents().get(0);
		for (final CompilationUnit cu : kdmModel.getCompilationUnits()) {
			System.out.println(cu.getOriginalFilePath());
		}
	}

	public void loadXmiFile() {
		
	}
	
}
