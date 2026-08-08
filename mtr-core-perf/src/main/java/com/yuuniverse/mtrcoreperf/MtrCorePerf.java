package com.yuuniverse.mtrcoreperf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.common.Mod;

/**
 * Entry point for the MTR Core performance patches.
 *
 * <p>The mod carries no content of its own — everything it does lives in mixins applied to the
 * Transport Simulation Core classes that ship inside the MTR jar
 * ({@code org.mtr.core.*}). Those classes are plain and unobfuscated, so none of the mixins
 * participate in Minecraft's remapping.</p>
 */
@Mod(MtrCorePerf.MOD_ID)
public class MtrCorePerf {

	public static final String MOD_ID = "mtrcoreperf";

	/** JVM flag, e.g. {@code -Dmtr.simulationTickMillis=50}. */
	public static final String SIMULATION_TICK_MILLISECONDS_PROPERTY = "mtr.simulationTickMillis";

	private static final Logger LOGGER = LoggerFactory.getLogger("MtrCorePerf");
	private static final long MIN_SIMULATION_TICK_MILLISECONDS = 10;
	private static final long MAX_SIMULATION_TICK_MILLISECONDS = 1000;

	private static Long resolvedTickMillis;

	public MtrCorePerf() {
		LOGGER.info("MTR Core performance patches loaded");
	}

	/**
	 * Resolve the configured simulation tick interval, clamped to a sane range.
	 *
	 * @param stockPeriod the interval MTR itself was about to use, returned unchanged when the
	 *                    property is absent or unparseable
	 */
	public static synchronized long simulationTickMillis(long stockPeriod) {
		if (resolvedTickMillis == null) {
			resolvedTickMillis = resolve(stockPeriod);
			if (resolvedTickMillis != stockPeriod) {
				LOGGER.info("Simulation tick interval set to {} ms (stock is {} ms)", resolvedTickMillis, stockPeriod);
			}
		}
		return resolvedTickMillis;
	}

	private static long resolve(long stockPeriod) {
		final String configured = System.getProperty(SIMULATION_TICK_MILLISECONDS_PROPERTY);
		if (configured == null || configured.isEmpty()) {
			return stockPeriod;
		}
		try {
			final long parsed = Long.parseLong(configured.trim());
			return Math.max(MIN_SIMULATION_TICK_MILLISECONDS, Math.min(MAX_SIMULATION_TICK_MILLISECONDS, parsed));
		} catch (NumberFormatException e) {
			LOGGER.warn("Ignoring unparseable {}={}", SIMULATION_TICK_MILLISECONDS_PROPERTY, configured);
			return stockPeriod;
		}
	}
}
