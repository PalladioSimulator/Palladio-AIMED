package aimed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.measure.quantity.Duration;
import javax.measure.unit.UnitFormat;

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
import org.jscience.physics.amount.Amount;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.ExtensionRegistry;
import org.lpe.common.extension.IExtension;
import org.lpe.common.extension.IExtensionRegistry;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.rosuda.REngine.Rserve.RserveException;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.exceptions.WorkloadException;

import messages.*;
import util.RAdapter;
import util.Config;
import util.CostumUnits;


public class AimedMainController extends Observable implements Observer {
	private static AimedMainController instance = null;

	private IAdaptiveInstrumentation instrumentationClient;
	
	private Map<String, MeasurementData> results = new HashMap<>();
	
	private List<IExtension> workloadExtensions;
	
	private AbstractWorkloadAdapter selectedWorkloadAdapter;
	
	private FileProcessor fileProcessor;
	
	private RAdapter rAdapter;
	
	private Future<?> measurementThreadPool = null;

	public static synchronized AimedMainController getInstance() {
		if (instance == null) {
			instance = new AimedMainController();
		}
		return instance;
	}

	private AimedMainController() {
		loadWorkloadAdapter();
		initializeCostumUnits();
	}
	
	private void initializeCostumUnits() {
		CostumUnits costumUnit = CostumUnits.getInstance();
		UnitFormat.getInstance().label(CostumUnits.ResourceDemand, "AbstractWorkUnit");
		UnitFormat.getInstance().alias(CostumUnits.ResourceDemand, "AbstractWorkUnit");
	}
	
	public void loadResources(String sourceCodeDecoratorFilePath) {
		fileProcessor = new FileProcessor();
		fileProcessor.loadResources(sourceCodeDecoratorFilePath);
	}
	
	public List<String> getSeffMethodNames() {
		List<String> result = new ArrayList<>();
		List<ResourceDemandingSEFF> seffs = fileProcessor.getSeffs();
		StringBuilder sb;
		for (ResourceDemandingSEFF seff : seffs) {
			sb = new StringBuilder();
			sb.append(fileProcessor.extractEntityDefinition(seff.getBasicComponent_ServiceEffectSpecification().getEntityName()));
			sb.append(".");
			sb.append(seff.getDescribedService__SEFF().getEntityName());
			result.add(sb.toString());
		}
		return result;
	}
	
	private void loadWorkloadAdapter() {
		workloadExtensions = new ArrayList<>();
		Properties workloadProperties = new Properties();
		workloadProperties.put(ExtensionRegistry.PLUGINS_FOLDER_PROPERTY_KEY, Config.getInstance().getProperty("plugins.path", ""));
		workloadProperties.put(ExtensionRegistry.APP_ROOT_DIR_PROPERTY_KEY, Config.getInstance().getProperty("plugins.path", ""));
		GlobalConfiguration.initialize(workloadProperties);
		IExtensionRegistry extensionRegistry = ExtensionRegistry.getSingleton();
		final Collection<? extends IExtension> extensions = extensionRegistry.getExtensions();
		for (final IExtension ext : extensions) {
			if (IWorkloadAdapter.class.isAssignableFrom(ext.getExtensionArtifactClass())) {
					workloadExtensions.add(ext);
			}
		}
	}
	
	public void setWorkloadAdapter(AbstractWorkloadAdapter adapter, Properties properties) {
		selectedWorkloadAdapter = adapter;
		selectedWorkloadAdapter.setProperties(properties);
		try {
			selectedWorkloadAdapter.initialize();
		} catch (WorkloadException e) {
			e.printStackTrace();
		}
	}
	
	public List<IExtension> getAvailableWorkloadAdapter() {
		return workloadExtensions;
	}

	public boolean connectToAIM(String host, String port) {
		if (host.isEmpty() || host == null || port.isEmpty() || port == null) {
			notifyObservers(new ConnectionStateMessage("Host or port for the connection to AIM is empty."));
			return false;
		}
		try {
			if (!JMXAdaptiveInstrumentationClient.testConnection(host, port)) {
				notifyObservers(new ConnectionStateMessage(String.format("Can't connect to AIM on %s:%s", host, port)));
				return false;
			}
			instrumentationClient = new JMXAdaptiveInstrumentationClient(host, port);
		} catch (Exception e) {
		}
		return instrumentationClient.testConnection();
	}
	
