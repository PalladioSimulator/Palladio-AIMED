package aimed;

import java.io.File;
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
import javax.measure.unit.SI;

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.jscience.physics.amount.Amount;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.ExtensionRegistry;
import org.lpe.common.extension.IExtension;
import org.lpe.common.extension.IExtensionRegistry;
import org.lpe.common.util.LpeStringUtils;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.exceptions.WorkloadException;

import messages.ConnectionStateMessage;
import messages.MeasurementState;
import messages.MeasurementStateMessage;
import messages.ResultMessage;

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
	}
	
	public void loadResources(String sourceCodeDecoratorFilePath) {
		fileProcessor = new FileProcessor();
		fileProcessor.loadResources(sourceCodeDecoratorFilePath);
	}
	
	public List<String> getSeffMethodNames() {
		return fileProcessor.getSeffMethods();
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
	
	private long calculateResponseTimeOfMethod(String completeMethodName, List<String> trace1Methods, MeasurementData measurementData) {
		long investigatedResponseTime = calculateMethodAverage(completeMethodName, measurementData);
		for (String method : trace1Methods) {
			investigatedResponseTime -= calculateMethodAverage(method, measurementData);
		}
		return investigatedResponseTime;
	}
	
	private long calculateMethodAverage(String methodPattern, MeasurementData measurementData) {
		List<AbstractRecord> records = measurementData.getRecords();
		long sum = 0;
		long patternsFound = 0;
		ResponseTimeRecord rtRec;
		for(AbstractRecord rec : records) {
			rtRec = (ResponseTimeRecord)rec;
			if(LpeStringUtils.patternMatches(rtRec.getOperation(), methodPattern)) {
				sum += rtRec.getResponseTime();
				patternsFound++;
			}
		}
		if (patternsFound == 0) {
			return -1;
		}
		return sum / patternsFound;
	}
	
	public void appendResults(String pattern, MeasurementData measurementData) {
		results.put(pattern, measurementData);
	}

	private void createResults() {
		ResultCalculator resultCalc = new ResultCalculator();
		resultCalc.setFileProcessor(fileProcessor);
		//TODO: Remove hard-coded throughput.
		resultCalc.setCPUThrougput(Amount.valueOf(2.4, SI.GIGA(SI.HERTZ)));
		for (String method : results.keySet()) {
			notifyObservers(new MeasurementStateMessage(MeasurementState.CALCULATING, String.format("Now calculating results for %s.", method)));
			resultCalc.calculateAndWriteResourceDemand(method, results.get(method));
		}
		String completeMethodName;
		List<String> traceMethods;
		List<String> resultLines = new ArrayList<>();
		long responseTime;
		for (String pattern : results.keySet()) {
			completeMethodName = getCompleteNameOfInvestigatedMethod(pattern, results.get(pattern));
			traceMethods = getTraceMethodsOfInvestigatedMethod(pattern, results.get(pattern));
			responseTime = calculateResponseTimeOfMethod(completeMethodName, traceMethods, results.get(pattern));
			resultLines.add(pattern + ": " + String.valueOf(responseTime) + " ns");
		}
		notifyObservers(new ResultMessage(resultLines));
	}
	
	private String getCompleteNameOfInvestigatedMethod(String methodPattern, MeasurementData measurementData) {
		List<AbstractRecord> records = measurementData.getRecords();
		ResponseTimeRecord rtRec;
		for (AbstractRecord rec : records) {
			rtRec = (ResponseTimeRecord)rec;
			if (LpeStringUtils.patternMatches(rtRec.getOperation(), methodPattern)) {
				return rtRec.getOperation();
			}
		}
		return "";
	}
	
	private List<String> getTraceMethodsOfInvestigatedMethod(String methodPattern, MeasurementData measurementData) {
		List<String> traceMethods = new ArrayList<>();
		List<AbstractRecord> records = measurementData.getRecords();
		ResponseTimeRecord rtRec;
		for (AbstractRecord rec : records) {
			rtRec = (ResponseTimeRecord)rec;
			if ( ! LpeStringUtils.patternMatches(rtRec.getOperation(), methodPattern)) {
				if (!traceMethods.contains(rtRec.getOperation())) {
					traceMethods.add(rtRec.getOperation());
				}
			}
		}
		return traceMethods;
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
