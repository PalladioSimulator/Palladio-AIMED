package aimed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.palladiosimulator.pcm.seff.InternalAction;
import org.palladiosimulator.pcm.seff.LoopAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingInternalBehaviour;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.palladiosimulator.pcm.seff.StartAction;
import org.palladiosimulator.pcm.seff.StopAction;

import util.ResourceDemandingInterval;

public class ResultCalculator {
	private List<List<ResponseTimeRecord>> measurementsList;
	private String methodName;
	private FileProcessor fileProcessor;
	private Amount<Frequency> throughputCpu = Amount.valueOf(0, SI.HERTZ);
	private Amount<Frequency> throughputHdd = Amount.valueOf(0, SI.HERTZ);
	private Map<InternalAction, Set<Amount<Duration>>> responseTimesPerInternalAction;

	public ResultCalculator() {
		responseTimesPerInternalAction = new HashMap<>();
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
		Map<InternalAction, ResourceDemandingInterval> intervals = getResourceDemandingIntervals(seff);
		for (InternalAction ia : intervals.keySet()) {
			calculateResponseTimes(ia, intervals.get(ia));
		}
		
		/*Amount<Duration> methodResponseTime;
		for (List<ResponseTimeRecord> records : measurementsList) {
			methodResponseTime = calculateResponseTime(seff, records);
			System.out.println(methodName + ": " + methodResponseTime.doubleValue(SI.SECOND));
		}*/
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
	
	private Map<InternalAction, ResourceDemandingInterval> getResourceDemandingIntervals(ResourceDemandingSEFF seff) {
		Map<InternalAction, ResourceDemandingInterval> result = new HashMap<>();
		List<AbstractAction> actions = seff.getSteps_Behaviour();
		ResourceDemandingInterval interval;
		for (AbstractAction action : actions) {
			if (action instanceof InternalAction) {
				result = new HashMap<>();
				interval = getIntervalOfInternalAction((InternalAction) action);
				result.put((InternalAction) action, interval);
			}
		}
		return result;
	}
	
	private ResourceDemandingInterval getIntervalOfInternalAction(InternalAction ia) {
		ResourceDemandingInterval result = new ResourceDemandingInterval();
		result.begin = getIntervalBegin(ia);
		result.end = getIntervalEnd(ia);
		return result;
	}
	
	private AbstractAction getIntervalBegin(InternalAction ia) {
		AbstractAction temp = ia;
		while (temp != null) {
			temp = temp.getPredecessor_AbstractAction();
			if (getIntervalBorderAction(temp) != null) {
				return getIntervalBorderAction(temp);
			}
		}
		System.out.println("Interval Begin not found in getIntervalBegin");
		return null;
	}
	
	private AbstractAction getIntervalEnd(InternalAction ia) {
		AbstractAction temp = ia;
		while (temp != null) {
			temp = temp.getSuccessor_AbstractAction();
			if (getIntervalBorderAction(temp) != null) {
				return getIntervalBorderAction(temp);
			}
		}
		System.out.println("Interval End not found in getIntervalEnd");
		return null;
	}
	
	private AbstractAction getIntervalBorderAction(AbstractAction action) {
		if (action instanceof StartAction) {
			return action;
		}
		if (action instanceof ExternalCallAction) {
			return action;
		}
		if (action instanceof LoopAction) {
			return getExternalCallFromLoopBody((LoopAction) action);
		} 
		if (action instanceof BranchAction) {
			//TODO: Handle branch action if has external call
		}
		if (action instanceof StopAction) {
			return action;
		} 
		return null;
	}
	
	private ExternalCallAction getExternalCallFromLoopBody(LoopAction la) {
		List<AbstractAction> actions = la.getBodyBehaviour_Loop().getSteps_Behaviour();
		for (AbstractAction action : actions) {
			if (action instanceof ExternalCallAction) {
				return (ExternalCallAction) action;
			}
		}
		return null;
	}
	

	private void calculateResponseTimes(InternalAction ia, ResourceDemandingInterval rdi) {
		
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
