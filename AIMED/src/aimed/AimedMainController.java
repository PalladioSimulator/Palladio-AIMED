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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.aim.description.builder.InstrumentationEntityBuilder;
import org.aim.ui.entities.RawInstrumentationEntity;
import org.jscience.physics.amount.Amount;
import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.ExtensionRegistry;
import org.lpe.common.extension.IExtension;
import org.lpe.common.extension.IExtensionRegistry;
import org.lpe.common.util.LpeStringUtils;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.jmeter.JMeterConfigKeys;
import org.spotter.ext.jmeter.workload.JMeterWorkloadClient;

import messages.ConnectionStateMessage;
import messages.MeasurementState;
import messages.MeasurementStateMessage;
import messages.ResultMessage;

public class AimedMainController extends Observable implements Observer {
	private static AimedMainController instance = null;

	private IAdaptiveInstrumentation instrumentationClient;
	
	private Map<String, MeasurementData> results = new HashMap<>();
	
	private String currentMethodPattern;
	
	private File kdmFile;
	
	private List<IExtension> workloadExtensions;
	
	private AbstractWorkloadAdapter selectedWorkloadAdapter;

	public static synchronized AimedMainController getInstance() {
		if (instance == null) {
			instance = new AimedMainController();
		}
		return instance;
	}

	private AimedMainController() {
		loadWorkloadAdapter();
		/*Amount<Duration> responseTime = Amount.valueOf(1006802401, SI.NANO(SI.SECOND));
		System.out.println(responseTime);*/
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
			setChanged();
			notifyObservers(new ConnectionStateMessage("Host or port is empty."));
			return false;
		}
		try {
			if (!JMXAdaptiveInstrumentationClient.testConnection(host, port)) {
				setChanged();
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
			final List<String> methodPatterns,
			final File kdmFile
			) {		
		this.kdmFile = kdmFile;
		
		setChanged();
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
	
	public String getCurrentMethodPattern() {
		return currentMethodPattern;
	}
	
	public void appendResults(String pattern, MeasurementData measurementData) {
		results.put(pattern, measurementData);
	}

	private void showResults() {
		KdmProcessor kdmProcessor = new KdmProcessor();
		kdmProcessor.parse(kdmFile);
		String completeMethodName;
		List<String> traceMethods;
		List<String> resultLines = new ArrayList<>();
		long responseTime;
		for (String pattern : results.keySet()) {
			completeMethodName = getCompleteNameOfInvestigatedMethod(pattern, results.get(pattern));
			traceMethods = getTraceMethodsOfInvestigatedMethod(pattern, results.get(pattern));
			traceMethods = kdmProcessor.getTrace1MethodPatterns(completeMethodName, traceMethods);
			responseTime = calculateResponseTimeOfMethod(completeMethodName, traceMethods, results.get(pattern));
			resultLines.add(pattern + ": " + String.valueOf(responseTime) + " ns");
		}
		setChanged();
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
				setChanged();
				notifyObservers(msg);
				if (msg.getMeasurementState() == MeasurementState.STOPPING) {
					showResults();
				}
			}
		}
	}
}
