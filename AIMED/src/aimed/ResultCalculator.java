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
import javax.measure.unit.SI;

import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.artifacts.records.NanoResponseTimeRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.jscience.physics.amount.Amount;
import org.lpe.common.util.LpeStringUtils;
import org.palladiosimulator.pcm.core.PCMRandomVariable;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.AbstractBranchTransition;
import org.palladiosimulator.pcm.seff.BranchAction;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.InternalAction;
import org.palladiosimulator.pcm.seff.LoopAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.palladiosimulator.pcm.seff.StartAction;
import org.palladiosimulator.pcm.seff.StopAction;
import org.palladiosimulator.pcm.seff.seff_performance.ParametricResourceDemand;

import util.CostumUnits;
import util.RAdapter;
import util.ResourceDemandingInterval;

/**
 * This class handles all calculations of the records coming from AIM.
 * @author Marcel Müller
 *
 */
public class ResultCalculator {
	/**
	 * A list of measurements. It contains for each call on the method under
	 * study a own list.
	 */
	private List<List<ResponseTimeRecord>> measurementsList;

	/**
	 * The name of the method that is current under study.
	 */
	private String methodName;

	/**
	 * The instance of the class that contains the resources.
	 */
	private ResourceHandler resourceHandler;

	/**
	 * The adapter used for the creation of the doublePDF function.
	 */
	private RAdapter rAdapter;

	/**
	 * The processing rate of the CPU to transform response times to abstract
	 * work units.
	 */
	private long processingRateCpu = 1;

	/**
	 * These are the results for each resource demanding interval.
	 */
	private Map<InternalAction, Set<Amount<Duration>>> resourceDemandPerInternalAction;

	/**
	 * This list is used as stack to begin each calculation at the right time
	 * stamp;
	 */
	private LinkedList<Amount<Duration>> prevTimeStamps;

	/**
	 * Initializes the result calculator.
	 */
	public ResultCalculator() {
		resourceDemandPerInternalAction = new HashMap<>();
	}

	/**
	 * Sets the given resource handler.
	 * 
	 * @param resourceHandler
	 *            The given resource handler.
	 */
	public void setFileProcessor(ResourceHandler resourceHandler) {
		this.resourceHandler = resourceHandler;
	}

	/**
	 * Sets the given adapter to calculate the doublePDF string.
	 * 
	 * @param rAdapter
	 *            The given adapter.
	 */
	public void setRAdapter(RAdapter rAdapter) {
		this.rAdapter = rAdapter;
	}

	/**
	 * This function begins the calculation phase. The result is saved in the
	 * <code>resourceDemandPerInternalAction</code>.
	 * 
	 * @param methodName
	 *            The method under study.
	 * @param data
	 *            The measured data from AIM.
	 */
	public void calculateResourceDemand(String methodName, MeasurementData data) {
		this.methodName = methodName;
		splitMeasurementDataOnlyTrace1Methods(methodName, data.getRecords());
		ResourceDemandingSEFF seff = resourceHandler.getSeff(methodName);
		Map<InternalAction, ResourceDemandingInterval> intervals = getResourceDemandingIntervals(seff);
		prevTimeStamps = new LinkedList<>();
		for (InternalAction ia : intervals.keySet()) {
			calculateResponseTimes(ia, intervals.get(ia));
		}
		rAdapter.loadDefaultDhistSource();
		String doublePDF;
		for (InternalAction ia : resourceDemandPerInternalAction.keySet()) {
			Set<Amount<Duration>> bla = resourceDemandPerInternalAction.get(ia);
			doublePDF = rAdapter.doublePDF(bla);
			writeResourceDemandToInternalAction(ia, doublePDF);
		}
	}

	/**
	 * Write the calculated resource demands into the internal action.
	 * 
	 * @param ia
	 *            The Internal Action to be extended with the resource demands.
	 * @param resourceDemand
	 *            The doublePDF function of the resource demands to be inserted
	 *            into the Internal Action.
	 */
	private void writeResourceDemandToInternalAction(InternalAction ia, String resourceDemand) {
		List<ParametricResourceDemand> prds = ia.getResourceDemand_Action();
		PCMRandomVariable prv;
		for (ParametricResourceDemand prd : prds) {
			prv = prd.getSpecification_ParametericResourceDemand();
			prv.setSpecification(resourceDemand);
		}
	}

