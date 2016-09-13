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
import org.aim.artifacts.records.NanoResponseTimeRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.jscience.physics.amount.Amount;
import org.lpe.common.util.LpeStringUtils;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.BranchAction;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.LoopAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingInternalBehaviour;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.palladiosimulator.pcm.seff.StopAction;

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

	public void calculateResourceDemand(String methodName, MeasurementData data) {
		this.methodName = methodName;
		splitMeasurementData(methodName, data.getRecords());
		ResourceDemandingSEFF seff = fileProcessor.getSeff(methodName);
		Amount<Duration> methodResponseTime;
		for (List<ResponseTimeRecord> records : measurementsList) {
			methodResponseTime = calculateResponseTime(seff, records);
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

	private Amount<Duration> calculateResponseTime(ResourceDemandingSEFF seff, List<ResponseTimeRecord> records) {
		Amount<Duration> resultingResponseTime = Amount.valueOf(0, SI.NANO(SI.SECOND));
		List<String> trace1Methods = fileProcessor.getTrace1Methods(methodName);
		List<AbstractAction> actions = seff.getSteps_Behaviour();
		Amount<Duration> intervalBeginTimeStamp = getMethodNanoTimestamp(methodName, records);
		Amount<Duration> intervalEndTimeStamp;
		for (AbstractAction action : actions) {
			//TODO: Save each interval separate
			if (action instanceof LoopAction) {
				LoopAction la = (LoopAction) action;
				intervalEndTimeStamp = getLoopActionBodyExternalCallTimestamp(la, records);
				resultingResponseTime = removeChildMethodResponseTimes(intervalBeginTimeStamp, intervalEndTimeStamp, records);				
				intervalBeginTimeStamp = intervalEndTimeStamp.plus(getResponseTimeByThisTimeStamp(intervalEndTimeStamp, records));
			} else 
			if (action instanceof ExternalCallAction) {
				ExternalCallAction eca = (ExternalCallAction) action;
				intervalEndTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder(eca), records);
				resultingResponseTime = removeChildMethodResponseTimes(intervalBeginTimeStamp, intervalEndTimeStamp, records);				
			} else
			if (action instanceof StopAction) {
				
			}
			//TODO: move Interval;
		}
		return resultingResponseTime;
	}
	
	private Amount<Duration> getResponseTimeByThisTimeStamp(Amount<Duration> nanoTimeStamp, List<ResponseTimeRecord> records) {
		NanoResponseTimeRecord nsRec;
		for (ResponseTimeRecord record : records) {
			nsRec = (NanoResponseTimeRecord) record;
			if (nsRec.getNanoTimestamp() == nanoTimeStamp.getExactValue()) {
				return Amount.valueOf(nsRec.getResponseTime(), SI.NANO(SI.SECOND));
			}
		}
		System.out.println("TimeStamp not found in getResponseTimeByThisTimeStamp");
		return Amount.valueOf(0, SI.NANO(SI.SECOND));
	}
	
	private Amount<Duration> removeChildMethodResponseTimes(Amount<Duration> intervalBeginTimeStamp, Amount<Duration> intervalEndTimeStamp, List<ResponseTimeRecord> records) {
		Amount<Duration> result = intervalEndTimeStamp.minus(intervalBeginTimeStamp);
		Amount<Duration> recordResponseTime;
		NanoResponseTimeRecord nsRec;
		for (ResponseTimeRecord record : records) {
			nsRec = (NanoResponseTimeRecord) record;
			if (nsRec.getNanoTimestamp() > intervalBeginTimeStamp.getExactValue() && 
					nsRec.getNanoTimestamp() <= intervalEndTimeStamp.getExactValue()) {
				result = result.minus(Amount.valueOf(nsRec.getResponseTime(), SI.NANO(SI.SECOND)));
			}
		}
		return result;
	}

	private Amount<Duration> getLoopActionBodyExternalCallTimestamp (LoopAction la, List<ResponseTimeRecord> records){
		List<AbstractAction> actions = la.getBodyBehaviour_Loop().getSteps_Behaviour();
		for (AbstractAction action : actions) {
			if (action instanceof ExternalCallAction) {
				ExternalCallAction eca = (ExternalCallAction) action;
				System.out.println(eca.getEntityName());
				return getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder(eca), records);
			}
		}
		return Amount.valueOf(0, SI.NANO(SI.SECOND));
	}
		
	private Amount<Duration> getMethodNanoTimestamp(String methodName, List<ResponseTimeRecord> records) {
		NanoResponseTimeRecord nr;
		for (ResponseTimeRecord record : records) {
			if (LpeStringUtils.patternMatches(record.getOperation(), methodName)) {
				nr = (NanoResponseTimeRecord) record;
				return Amount.valueOf(nr.getNanoTimestamp(), SI.NANO(SI.SECOND));
			}
		}
		System.out.println("Method not found in getMethodNanoTimestamp");
		return Amount.valueOf(0, SI.NANO(SI.SECOND));
	}
	
	private String getMethodDefinitionAndAddPlaceholder(ExternalCallAction eca) {
		String methodName = eca.getCalledService_ExternalService().getEntityName();
		String classDefinition = eca.getCalledService_ExternalService().getInterface__OperationSignature().getEntityName();
		int lastDot = classDefinition.lastIndexOf(".");
		classDefinition = classDefinition.substring(lastDot + 1);
		return "*" + classDefinition + "." + methodName + "*";
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
