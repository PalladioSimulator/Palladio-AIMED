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
		splitMeasurementData(data.getRecords());
		ResourceDemandingSEFF seff = fileProcessor.getSeff(methodName);
		for (List<ResponseTimeRecord> records : measurementsList) {
			calculateInvestigatedResponseTime(seff, records);
		}
	}

	private void splitMeasurementData(List<AbstractRecord> measurementRecords) {
		measurementsList = new ArrayList<>();
		List<ResponseTimeRecord> oneMeasurement = new ArrayList<>();
		long prevCallId = -1;
		for (AbstractRecord record : measurementRecords) {
			if (prevCallId == -1) {
				oneMeasurement.add((ResponseTimeRecord) record);
				prevCallId = record.getCallId();
			} else if (prevCallId > record.getCallId()) {
				oneMeasurement.add((ResponseTimeRecord) record);
				prevCallId = record.getCallId();
			} else { // prevCallId <= record.getCallId()
				prevCallId = record.getCallId();
				measurementsList.add(oneMeasurement);
				oneMeasurement = new ArrayList<>();
				oneMeasurement.add((ResponseTimeRecord) record);
			}
		}
	}

	private void calculateInvestigatedResponseTime(ResourceDemandingSEFF seff, List<ResponseTimeRecord> records) {
		Amount<Duration> investigatedResponseTime = getInvestigatedMethodResponseTime(records);
		List<AbstractAction> actions = seff.getSteps_Behaviour();
		for (AbstractAction action : actions) {
			if (action instanceof LoopAction) {
				String methodName = fileProcessor.getMethodName(action);
				investigatedResponseTime.minus(getSumResponseTimeOfMethod(methodName, records));
			}
			if (action instanceof BranchAction) {
				String ifMethod = fileProcessor.getIfMethodName(action);
				String elseMethod = fileProcessor.getElseMethodName(action);
				investigatedResponseTime.minus(getBranchResponseTime(ifMethod, elseMethod, records));
			}
		}
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

	private Amount<Duration> getBranchResponseTime(String ifMethodName, String elseMethodName,
			List<ResponseTimeRecord> records) {
		Amount<Duration> result = Amount.valueOf(0, SI.NANO(SI.SECOND));
		if (elseMethodName == null || elseMethodName.isEmpty()) {
			result = getSumResponseTimeOfMethod(ifMethodName, records)
					.times(getCallPropabilityInWholeRecords(ifMethodName));
		} else {
			double ifMethodCount = getCallCount(ifMethodName, records);
			double elseMethodCount = getCallCount(elseMethodName, records);
			double sum = ifMethodCount + elseMethodCount;
			double ifMethodPropability = 0;
			double elseMethodPropability = 0;
			if (sum != 0) {
				ifMethodPropability = ifMethodCount / sum;
				elseMethodPropability = elseMethodCount / sum;
			}
			Amount<Duration> ifMethodResponseTime = getSumResponseTimeOfMethod(ifMethodName, records)
					.times(ifMethodPropability);
			Amount<Duration> elseMethodResponseTime = getSumResponseTimeOfMethod(elseMethodName, records)
					.times(elseMethodPropability);
			result = ifMethodResponseTime.plus(elseMethodResponseTime);
		}
		return result;
	}

	private int getCallCount(String methodName, List<ResponseTimeRecord> records) {
		int result = 0;
		for (ResponseTimeRecord record : records) {
			if (LpeStringUtils.patternMatches(record.getOperation(), methodName)) {
				result++;
			}
		}
		return result;
	}

	private double getCallPropabilityInWholeRecords(String methodName) {
		int callCount = 0;
		double result = 0;
		for (List<ResponseTimeRecord> records : measurementsList) {
			callCount += getCallCount(methodName, records);
		}
		if (measurementsList.size() != 0) {
			result = (double) callCount / (double) measurementsList.size();
		}
		return result;
	}

}
