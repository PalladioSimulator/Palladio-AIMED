package util;

import org.palladiosimulator.pcm.seff.AbstractAction;

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
