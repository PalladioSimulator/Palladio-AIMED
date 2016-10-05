package aimed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.activation.UnsupportedDataTypeException;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.gmt.modisco.java.ArrayAccess;
import org.eclipse.gmt.modisco.java.ArrayInitializer;
import org.eclipse.gmt.modisco.java.ArrayLengthAccess;
import org.eclipse.gmt.modisco.java.AssertStatement;
import org.eclipse.gmt.modisco.java.Assignment;
import org.eclipse.gmt.modisco.java.Block;
import org.eclipse.gmt.modisco.java.CastExpression;
import org.eclipse.gmt.modisco.java.CatchClause;
import org.eclipse.gmt.modisco.java.ClassInstanceCreation;
import org.eclipse.gmt.modisco.java.ConditionalExpression;
import org.eclipse.gmt.modisco.java.ContinueStatement;
import org.eclipse.gmt.modisco.java.DoStatement;
import org.eclipse.gmt.modisco.java.EnhancedForStatement;
import org.eclipse.gmt.modisco.java.Expression;
import org.eclipse.gmt.modisco.java.ExpressionStatement;
import org.eclipse.gmt.modisco.java.FieldAccess;
import org.eclipse.gmt.modisco.java.ForStatement;
import org.eclipse.gmt.modisco.java.IfStatement;
import org.eclipse.gmt.modisco.java.InfixExpression;
import org.eclipse.gmt.modisco.java.InstanceofExpression;
import org.eclipse.gmt.modisco.java.LabeledStatement;
import org.eclipse.gmt.modisco.java.MethodInvocation;
import org.eclipse.gmt.modisco.java.NumberLiteral;
import org.eclipse.gmt.modisco.java.ParenthesizedExpression;
import org.eclipse.gmt.modisco.java.PostfixExpression;
import org.eclipse.gmt.modisco.java.PrefixExpression;
import org.eclipse.gmt.modisco.java.ReturnStatement;
import org.eclipse.gmt.modisco.java.SingleVariableAccess;
import org.eclipse.gmt.modisco.java.Statement;
import org.eclipse.gmt.modisco.java.SuperConstructorInvocation;
import org.eclipse.gmt.modisco.java.SwitchCase;
import org.eclipse.gmt.modisco.java.SwitchStatement;
import org.eclipse.gmt.modisco.java.SynchronizedStatement;
import org.eclipse.gmt.modisco.java.ThrowStatement;
import org.eclipse.gmt.modisco.java.TryStatement;
import org.eclipse.gmt.modisco.java.VariableDeclaration;
import org.eclipse.gmt.modisco.java.VariableDeclarationStatement;
import org.eclipse.gmt.modisco.java.WhileStatement;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.gmt.modisco.omg.kdm.kdm.KdmPackage;
import org.lpe.common.util.LpeStringUtils;
import org.palladiosimulator.pcm.repository.RepositoryPackage;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.somox.sourcecodedecorator.MethodLevelSourceCodeLink;
import org.somox.sourcecodedecorator.Seff2MethodLink;
import org.somox.sourcecodedecorator.SourceCodeDecoratorPackage;
import org.somox.sourcecodedecorator.SourceCodeDecoratorRepository;

public class FileProcessor {
	/**
	 * The resource containing the KDM and the PCM.
	 */
	private Resource sourceCodeResource;
	
	/**
	 * The repository of the source code decorator .
	 */
	private SourceCodeDecoratorRepository sourceCodeModel;
	
	/**
	 * A list of methods directly called by one method.
	 */
	private List<String> calledMethods;
	
