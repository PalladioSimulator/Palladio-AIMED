package util;

import org.palladiosimulator.pcm.seff.AbstractAction;

/**
 * This class is a help class to save the begin and end of an interval enclosing an Internal Action.
 * @author Marcel Müller
 *
 */
public class ResourceDemandingInterval {
	public AbstractAction begin;
	public AbstractAction end;
	public ResourceDemandingInterval() {
	}
	public ResourceDemandingInterval(AbstractAction begin, AbstractAction end) {
		this.begin = begin;
		this.end = end;
	}
}
