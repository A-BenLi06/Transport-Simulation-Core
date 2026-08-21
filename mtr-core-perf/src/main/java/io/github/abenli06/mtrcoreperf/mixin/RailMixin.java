package io.github.abenli06.mtrcoreperf.mixin;

import org.mtr.core.data.Client;
import org.mtr.core.data.Rail;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Removes the per-tick, per-rail rebuild of the signal reservation state.
 *
 * <p>Stock {@code tick1} does two things that cost more than they need to, for every rail in the
 * network, on every simulation tick:</p>
 *
 * <ul>
 * <li>{@code Utilities.sameItems} is {@code containsAll} in both directions, i.e. two O(n log n)
 *     tree traversals, to answer a question that a single ordered walk answers in O(n). Both
 *     operands are key sets of a {@link Long2LongAVLTreeMap} and are therefore already sorted,
 *     so equal size plus element-wise equality is exactly set equality.</li>
 * <li>{@code old.clear(); old.putAll(current); current.clear();} rebuilds an AVL tree from
 *     scratch, allocating one node per entry. The old map is about to be discarded anyway, so the
 *     two maps can simply trade places and the recycled one be cleared.</li>
 * </ul>
 *
 * <p>The lambda passed to {@code clients.forEach} also captured {@code this} and
 * {@code needsUpdate}, allocating one object per rail per tick; a plain loop does not.</p>
 *
 * <p>Observable behaviour is unchanged: {@code needsUpdate} is computed from the same two set
 * comparisons, before the buffers are swapped, and every client that was updated before is still
 * updated with the same argument.</p>
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailMixin {

	@Shadow @Final @Mutable private Long2LongAVLTreeMap preBlockedVehicleIds;
	@Shadow @Final @Mutable private Long2LongAVLTreeMap currentlyBlockedVehicleIds;
	@Shadow @Final @Mutable private Long2LongAVLTreeMap preBlockedVehicleIdsOld;
	@Shadow @Final @Mutable private Long2LongAVLTreeMap currentlyBlockedVehicleIdsOld;

	@Shadow
	public abstract boolean closeTo(org.mtr.core.data.Position position, double radius);

	/**
	 * @author A-BenLi06
	 * @reason Swap the reservation buffers instead of copying them, and compare the key sets in a
	 *         single ordered pass instead of two {@code containsAll} calls.
	 */
	@Overwrite
	public void tick1(Simulator simulator) {
		final boolean needsUpdate = mtrcoreperf$keysDiffer(preBlockedVehicleIds, preBlockedVehicleIdsOld)
				|| mtrcoreperf$keysDiffer(currentlyBlockedVehicleIds, currentlyBlockedVehicleIdsOld);

		for (final Client client : simulator.clients) {
			if (closeTo(client.getPosition(), client.getUpdateRadius())) {
				client.update((Rail) (Object) this, needsUpdate);
			}
		}

		Long2LongAVLTreeMap recycled = preBlockedVehicleIdsOld;
		preBlockedVehicleIdsOld = preBlockedVehicleIds;
		preBlockedVehicleIds = recycled;
		preBlockedVehicleIds.clear();

		recycled = currentlyBlockedVehicleIdsOld;
		currentlyBlockedVehicleIdsOld = currentlyBlockedVehicleIds;
		currentlyBlockedVehicleIds = recycled;
		currentlyBlockedVehicleIds.clear();
	}

	/**
	 * @author A-BenLi06
	 * @reason Avoid building a {@code LongStream} and a capturing predicate per call; this runs
	 *         once per blocked-colour map per signal test, i.e. several times per vehicle per tick.
	 */
	@Overwrite
	private static boolean isNotBlocked(Long2LongAVLTreeMap blockedVehicleIds, long vehicleId) {
		final LongIterator iterator = blockedVehicleIds.values().iterator();
		while (iterator.hasNext()) {
			if (iterator.nextLong() != vehicleId) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Set equality for two sorted long key sets. Equivalent to
	 * {@code !Utilities.sameItems(a.keySet(), b.keySet())}.
	 */
	@Unique
	private static boolean mtrcoreperf$keysDiffer(Long2LongAVLTreeMap a, Long2LongAVLTreeMap b) {
		if (a.size() != b.size()) {
			return true;
		}
		final LongIterator iteratorA = a.keySet().iterator();
		final LongIterator iteratorB = b.keySet().iterator();
		while (iteratorA.hasNext()) {
			if (iteratorA.nextLong() != iteratorB.nextLong()) {
				return true;
			}
		}
		return false;
	}
}
