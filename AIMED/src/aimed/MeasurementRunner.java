package aimed;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import org.aim.aiminterface.IAdaptiveInstrumentation;
import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.aim.description.builder.InstrumentationEntityBuilder;
import org.aim.ui.entities.RawInstrumentationEntity;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;

import messages.MeasurementState;
import messages.MeasurementStateMessage;

public class MeasurementRunner extends Observable implements Runnable {
	private AbstractWorkloadAdapter workloadAdapter;
	
	private IAdaptiveInstrumentation instrumentationClient;
	
	private int warmupDuration;
	
	private int measurementDuration;
	
	private List<String> methodPatterns;
	
	private boolean isInitialized = false;
	
	public MeasurementRunner() {
	}
	
	public void setWarmupDurationInS (int warmupDurationInS){
		if (warmupDurationInS < 0) {
			throw new IllegalArgumentException("Warmup duration cannot be negative.");
		}
		this.warmupDuration = warmupDurationInS;
	}
	
	public void setMeasurementDuration(int measurementDurationInS) {
		if (measurementDurationInS < 0) {
			throw new IllegalArgumentException("Measurement duration cannot be negative.");
		}
		this.measurementDuration = measurementDurationInS;
	}
	
	public void setWorkloadAdapter(AbstractWorkloadAdapter workloadAdapter) {
		if (workloadAdapter == null) {
			throw new IllegalArgumentException("Workload adapter cannot be null.");
		}
		this.workloadAdapter = workloadAdapter;
	}
	
	public void setMethodPatterns(List<String> methodPatterns) {
		if (methodPatterns.isEmpty() || methodPatterns == null) {
			throw new IllegalArgumentException("Method patterns cannot be null or empty.");
		}
		this.methodPatterns = methodPatterns;
	}
	
	public void setInstrumentationClient(IAdaptiveInstrumentation instrumentationClient) {
		if (instrumentationClient == null) {
			throw new IllegalArgumentException("Instrumentation client cannot be null.");
		}
		this.instrumentationClient = instrumentationClient;
	}
	
	public void initialize() {
		setWarmupDurationInS(this.warmupDuration);
		setMeasurementDuration(this.measurementDuration);
		setWorkloadAdapter(this.workloadAdapter);
		setMethodPatterns(this.methodPatterns);
		this.isInitialized = true;
	}
		
	private LoadConfig generateLoadConfig(int workloadDurationInS) {
		LoadConfig loadConfig = new LoadConfig();
		// Caused by the single user approach
		loadConfig.setNumUsers(1);
		loadConfig.setRampUpUsersPerInterval(1);
		loadConfig.setRampUpIntervalLength(1);
		loadConfig.setExperimentDuration(workloadDurationInS);
		loadConfig.setCoolDownIntervalLength(5);
		loadConfig.setCoolDownUsersPerInterval(1);
		return loadConfig;
	}
	
	private void warmUp() {
		if (warmupDuration == 0) {
			return;
		}
		LoadConfig loadConfig = generateLoadConfig(warmupDuration);
		try {
			workloadAdapter.startLoad(loadConfig);
			workloadAdapter.waitForFinishedLoad();
		} catch (WorkloadException e1) {
			e1.printStackTrace();
		}
	}
	
	private void runMeasurements() {
		notifyObservers(new MeasurementStateMessage(MeasurementState.RUNNING, "Starting Measurements..."));
		LoadConfig loadConfig = generateLoadConfig(measurementDuration);
		for (String pattern : methodPatterns) {
			try {				
				InstrumentationDescription instrumentationDescription = createInstrumentationDescription(pattern);
				notifyObservers(new MeasurementStateMessage(MeasurementState.RUNNING, String.format("Now measuring %s", pattern)));
				workloadAdapter.startLoad(loadConfig);
				
				instrumentationClient.instrument(instrumentationDescription);
				instrumentationClient.enableMonitoring();

				workloadAdapter.waitForFinishedLoad();

				instrumentationClient.disableMonitoring();
				MeasurementData result = instrumentationClient.getMeasurementData();
				AimedMainController.getInstance().appendResults(pattern, result);
				instrumentationClient.uninstrument();
				notifyObservers(new MeasurementStateMessage(MeasurementState.RUNNING,
						String.format("Finished measurement of %s.", pattern)));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		notifyObservers(new MeasurementStateMessage(MeasurementState.STOPPING, "Finished measurements."));
	}
	
	private InstrumentationDescription createInstrumentationDescription(String methodPattern) {
		//List<String> apiScopes = new ArrayList<String>(instrumentationClient.getSupportedExtensions().getApiScopeExtensions());
		List<String> customScopes = new ArrayList<String>(
				instrumentationClient.getSupportedExtensions().getCustomScopeExtensions());
		List<String> probes = new ArrayList<String>(
				instrumentationClient.getSupportedExtensions().getProbeExtensionsMapping().keySet());// [3] -> ResponsetimeProbe
		//List<String> sampler = new ArrayList<String>(instrumentationClient.getSupportedExtensions().getSamplerExtensions());

		InstrumentationDescriptionBuilder descBuilder = new InstrumentationDescriptionBuilder();
		RawInstrumentationEntity instEntity = new RawInstrumentationEntity();
		instEntity.setTraceScope(true); //set this to true for tracing
		//instEntity.setProbes(new String[] {probes.get(6)}); // NanoResponsetimeProbe
		instEntity.setScope(customScopes.get(0)); // 0 = MethodScope
		instEntity.setScopeSettings(new String[]{methodPattern});
		InstrumentationEntityBuilder entBuilder = descBuilder.newMethodScopeEntity(instEntity.getScopeSettings());
		entBuilder.addProbe(probes.get(6));
		entBuilder.enableTrace(); // uncomment this for no tracing
		entBuilder.entityDone();
		InstrumentationDescription instrumentationDescription = descBuilder.build();
		return instrumentationDescription;
	}
	
	@Override
	public void run() {
		if (isInitialized) {
			warmUp();
			runMeasurements();
		} else {
			final String message = "Measurement evnironment not initialized.";
			notifyObservers(new MeasurementStateMessage(MeasurementState.STOPPING, message));
			throw new IllegalStateException(message);
		}
	}
	
	@Override
	public void notifyObservers(Object o) {
		setChanged();
		super.notifyObservers(o);
	}

}
