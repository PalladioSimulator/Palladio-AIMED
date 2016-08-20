package aimed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.quantity.Frequency;
import javax.measure.unit.SI;

import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.jscience.physics.amount.Amount;
import org.lpe.common.util.LpeStringUtils;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.BranchAction;
import org.palladiosimulator.pcm.seff.LoopAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingInternalBehaviour;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;

public class ResultCalculator {
	private List<List<ResponseTimeRecord>> measurementsList;
	private String methodName;
	private FileProcessor fileProcessor;
	private Amount<Frequency> throughputCpu = Amount.valueOf(0, SI.HERTZ);
	private Amount<Frequency> throughputHdd = Amount.valueOf(0, SI.HERTZ);

	public ResultCalculator() {

	}

	public void setFileProcessor(FileProcessor fileProcessor) {
		this.fileProcessor = fileProcessor;
	}
	
	public void setCPUThrougput(Amount<Frequency> throughputCpu) {
		this.throughputCpu = throughputCpu;
	}
	
	public void setHDDThroughput(Amount<Frequency> throughputHdd) {
		this.throughputHdd = throughputHdd;
	}

	public void calculateAndWriteResourceDemand(String methodName, MeasurementData data) {
		this.methodName = methodName;
		splitMeasurementData(methodName, data.getRecords());
		ResourceDemandingSEFF seff = fileProcessor.getSeff(methodName);
		Amount<Duration> methodResponseTime;
		for (List<ResponseTimeRecord> records : measurementsList) {
			methodResponseTime = calculateInvestigatedResponseTime(seff, records);
			System.out.println(methodName + ": " + methodResponseTime.doubleValue(SI.SECOND));
		}
	}

	private void splitMeasurementData(String methodName, List<AbstractRecord> measurementRecords) {
		measurementsList = new ArrayList<>();
		List<ResponseTimeRecord> oneMeasurement = new ArrayList<>();
		ResponseTimeRecord rtRec;
		for (AbstractRecord record : measurementRecords) {
			rtRec = (ResponseTimeRecord) record;
			oneMeasurement.add(rtRec);
			if (LpeStringUtils.patternMatches(rtRec.getOperation(), methodName)) {
				measurementsList.add(oneMeasurement);
				oneMeasurement = new ArrayList<>();
			}
		}
	}

	private Amount<Duration> calculateInvestigatedResponseTime(ResourceDemandingSEFF seff, List<ResponseTimeRecord> records) {
		Amount<Duration> investigatedResponseTime = getInvestigatedMethodResponseTime(records);
		List<String> trace1Methods = fileProcessor.getTrace1Methods(methodName);
		
		return investigatedResponseTime;
	}

	private Amount<Duration> getInvestigatedMethodResponseTime(List<ResponseTimeRecord> records) {
		for (ResponseTimeRecord record : records) {
			if (LpeStringUtils.patternMatches(record.getOperation(), methodName)) {
				return Amount.valueOf(record.getResponseTime(), SI.NANO(SI.SECOND));
			}
		}
		return Amount.valueOf(0, SI.SECOND);
	}
	
	private Amount<Duration> getSumResponseTimeOfMethod(String methodName, List<ResponseTimeRecord> records) {
		Amount<Duration> result = Amount.valueOf(0, SI.NANO(SI.SECOND));
		for (ResponseTimeRecord record : records) {
			if (LpeStringUtils.patternMatches(record.getOperation(), methodName)) {
				result.plus(Amount.valueOf(record.getResponseTime(), SI.NANO(SI.SECOND)));
			}
		}
		return result;
	}
}