	public boolean isConnectedToAIM() {
		if (instrumentationClient == null) {
			return false;
		} else {
			return instrumentationClient.testConnection();
		}
	}
	
	public void disconnectFromAIM() {
		try {
			if (instrumentationClient != null) {
				instrumentationClient.disableMonitoring();
				instrumentationClient.uninstrument();				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		instrumentationClient = null;
	}
	
	public boolean connectToRserve(String host, String port) {
		if (host.isEmpty() || host == null || port.isEmpty() || port == null) {
			notifyObservers(new ConnectionStateMessage("Host or port for the connection to Rserve is empty."));
			return false;
		}
		try {
			if (rAdapter == null) {
				rAdapter = new RAdapter();
			}
			rAdapter.connect(host, Integer.parseInt(port));		
			return rAdapter.isConnected();	
		} catch (RserveException e) {
			notifyObservers(new ConnectionStateMessage(String.format("Can't connect to Rserve on %s:%s", host, port)));
		}
		return false;
	}
	
	public boolean isConnectedToRserve() {
		if (rAdapter == null) {
			return false;
		}
		return rAdapter.isConnected();
	}
	
	public void disconnectFromRserve() {
		if (rAdapter != null) {
			rAdapter.disconnect();
		}
	}
	
	public void startMeasurement(
			final int warmupDurationInS, 
			final int measurementDurationInS, 
			List<String> methodPatterns
			) {		
		List<String> newMethodPatterns = new ArrayList<>();
		for (String method : methodPatterns) {
			if (!method.endsWith("*")) {
				method = method + "*";
			}
			newMethodPatterns.add(method);
		}
		methodPatterns = newMethodPatterns;
		notifyObservers(new MeasurementStateMessage(MeasurementState.STARTING, "Warming up..."));
		MeasurementRunner runner = createRunnableMeasurement(warmupDurationInS, measurementDurationInS, methodPatterns);
		measurementThreadPool = Executors.newCachedThreadPool().submit(runner);
	}

	private MeasurementRunner createRunnableMeasurement(final int warmupDurationInS, final int measurementDurationInS,
			final List<String> methodPatterns) {
		MeasurementRunner runner = new MeasurementRunner();
		runner.setWarmupDurationInS(warmupDurationInS);
		runner.setMeasurementDuration(measurementDurationInS);
		runner.setWorkloadAdapter(selectedWorkloadAdapter);
		runner.setMethodPatterns(methodPatterns);
		runner.setInstrumentationClient(instrumentationClient);
		runner.initialize();
		runner.addObserver(this);
		return runner;
	}
		
	public void appendResults(String pattern, MeasurementData measurementData) {
		results.put(pattern, measurementData);
	}

	private void createResults() {
		if (measurementThreadPool != null) {
			measurementThreadPool.cancel(true);
		}
		ResultCalculator resultCalc = new ResultCalculator();
		resultCalc.setFileProcessor(fileProcessor);
		resultCalc.setRAdapter(rAdapter);
		for (String method : results.keySet()) {
			notifyObservers(new MeasurementStateMessage(MeasurementState.CALCULATING, String.format("Now calculating results for %s.", method)));
			resultCalc.calculateResourceDemand(method, results.get(method));
		}
		/*
		notifyObservers(new ResultMessage(resultLines));
		*/
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		if (arg0 instanceof MeasurementRunner) {
			if (arg1 instanceof MeasurementStateMessage){
				MeasurementStateMessage msg = (MeasurementStateMessage) arg1;
				notifyObservers(msg);
				if (msg.getMeasurementState() == MeasurementState.STOPPING) {
					createResults();
				}
			}
		}
	}
	
	@Override
	public void notifyObservers(Object o) {
		setChanged();
		super.notifyObservers(o);
	}
}
