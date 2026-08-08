package com.yuuniverse.mtrcoreperf.mixin;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.mtr.core.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.yuuniverse.mtrcoreperf.MtrCorePerf;

/**
 * Makes the threaded simulation's tick interval configurable.
 *
 * <p>When {@code useThreadedSimulation} is enabled in MTR's server config, the simulation runs on
 * its own scheduled thread at a fixed 10 ms period — 100 ticks per second, five times the rate of
 * the Minecraft server thread it feeds. On a large network that is the dominant CPU cost of the
 * mod, and most of it buys nothing observable.</p>
 *
 * <p>{@code Main.MILLISECONDS_PER_TICK} is a compile-time constant, so it is inlined at the call
 * site and cannot be changed by writing the field; the schedule call itself has to be
 * intercepted. That call lives in the lambda the constructor passes to {@code simulators.forEach},
 * hence the synthetic method name below — if a future MTR build renames it, {@code require = 0}
 * makes this a no-op instead of a startup crash.</p>
 *
 * <p>Default is 10 ms, i.e. byte-for-byte stock behaviour. Set
 * {@code -Dmtr.simulationTickMillis=50} to run the simulation at 20 TPS. Has no effect when
 * threaded simulation is off, because then nothing is scheduled at all.</p>
 */
@Mixin(value = Main.class, remap = false)
public class MainMixin {

	@Redirect(
			method = "lambda$new$0",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/concurrent/ScheduledExecutorService;scheduleAtFixedRate(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
			),
			remap = false,
			require = 0
	)
	private ScheduledFuture<?> mtrcoreperf$applyConfiguredTickInterval(
			ScheduledExecutorService executor, Runnable command, long initialDelay, long period, TimeUnit unit) {
		return executor.scheduleAtFixedRate(command, initialDelay, MtrCorePerf.simulationTickMillis(period), unit);
	}
}
