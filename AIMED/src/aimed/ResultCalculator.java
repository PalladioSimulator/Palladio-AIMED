package aimed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
import org.palladiosimulator.pcm.seff.AbstractBranchTransition;
import org.palladiosimulator.pcm.seff.BranchAction;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.InternalAction;
import org.palladiosimulator.pcm.seff.LoopAction;
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
	private LinkedList<Amount<Duration>> prevTimeStamps;

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
		prevTimeStamps = new LinkedList<>();
		for (InternalAction ia : intervals.keySet()) {
			calculateResponseTimes(ia, intervals.get(ia));
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
				//TODO: Add only if is trace 1 method.
				measurementsList.add(oneMeasurement);
				oneMeasurement = new ArrayList<>();
			}
		}
	}
	
	private Map<InternalAction, ResourceDemandingInterval> getResourceDemandingIntervals(ResourceDemandingSEFF seff) {
		Map<InternalAction, ResourceDemandingInterval> result = new LinkedHashMap<>();
		List<AbstractAction> actions = seff.getSteps_Behaviour();
		ResourceDemandingInterval interval;
		for (AbstractAction action : actions) {
			if (action instanceof InternalAction) {
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
			return getExternalCallFromBranchBody((BranchAction) action);
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
	
	private ExternalCallAction getExternalCallFromBranchBody(BranchAction ba) {
		List<AbstractBranchTransition> abts = ba.getBranches_Branch();
		for (AbstractBranchTransition abt : abts) {
			List<AbstractAction> actions = abt.getBranchBehaviour_BranchTransition().getSteps_Behaviour();
			for (AbstractAction action : actions) {
				if (action instanceof ExternalCallAction) {
					return (ExternalCallAction) action;
				}
			}
		}
		return null;
	}
	
	private void calculateResponseTimes(InternalAction ia, ResourceDemandingInterval rdi) {
		Set<Amount<Duration>> responseTimes = new HashSet<>();
		for (List<ResponseTimeRecord> records : measurementsList) {
			responseTimes.add(getResponseTimePerRecord(rdi, records));	
		}
		responseTimesPerInternalAction.put(ia, responseTimes);
	}
	
	private Amount<Duration> getResponseTimePerRecord(ResourceDemandingInterval rdi, List<ResponseTimeRecord> records) {
		Amount<Duration> beginTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		Amount<Duration> endTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		Amount<Duration> prevTimeStamp;
		if (measurementsList.size() == prevTimeStamps.size()) {
			prevTimeStamp = prevTimeStamps.removeFirst();
		} else {
			prevTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		}
		if (rdi.begin instanceof StartAction) {
			beginTimeStamp = getMethodNanoTimestamp(methodName, records);
		}
		if (rdi.begin instanceof ExternalCallAction) {
			beginTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), records, prevTimeStamp);
			if (beginTimeStamp.getExactValue() == 0) {
				if (wasInABranch((ExternalCallAction) rdi.begin)) {
					rdi.begin = getOtherBranchExternalCall((ExternalCallAction) rdi.begin);
					beginTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), records, prevTimeStamp);
				}
			}
		}
		prevTimeStamp = beginTimeStamp.plus(Amount.valueOf("1 ns"));
		if (rdi.end instanceof ExternalCallAction) {
			endTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.end), records, prevTimeStamp);
			if (endTimeStamp.getExactValue() == 0) {
				if (wasInABranch((ExternalCallAction) rdi.end)) {
					rdi.end = getOtherBranchExternalCall((ExternalCallAction) rdi.end);
					endTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.end), records, prevTimeStamp);
				}
			}
		}
		if (rdi.end instanceof StopAction) {
			endTimeStamp = getMethodNanoTimestamp(methodName, records).plus(getResponseTimeByThisTimeStamp(getMethodNanoTimestamp(methodName, records), records));
		}
		
		if (rdi.begin instanceof ExternalCallAction) {
			if (wasInALoop((ExternalCallAction) rdi.begin)) {
				beginTimeStamp = getLastExternalCallTimeStamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), beginTimeStamp, endTimeStamp, records);
			}
			beginTimeStamp = beginTimeStamp.plus(getResponseTimeByThisTimeStamp(beginTimeStamp, records));
		}
		Amount<Duration> result = endTimeStamp.minus(beginTimeStamp);
		NanoResponseTimeRecord nsRec;
		for (ResponseTimeRecord record : records) {
			nsRec = (NanoResponseTimeRecord) record;
			if (nsRec.getNanoTimestamp() > beginTimeStamp.getExactValue() &&
					nsRec.getNanoTimestamp() < endTimeStamp.getExactValue()) {
				result = result.minus(Amount.valueOf(nsRec.getResponseTime(), SI.NANO(SI.SECOND)));
			}
		}
		prevTimeStamps.add(endTimeStamp);
		return result;
	}
	
	private boolean wasInALoop (ExternalCallAction eca){
		List<AbstractAction> actions = fileProcessor.getSeff(methodName).getSteps_Behaviour();
		for(AbstractAction action : actions) {
			if (action instanceof LoopAction) {
				LoopAction la = (LoopAction) action;
				List<AbstractAction> loopActions = la.getBodyBehaviour_Loop().getSteps_Behaviour();
				for (AbstractAction loopAction : loopActions) {
					if (loopAction.equals(eca)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private boolean wasInABranch (ExternalCallAction eca) {
		List<AbstractAction> actions = fileProcessor.getSeff(methodName).getSteps_Behaviour();
		for(AbstractAction action : actions) {
			if (action instanceof BranchAction) {
				BranchAction ba = (BranchAction) action;
				List<AbstractBranchTransition> abts = ba.getBranches_Branch();
				for (AbstractBranchTransition abt : abts) {
					List<AbstractAction> aas = abt.getBranchBehaviour_BranchTransition().getSteps_Behaviour();
					for (AbstractAction aa : aas) {
						if (aa instanceof ExternalCallAction) {
							if (aa.equals(eca)) {
								return true;
							}
						}
					}
					
				}
			}
		}
		return false;
	}
	
	private ExternalCallAction getOtherBranchExternalCall(ExternalCallAction eca) {
		List<AbstractAction> actions = fileProcessor.getSeff(methodName).getSteps_Behaviour();
		ExternalCallAction prevEca = eca;
		for(AbstractAction action : actions) {
			if (action instanceof BranchAction) {
				BranchAction ba = (BranchAction) action;
				List<AbstractBranchTransition> abts = ba.getBranches_Branch();
				for (AbstractBranchTransition abt : abts) {
					List<AbstractAction> aas = abt.getBranchBehaviour_BranchTransition().getSteps_Behaviour();
					for (AbstractAction aa : aas) {
						if (aa instanceof ExternalCallAction) {
							if (!aa.equals(eca)) {
								prevEca = (ExternalCallAction) aa;
							}
						}
					}					
				}
			}
		}
		return prevEca;
	}
	
	private Amount<Duration> getLastExternalCallTimeStamp(String methodPattern, Amount<Duration> beginTimeStamp, Amount<Duration> endTimeStamp, List<ResponseTimeRecord> records) {
		Amount<Duration> prevNanoTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		NanoResponseTimeRecord nRec;
		for (ResponseTimeRecord rec : records) {
			if (LpeStringUtils.patternMatches(rec.getOperation(), methodPattern)) {
				nRec = (NanoResponseTimeRecord) rec;
				if (nRec.getNanoTimestamp() >= beginTimeStamp.getExactValue() &&
						nRec.getNanoTimestamp() < endTimeStamp.getExactValue()) {
					prevNanoTimeStamp = Amount.valueOf(nRec.getNanoTimestamp(), SI.NANO(SI.SECOND));
				}
			}
 		}		
		return prevNanoTimeStamp;
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

	private Amount<Duration> getMethodNanoTimestamp(String methodName, List<ResponseTimeRecord> records, Amount<Duration> startAtTimeStamp) {
		NanoResponseTimeRecord nr;
		for (ResponseTimeRecord record : records) {
			if (LpeStringUtils.patternMatches(record.getOperation(), methodName)) {
				nr = (NanoResponseTimeRecord) record;
				if (nr.getNanoTimestamp() >= startAtTimeStamp.getExactValue()) {
					return Amount.valueOf(nr.getNanoTimestamp(), SI.NANO(SI.SECOND));
				}
			}
		}
		return Amount.valueOf(0, SI.NANO(SI.SECOND));
	}
	
	private Amount<Duration> getMethodNanoTimestamp(String methodName, List<ResponseTimeRecord> records) {
		return getMethodNanoTimestamp(methodName, records, Amount.valueOf(0, SI.NANO(SI.SECOND)));
	}
	
	private String getMethodDefinitionAndAddPlaceholder(ExternalCallAction eca) {
		String methodName = eca.getCalledService_ExternalService().getEntityName();
		String classDefinition = eca.getCalledService_ExternalService().getInterface__OperationSignature().getEntityName();
		int lastDot = classDefinition.lastIndexOf(".");
		classDefinition = classDefinition.substring(lastDot + 1);
		return "*" + classDefinition + "." + methodName + "*";
	}
}
