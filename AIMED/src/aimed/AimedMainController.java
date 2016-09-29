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

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.ExtensionRegistry;
import org.lpe.common.extension.IExtension;
import org.lpe.common.extension.IExtensionRegistry;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.exceptions.WorkloadException;

import messages.ConnectionStateMessage;
import messages.MeasurementState;
import messages.MeasurementStateMessage;
import util.CostumUnits;

public class AimedMainController extends Observable implements Observer {
	private static AimedMainController instance = null;

	private IAdaptiveInstrumentation instrumentationClient;
	
	private Map<String, MeasurementData> results = new HashMap<>();
	
	private List<IExtension> workloadExtensions;
	
	private AbstractWorkloadAdapter selectedWorkloadAdapter;
	
	private FileProcessor fileProcessor;

	public static synchronized AimedMainController getInstance() {
		if (instance == null) {
			instance = new AimedMainController();
		}
		return instance;
	}

	private AimedMainController() {
		loadWorkloadAdapter();
		//CostumUnits test = CostumUnits.getInstance();
		//Amount<Dimensionless> processingRate = Amount.valueOf(1, CostumUnits.ProcessingRate);
		//Amount<Duration> bla = Amount.valueOf(100000000, SI.NANO(SI.SECOND)).times(Amount.valueOf(1, CostumUnits.ProcessingRate));
		//System.out.println(processingRate);
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
		workloadProperties.put(ExtensionRegistry.PLUGINS_FOLDER_PROPERTY_KEY, "C:/Users/Cel/Studium/Bachelor/Vorbereitung/Eclipse/git/DynamicSpotter-Extensions/org.spotter.ext.parent/target");
		workloadProperties.put(ExtensionRegistry.APP_ROOT_DIR_PROPERTY_KEY, "C:/Users/Cel/Studium/Bachelor/Vorbereitung/Eclipse/git/DynamicSpotter-Extensions/org.spotter.ext.parent/target");
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

	public boolean connectToMainagent(String host, String port) {
		if (host.isEmpty() || host == null || port.isEmpty() || port == null) {
			notifyObservers(new ConnectionStateMessage("Host or port is empty."));
			return false;
		}
		try {
			if (!JMXAdaptiveInstrumentationClient.testConnection(host, port)) {
				notifyObservers(new ConnectionStateMessage(String.format("Can't connect to %s:%s", host, port)));
				return false;
			}
			instrumentationClient = new JMXAdaptiveInstrumentationClient(host, port);
		} catch (Exception e) {
		}
		return instrumentationClient.testConnection();
	}
	
	public boolean isConnected() {
		if (instrumentationClient == null) {
			return false;
		} else {
			return instrumentationClient.testConnection();
		}
	}
	
	public void disconnectFromMainagent() {
		try {
		instrumentationClient.disableMonitoring();
		instrumentationClient.uninstrument();
		} catch (Exception e) {
			e.printStackTrace();
		}
		instrumentationClient = null;
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
		Future<?> future = Executors.newCachedThreadPool().submit(runner);
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
		ResultCalculator resultCalc = new ResultCalculator();
		resultCalc.setFileProcessor(fileProcessor);
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
