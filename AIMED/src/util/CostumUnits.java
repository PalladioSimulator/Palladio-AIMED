package util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import javax.measure.unit.SystemOfUnits;
import javax.measure.unit.Unit;

/**
 * This class adds the unit of resource demands to JScience.
 * @author Marcel M�ller
 *
 */
public class CostumUnits extends SystemOfUnits {
	private static HashSet<Unit<?>> UNITS = new HashSet();

	private static final CostumUnits INSTANCE = new CostumUnits();
	
	public static final Unit<Duration> ResourceDemand = addUnit(SI.SECOND.asType(Duration.class));

	public static CostumUnits getInstance() {
		return INSTANCE;
	}

	@Override
	public Set<Unit<?>> getUnits() {
		return Collections.unmodifiableSet(UNITS);
	}

	private static <U extends Unit<?>> U addUnit(U unit) {
		UNITS.add(unit);
		return unit;
	}
}