	/**
	 * Splits the measurement data to get a separated list for each call. At the
	 * same time, all methods, that are not directly called from the method
	 * under study, are removed.
	 * 
	 * @param methodName
	 *            The method under study.
	 * @param measurementRecords
	 *            The measurement data given from AIM.
	 */
	private void splitMeasurementDataOnlyTrace1Methods(String methodName, List<AbstractRecord> measurementRecords) {
		measurementsList = new ArrayList<>();
		List<String> trace1Methods = resourceHandler.getTrace1Methods(methodName);
		List<ResponseTimeRecord> oneMeasurement = new ArrayList<>();
		ResponseTimeRecord rtRec;
		for (AbstractRecord record : measurementRecords) {
			rtRec = (ResponseTimeRecord) record;
			if (LpeStringUtils.patternMatches(rtRec.getOperation(), methodName)) {
				// TODO: Add only if is trace 1 method.
				oneMeasurement.add(rtRec);
				measurementsList.add(oneMeasurement);
				oneMeasurement = new ArrayList<>();
			}
			if (isTrace1Method(rtRec.getOperation(), trace1Methods)) {
				oneMeasurement.add(rtRec);
			}
		}
	}

	/**
	 * Checks if the given method is directly called from the method under
	 * study.
	 * 
	 * @param operation
	 *            The method that could be directly called.
	 * @param trace1Methods
	 *            All methods that are directly called.
	 * @return Returns true if the method is directly called, else false.
	 */
	private boolean isTrace1Method(String operation, List<String> trace1Methods) {
		for (String method : trace1Methods) {
			if (LpeStringUtils.patternMatches(operation, method)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extracts the resource demanding intervals from the given RDSEFF. Each
	 * resource demanding interval encloses an Internal Action.
	 * 
	 * @param seff
	 *            The given RDSEFF to be separated into intervals.
	 * @return Returns a Map of Internal Actions and their enclosing resource
	 *         demanding interval.
	 */
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

	/**
	 * Searches for the interval limits enclosing the Internal Action.
	 * 
	 * @param ia
	 *            The given Internal Action to be enclosed.
	 * @return Returns a resource demanding interval enclosing an Internal
	 *         Action.
	 */
	private ResourceDemandingInterval getIntervalOfInternalAction(InternalAction ia) {
		ResourceDemandingInterval result = new ResourceDemandingInterval();
		result.begin = getIntervalBegin(ia);
		result.end = getIntervalEnd(ia);
		return result;
	}

	/**
	 * @return Returns the begin of an interval of a given Internal Action.
	 */
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

	/**
	 * @return Returns the end of an interval of a given Internal Action.
	 */
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

	/**
	 * At the current version, the interval borders are just Start/Stop Actions
	 * or External Call Actions. Loops and Branches are recursively searched for
	 * external calls.
	 * 
	 * @return Returns the action that is an interval begin or end.
	 */
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
		}
		if (action instanceof StopAction) {
			return action;
		}
		return null;
	}

	/**
	 * This function searches a External Call Action in the Body of a Loop
	 * Action.
	 * 
	 * @return Returns the ExternalCall if found, else it returns null.
	 */
	private ExternalCallAction getExternalCallFromLoopBody(LoopAction la) {
		List<AbstractAction> actions = la.getBodyBehaviour_Loop().getSteps_Behaviour();
		for (AbstractAction action : actions) {
			if (action instanceof ExternalCallAction) {
				return (ExternalCallAction) action;
			}
		}
		return null;
	}

	/**
	 * Searches an External Call Action in the body of a BranchAction.
	 * 
	 * @return returns the found External Call Action or null if no one is
	 *         found.
	 */
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

	/**
	 * Calculates the resource demands for each Internal Actions and its related
	 * resource demanding interval.
	 * 
	 * @param ia
	 *            The given Internal Action.
	 * @param rdi
	 *            The related resource demanding interval of the given Internal
	 *            Action.
	 */
	private void calculateResponseTimes(InternalAction ia, ResourceDemandingInterval rdi) {
		Set<Amount<Duration>> resourceDemands = new HashSet<>();
		Amount<Duration> responseTime;
		Amount<Duration> resourceDemand;
		for (List<ResponseTimeRecord> records : measurementsList) {
			responseTime = getResponseTimePerRecord(rdi, records);
			resourceDemand = responseTime.to(CostumUnits.ResourceDemand).times(processingRateCpu);
			resourceDemands.add(resourceDemand);
		}
		resourceDemandPerInternalAction.put(ia, resourceDemands);
	}

	/**
	 * This function calculates the absolute response time for each resource
	 * demanding interval.
	 * 
	 * @param rdi
	 *            The given resource demanding interval.
	 * @param records
	 *            The records to be used for calculations.
	 * @return Returns the response time of this resource demanding interval.
	 */
	private Amount<Duration> getResponseTimePerRecord(ResourceDemandingInterval rdi, List<ResponseTimeRecord> records) {
		Amount<Duration> beginTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		Amount<Duration> endTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		Amount<Duration> prevTimeStamp;
		// At the first time, the full measurements list is used, after that,
		// the beginning time stamp is read from the list.
		if (measurementsList.size() == prevTimeStamps.size()) {
			prevTimeStamp = prevTimeStamps.removeFirst();
		} else {
			prevTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		}
		if (rdi.begin instanceof StartAction) {
			beginTimeStamp = getMethodNanoTimestamp(methodName, records);
		}
		if (rdi.begin instanceof ExternalCallAction) {
			beginTimeStamp = getMethodNanoTimestamp(
					getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), records, prevTimeStamp);
			if (beginTimeStamp.getExactValue() == 0) {
				if (wasInABranch((ExternalCallAction) rdi.begin)) {
					rdi.begin = getOtherBranchExternalCall((ExternalCallAction) rdi.begin);
					beginTimeStamp = getMethodNanoTimestamp(
							getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), records,
							prevTimeStamp);
				}
			}
		}
		// Increases the prevTimeStamp with 1ns to not get the same records for the endTimeStamp.
		prevTimeStamp = beginTimeStamp.plus(Amount.valueOf("1 ns"));
		if (rdi.end instanceof ExternalCallAction) {
			endTimeStamp = getMethodNanoTimestamp(getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.end),
					records, prevTimeStamp);
			if (endTimeStamp.getExactValue() == 0) {
				if (wasInABranch((ExternalCallAction) rdi.end)) {
					rdi.end = getOtherBranchExternalCall((ExternalCallAction) rdi.end);
					endTimeStamp = getMethodNanoTimestamp(
							getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.end), records, prevTimeStamp);
				}
			}
		}
		if (rdi.end instanceof StopAction) {
			endTimeStamp = getMethodNanoTimestamp(methodName, records)
					.plus(getResponseTimeByThisTimeStamp(getMethodNanoTimestamp(methodName, records), records));
		}
		
		//If the External Call was in a loop, take the last call from the records.
		if (rdi.begin instanceof ExternalCallAction) {
			if (wasInALoop((ExternalCallAction) rdi.begin)) {
				beginTimeStamp = getLastExternalCallTimeStamp(
						getMethodDefinitionAndAddPlaceholder((ExternalCallAction) rdi.begin), beginTimeStamp,
						endTimeStamp, records);
			}
			beginTimeStamp = beginTimeStamp.plus(getResponseTimeByThisTimeStamp(beginTimeStamp, records));
		}
		//Calculates the response time of the given interval.
		Amount<Duration> result = endTimeStamp.minus(beginTimeStamp);
		//Removes all trace1Methods response times from the interval to get the absolute response time.
		NanoResponseTimeRecord nsRec;
		for (ResponseTimeRecord record : records) {
			nsRec = (NanoResponseTimeRecord) record;
			if (nsRec.getNanoTimestamp() > beginTimeStamp.getExactValue()
					&& nsRec.getNanoTimestamp() < endTimeStamp.getExactValue()) {
				result = result.minus(Amount.valueOf(nsRec.getResponseTime(), SI.NANO(SI.SECOND)));
			}
		}
		prevTimeStamps.add(endTimeStamp);
		return result;
	}

	/**
	 * @return Returns true if the External Call Actions was in the body of a Loop Action, else false.
	 */
	private boolean wasInALoop(ExternalCallAction eca) {
		List<AbstractAction> actions = resourceHandler.getSeff(methodName).getSteps_Behaviour();
		for (AbstractAction action : actions) {
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

	/**
	 * @return Returns true if the given External Call Action was in the body of a Branch Action, else false.
	 */
	private boolean wasInABranch(ExternalCallAction eca) {
		List<AbstractAction> actions = resourceHandler.getSeff(methodName).getSteps_Behaviour();
		for (AbstractAction action : actions) {
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

	/**
	 * If the External Call Action cannot be found in the current records, eventually it was in the other branch
	 * of a Branch Action.
	 * @return Returns the External Call Action of the other branch.
	 */
	private ExternalCallAction getOtherBranchExternalCall(ExternalCallAction eca) {
		List<AbstractAction> actions = resourceHandler.getSeff(methodName).getSteps_Behaviour();
		ExternalCallAction prevEca = eca;
		for (AbstractAction action : actions) {
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

	/**
	 * This method searches the last External Call Action from the body of a Loop Action.
	 * @param methodPattern The related methods of a External Call Action.
	 * @param beginTimeStamp The begin of the Loop Action.
	 * @param endTimeStamp The end of the Loop Action.
	 * @param records The records under examination.
	 * @return
	 */
	private Amount<Duration> getLastExternalCallTimeStamp(String methodPattern, Amount<Duration> beginTimeStamp,
			Amount<Duration> endTimeStamp, List<ResponseTimeRecord> records) {
		Amount<Duration> prevNanoTimeStamp = Amount.valueOf(0, SI.NANO(SI.SECOND));
		NanoResponseTimeRecord nRec;
		for (ResponseTimeRecord rec : records) {
			if (LpeStringUtils.patternMatches(rec.getOperation(), methodPattern)) {
				nRec = (NanoResponseTimeRecord) rec;
				if (nRec.getNanoTimestamp() >= beginTimeStamp.getExactValue()
						&& nRec.getNanoTimestamp() < endTimeStamp.getExactValue()) {
					prevNanoTimeStamp = Amount.valueOf(nRec.getNanoTimestamp(), SI.NANO(SI.SECOND));
				}
			}
		}
		return prevNanoTimeStamp;
	}

	/**
	 * 
	 * @param nanoTimeStamp The given time stamp.
	 * @param records The given records.
	 * @return Returns the response time of the given time stamp in this record.
	 */
	private Amount<Duration> getResponseTimeByThisTimeStamp(Amount<Duration> nanoTimeStamp,
			List<ResponseTimeRecord> records) {
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

	/**
	 * @param methodName The name of the given method.
	 * @param records The records to be examined.
	 * @param startAtTimeStamp Begin the search with this time stamp.
	 * @return Returns a time stamp of a given method.
	 */
	private Amount<Duration> getMethodNanoTimestamp(String methodName, List<ResponseTimeRecord> records,
			Amount<Duration> startAtTimeStamp) {
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

	/**
	 * @param methodName The name of the given method.
	 * @param records The records to be examined.
	 * @return Returns a time stamp of a given method.
	 */
	private Amount<Duration> getMethodNanoTimestamp(String methodName, List<ResponseTimeRecord> records) {
		return getMethodNanoTimestamp(methodName, records, Amount.valueOf(0, SI.NANO(SI.SECOND)));
	}

	/**
	 * @return Returns the class name and the method name of the given External Call Action.
	 */
	private String getMethodDefinitionAndAddPlaceholder(ExternalCallAction eca) {
		String methodName = eca.getCalledService_ExternalService().getEntityName();
		String classDefinition = eca.getCalledService_ExternalService().getInterface__OperationSignature()
				.getEntityName();
		int lastDot = classDefinition.lastIndexOf(".");
		classDefinition = classDefinition.substring(lastDot + 1);
		return "*" + classDefinition + "." + methodName + "*";
	}
}