	/**
	 * Add required packages to the registry.
	 */
	public FileProcessor() {
		EPackage.Registry.INSTANCE.put(KdmPackage.eINSTANCE.getNsURI(), KdmPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(JavaPackage.eINSTANCE.getNsURI(), JavaPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(SourceCodeDecoratorPackage.eINSTANCE.getNsURI(), SourceCodeDecoratorPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(RepositoryPackage.eINSTANCE.getNsURI(), RepositoryPackage.eINSTANCE);
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
	}
	
	/**
	 * Loads the resource using the given file path. 
	 * The KDM and the PCM are loaded automatically by using the paths in the inner of the source code decorator.
	 * @param sourceCodeDecoratorFilePath The file path to the source code decorator
	 */
	public void loadResources(String sourceCodeDecoratorFilePath) {
		ResourceSet rs = new ResourceSetImpl();
		URI sourceCodeDecoratorUri = URI.createFileURI(sourceCodeDecoratorFilePath);	
		try {
			sourceCodeResource = rs.getResource(sourceCodeDecoratorUri, true);
			//TODO: Remove this, its just for debugging.
			sourceCodeResource.setTrackingModification(true);
			sourceCodeModel =  (SourceCodeDecoratorRepository) sourceCodeResource.getContents().get(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return Returns all SEFFs existing in loaded the PCM.
	 */
	public List<ResourceDemandingSEFF> getSeffs() {
		EList<Seff2MethodLink> smls = sourceCodeModel.getSeff2MethodLink();
		List<ResourceDemandingSEFF> result = new ArrayList<>();
		ResourceDemandingSEFF seff;
		for (Seff2MethodLink sml : smls) {
			seff = (ResourceDemandingSEFF) sml.getSeff();
			result.add(seff);
		}
		return result;
	}
	
	/**
	 * Removes the brackets to get the class definition of a generated component.
	 * @param entityName The complete name of the component.
	 * @return Returns the class definition of the component, extracted by its name.
	 */
	public String extractEntityDefinition(String entityName) {
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
	
	/**
	 * Removes the method name to get the class definition of the method.
	 * @param completeMethodName The method and its class definition.
	 * @return Returns the class definition.
	 */
	private String extractClassName(String completeMethodName) {
		int lastDotIndex = completeMethodName.lastIndexOf(".");
		int beginIndex = 0;
		if (completeMethodName.startsWith("*")) {
			beginIndex = 1;
		}
		if (lastDotIndex == -1) {
			return "";
		} else {
			return completeMethodName.substring(beginIndex, lastDotIndex);
		}
	}
	
	/**
	 * Removes the class definition to get the name of the method.
	 * @param completeMethodName The method an its class definition.
	 * @return Returns the name of the method.
	 */
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
	
	/**
	 * @param completeMethodName Requires the class definition and the method, e.g., "package.class.method".
	 * @return Returns the SEFF of a given method.
	 */
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
	
	/**
	 * Store the resource back to disk.
	 */
	public void saveResource() {
		try {
			sourceCodeResource.save(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return Returns if the model is modified.
	 */
	public boolean isModified() {
		return sourceCodeResource.isModified();
	}
	
	/**
	 * This method returns all methods that are directly called by a method.
	 * @param completeMethodName
	 * @return
	 */
	public List<String> getTrace1Methods(String completeMethodName) {
		calledMethods = new ArrayList<>();
		List<MethodLevelSourceCodeLink> mlscls = sourceCodeModel.getMethodLevelSourceCodeLink();
		String method;
		for (MethodLevelSourceCodeLink mlscl : mlscls) {
			method = extractEntityDefinition(mlscl.getRepositoryComponent().getEntityName());
			method += "." + mlscl.getOperation().getEntityName();
			if (LpeStringUtils.patternMatches(method, completeMethodName)) {
				List<Statement> statements = mlscl.getFunction().getBody().getStatements();
				for (Statement statement : statements) {
					try {
						processStatement(statement);
					} catch (UnsupportedDataTypeException e) {
						e.printStackTrace();
					}
				}
				return calledMethods;
			}
		}
		return calledMethods;
	}
	
	private void processStatement(Statement statement) throws UnsupportedDataTypeException {
		if (statement instanceof TryStatement) {
			TryStatement ts = (TryStatement) statement;
			List<Statement> statements = ts.getBody().getStatements();
			for (Statement s : statements) {
				processStatement(s);
			}
			return;
		}
		if (statement instanceof ExpressionStatement) {
			ExpressionStatement es = (ExpressionStatement) statement;
			processExpression(es.getExpression());
			return;
		}
		if (statement instanceof IfStatement) {
			IfStatement is = (IfStatement) statement;
			processStatement(is.getThenStatement());
			processStatement(is.getElseStatement());
			return;
		}
		if (statement instanceof Block) {
			Block b = (Block) statement;
			List<Statement> ss = b.getStatements();
			for (Statement s : ss) {
				processStatement(s);
			}
			return;
		}
		if (statement instanceof CatchClause) {
			CatchClause cc = (CatchClause) statement;
			List<Statement> ss = cc.getBody().getStatements();
			for (Statement s : ss) {
				processStatement(s);
			}
			return;
		}
		if (statement instanceof DoStatement) {
			DoStatement ds = (DoStatement) statement;
			processStatement(ds.getBody());
			processExpression(ds.getExpression());
			return;
		}
		if (statement instanceof EnhancedForStatement) {
			EnhancedForStatement efs = (EnhancedForStatement) statement;
			processStatement(efs.getBody());
			processExpression(efs.getExpression());
			return;
		}
		if (statement instanceof ForStatement) {
			ForStatement fs = (ForStatement)statement;
			processStatement(fs.getBody());
			processExpression(fs.getExpression());
			return;
		}
		if (statement instanceof LabeledStatement) {
			LabeledStatement ls = (LabeledStatement) statement;
			processStatement(ls.getBody());
			return;
		}
		if (statement instanceof ReturnStatement) {
			ReturnStatement rs = (ReturnStatement) statement;
			processExpression(rs.getExpression());
			return;
		}
		if (statement instanceof SuperConstructorInvocation) {
			SuperConstructorInvocation sci = (SuperConstructorInvocation) statement;
			processExpression(sci.getExpression());
			return;
		}
		if (statement instanceof SwitchCase) {
			SwitchCase sc = (SwitchCase) statement;
			processExpression(sc.getExpression());
			return;
		}
		if (statement instanceof SwitchStatement) {
			SwitchStatement ss = (SwitchStatement) statement;
			processExpression(ss.getExpression());
			List<Statement> statements = ss.getStatements();
			for (Statement s : statements) {
				processStatement(s);
			}
			return;
		}
		if (statement instanceof SynchronizedStatement) {
			SynchronizedStatement syn = (SynchronizedStatement) statement;
			processExpression(syn.getExpression());
			List<Statement> ss = syn.getBody().getStatements();
			for (Statement s : ss) {
				processStatement(s);
			}
			return;
		}
		if (statement instanceof ThrowStatement) {
			ThrowStatement ts = (ThrowStatement) statement;
			processExpression(ts.getExpression());
			return;
		}
		if (statement instanceof WhileStatement) {
			WhileStatement ws = (WhileStatement) statement;
			processExpression(ws.getExpression());
			processStatement(ws.getBody());
			return;
		}
		if (statement instanceof AssertStatement) {
			AssertStatement as = (AssertStatement) statement;
			processExpression(as.getExpression());
			return;
		}
		if (statement instanceof VariableDeclarationStatement) {
			return;
		}
		if (statement instanceof ContinueStatement) {
			return;
		}
		if (statement != null) {
			throw new UnsupportedDataTypeException("Not supportet Statement: " + statement.toString());			
		}
	}
	
	private void processExpression(Expression expression) throws UnsupportedDataTypeException {
		if (expression instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) expression;
			String originalCompilationUnitName = null;
			try {
				originalCompilationUnitName = mi.getMethod().getOriginalCompilationUnit().getName();
			} catch (Exception e) {
				
			}
			if (originalCompilationUnitName == null) {
				return;
			} else {
				addMethodToCalledMethods(mi);
			}
			return;
		}
		if (expression instanceof Assignment) {
			Assignment a = (Assignment) expression;
			processExpression(a.getRightHandSide());
			processExpression(a.getLeftHandSide());
			return;
		}
		if (expression instanceof ArrayInitializer) {
			ArrayInitializer ai = (ArrayInitializer) expression;
			List<Expression> es = ai.getExpressions();
			for (Expression e : es) {
				processExpression(e);
			}
			return;
		}
		if (expression instanceof ArrayLengthAccess) {
			ArrayLengthAccess ala = (ArrayLengthAccess) expression;
			processExpression(ala.getArray());
			return;
		}
		if (expression instanceof CastExpression) {
			CastExpression ce = (CastExpression) expression;
			processExpression(ce.getExpression());
			return;
		}
		if (expression instanceof ClassInstanceCreation) {
			ClassInstanceCreation cic = (ClassInstanceCreation) expression;
			processExpression(cic.getExpression());
			return;
		}
		if (expression instanceof ConditionalExpression) {
			ConditionalExpression ce = (ConditionalExpression) expression;
			processExpression(ce.getExpression());
			return;
		}
		if (expression instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) expression;
			processExpression(fa.getExpression());
			return;
		}
		if (expression instanceof InfixExpression) {
			InfixExpression ie = (InfixExpression) expression;
			processExpression(ie.getLeftOperand());
			processExpression(ie.getRightOperand());
			return;
		}
		if (expression instanceof InstanceofExpression) {
			InstanceofExpression ie = (InstanceofExpression) expression;
			processExpression(ie.getLeftOperand());
			return;
		}
		if (expression instanceof ParenthesizedExpression) {
			ParenthesizedExpression pe = (ParenthesizedExpression) expression;
			processExpression(pe.getExpression());
			return;
		}
		if (expression instanceof PostfixExpression) {
			PostfixExpression pe = (PostfixExpression) expression;
			processExpression(pe.getOperand());
			return;
		}
		if (expression instanceof PrefixExpression) {
			PrefixExpression pe = (PrefixExpression) expression;
			processExpression(pe.getOperand());
			return;
		}
		if (expression instanceof SingleVariableAccess) {
			return;
		}
		if (expression instanceof NumberLiteral) {
			return;
		}
		if (expression instanceof ArrayAccess) {
			return;
		}
		if (expression != null) {
			throw new UnsupportedDataTypeException("Not supportet Expression: " + expression.toString());			
		}
	}
	
	private String removeCompilationUnitEnding(String compilationUnitName) {
		int dot = compilationUnitName.lastIndexOf(".");
		return compilationUnitName.substring(0, dot);
	}
	
	private void addMethodToCalledMethods(MethodInvocation methodInvocation) {
		//TODO: Recusive call on original compilation unit.
		String result = removeCompilationUnitEnding(methodInvocation.getMethod().getOriginalCompilationUnit().getName());	
		result = "*" + result + "." + methodInvocation.getMethod().getName() + "*";
		if (!calledMethods.contains(result)) {
			calledMethods.add(result);
		}
	}
	
	
	
	
	
	
}