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

import javax.measure.unit.UnitFormat;

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
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

	/**
	 * Client used for communication to AIM.
	 */
	private IAdaptiveInstrumentation instrumentationClient;
	
	/**
	 * Results added by MeasurementRunner
	 */
	private Map<String, MeasurementData> results = new HashMap<>();
	
	/**
	 * Loaded workloadExtensions
	 */
	private List<IExtension> workloadExtensions;
	
	/**
	 * The selected workload adapter
	 */
	private AbstractWorkloadAdapter selectedWorkloadAdapter;
	
	/**
	 * Instance of the class that loads the resources
	 */
	private FileProcessor fileProcessor;
	
	/**
	 * Instance of the class that converts the calculated response times to a histogram
	 */
	private RAdapter rAdapter;
	
	/**
	 * Thread future of the MeasurementRunner
	 */
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
	
	/**
	 * Initializes the costum units. Is required to rename the ResourceDemands to abstract work units.
	 */
	private void initializeCostumUnits() {
		CostumUnits costumUnit = CostumUnits.getInstance();
		UnitFormat.getInstance().label(CostumUnits.ResourceDemand, "AbstractWorkUnit");
		UnitFormat.getInstance().alias(CostumUnits.ResourceDemand, "AbstractWorkUnit");
	}
	
	/**
	 * Triggers fileProcessor to load the KDM and the PCM model and its connection, the source code decorator.
	 * @param sourceCodeDecoratorFilePath File path to the source code decorator.
	 */
	public void loadResources(String sourceCodeDecoratorFilePath) {
		fileProcessor = new FileProcessor();
		fileProcessor.loadResources(sourceCodeDecoratorFilePath);
	}
	
	/**
	 * This method extract the Name of the seffs and the compontent's name and connects them to a java style definition
	 * E.g., package.class.doX
	 * @return Returns a list of all existing methods in the resource model.
	 */
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
	
	/**
	 * Loads the workload extensions using the extension registry from dynamic spotter.
	 * Each found workload extension is added to the private member "workloadExtensions".
	 */
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
	
	/**
	 * Sets the workload adapter for later use.
	 * @param adapter The workload generation adapter
	 * @param properties The configuration of the workload adapter.
	 */
	public void setWorkloadAdapter(AbstractWorkloadAdapter adapter, Properties properties) {
		selectedWorkloadAdapter = adapter;
		selectedWorkloadAdapter.setProperties(properties);
		try {
			selectedWorkloadAdapter.initialize();
		} catch (WorkloadException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return Returns the loaded workload extensions.
	 */
	public List<IExtension> getAvailableWorkloadAdapter() {
		return workloadExtensions;
	}

	/**
	 * Connects to AIM using JMXAdaptiveInstrumentationClient
	 * @param host the host of AIM
	 * @param port the port of AIM
	 * @return returns if the connection was successful
	 */
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
	
	/**
	 * @return Returns if AIMED is connected to AIM
	 */
	public boolean isConnectedToAIM() {
		if (instrumentationClient == null) {
			return false;
		} else {
			return instrumentationClient.testConnection();
		}
	}
	
	/**
	 * Disonnects from AIM and disables monitoring and uninstruments AIM.
	 */
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
	
	/**
	 * Connects to the Rserve server to later convert the resource demands to a hist
	 * @param host Rserve host
	 * @param port Rserve port
	 * @return returns if the connection was successful
	 */
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
	
	/**
	 * @return Returns if the connection to Rserve was successful
	 */
	public boolean isConnectedToRserve() {
		if (rAdapter == null) {
			return false;
		}
		return rAdapter.isConnected();
	}
	
	/**
	 * Disconnects from Rserve without shutting down the Rserve server.
	 */
	public void disconnectFromRserve() {
		if (rAdapter != null) {
			rAdapter.disconnect();
		}
	}
	
	/**
	 * Starts AIMED to begin the measurements and calculations.
	 * @param warmupDurationInS The duration in seconds how long the workload should run without an instrumented system
	 * @param measurementDurationInS The duration seconds the workload adapter should run while AIM has instrumented the system
	 * @param methodPatterns The list of methods to be instrumented after each oher.
	 */
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
			if (!method.startsWith("*")) {
				method = "*" + method;
			}
			newMethodPatterns.add(method);
		}
		methodPatterns = newMethodPatterns;
		notifyObservers(new MeasurementStateMessage(MeasurementState.STARTING_MEASUREMENT, "Warming up..."));
		MeasurementRunner runner = createRunnableMeasurement(warmupDurationInS, measurementDurationInS, methodPatterns);
		measurementThreadPool = Executors.newCachedThreadPool().submit(runner);
	}

	/**
	 * Creates a runnable that can be submitted to a thread pool.
	 * @param warmupDurationInS
	 * @param measurementDurationInS
	 * @param methodPatterns
	 * @return Returns a submitable Runnable.
	 */
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
		
	/**
	 * Appends results to the collection of measurement results.
	 * @param pattern The name of the methods used as key in the map.
	 * @param measurementData The measured data as value.
	 */
	public void appendResults(String pattern, MeasurementData measurementData) {
		results.put(pattern, measurementData);
	}

	/**
	 * This method begins the calculation phase. It is called via an update of observed classes.
	 */
	private void createResults() {
		if (measurementThreadPool != null) {
			measurementThreadPool.cancel(true);
			measurementThreadPool = null;
		}
		ResultCalculator resultCalc = new ResultCalculator();
		resultCalc.setFileProcessor(fileProcessor);
		resultCalc.setRAdapter(rAdapter);
		for (String method : results.keySet()) {
			notifyObservers(new MeasurementStateMessage(MeasurementState.CALCULATING, String.format("Now calculating results for %s.", method)));
			resultCalc.calculateResourceDemand(method, results.get(method));
		}
		notifyObservers(new MeasurementStateMessage(MeasurementState.SAVING, "Saving resources."));
		fileProcessor.saveResources();
		
		notifyObservers(new MeasurementStateMessage(MeasurementState.ALL_FINISHED, "Finished."));
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
				if (msg.getMeasurementState() == MeasurementState.STOPPING_MEASUREMENT) {
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
