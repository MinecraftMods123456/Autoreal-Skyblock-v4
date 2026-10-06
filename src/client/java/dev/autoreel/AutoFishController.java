package dev.autoreel;

import java.util.Random;

/**
 * The auto-fishing brain. It never touches Minecraft classes: everything it needs to see or do
 * goes through {@link Game}. That keeps it small, easy to reason about, and easy to test.
 *
 * <pre>
 * IDLE --cast--> CASTING --bobber appears--> WAITING --bite--> BITE_DELAY --reel--> REELING --gone--> IDLE
 * </pre>
 *
 * The "particles running up to the rod" phase of vanilla fishing is simply the WAITING state:
 * nothing happens until the bobber itself reports a real bite.
 */
final class AutoFishController {

	interface Game {
		/** A fishing rod is in the main hand. */
		boolean holdingRod();

		/** The player currently has a bobber out in the world. */
		boolean bobberOut();

		/** The bobber is showing a bite right now. Called once per tick while waiting. */
		boolean fishBiting();

		/** Right-click with the rod: casts when there is no bobber, reels in when there is one. */
		void useRod();
	}

	// ---- Tuning. One tick = 1/20 of a second. ----

	/** Ignore the bobber this long after it lands, so the landing splash is never mistaken for a bite. */
	static final int BITE_WARMUP_TICKS = 25;

	/** Pause between the bite and reeling in. A hooked fish stays on for 20-40 ticks, so keep this well under 20. */
	static final int REACT_MIN = 4;
	static final int REACT_MAX = 9;

	/** Pause between a catch and the next cast. */
	static final int RECAST_MIN = 14;
	static final int RECAST_MAX = 26;

	/** How long to wait for the server to confirm a cast or a reel before assuming the click got lost. */
	static final int CONFIRM_TICKS = 50;

	/** If nothing bites for this long, reel in and recast (unsticks bobbers in bad spots). */
	static final int MAX_WAIT_TICKS = 20 * 60;

	enum State { IDLE, CASTING, WAITING, BITE_DELAY, REELING }

	private final Random random;
	private State state = State.IDLE;
	private int timer;
	private int bobberAge;

	AutoFishController() {
		this(new Random());
	}

	AutoFishController(Random random) {
		this.random = random;
		reset();
	}

	/** Start from scratch (called when auto fishing is switched on). */
	void reset() {
		state = State.IDLE;
		timer = 8; // short breather before the very first cast
		bobberAge = 0;
	}

	State state() {
		return state;
	}

	/** Advance one game tick. */
	void tick(Game game) {
		if (!game.holdingRod()) {
			// Not holding a rod: stand by. The server drops the bobber on its own in that case.
			state = State.IDLE;
			timer = Math.max(timer, 20);
			return;
		}

		switch (state) {
			case IDLE -> {
				if (game.bobberOut()) {
					// Already fishing (cast by hand, or we were switched on mid-cast).
					startWaiting();
				} else if (timer > 0) {
					timer--;
				} else {
					game.useRod(); // cast
					state = State.CASTING;
					timer = CONFIRM_TICKS;
				}
			}
			case CASTING -> {
				if (game.bobberOut()) {
					startWaiting();
				} else if (--timer <= 0) {
					goIdle(); // the cast got lost, try again
				}
			}
			case WAITING -> {
				if (!game.bobberOut()) {
					goIdle(); // bobber vanished (reeled by hand, too far away, ...)
					return;
				}
				bobberAge++;
				boolean biting = game.fishBiting(); // called every tick so the game side can track motion
				if (biting && bobberAge >= BITE_WARMUP_TICKS) {
					state = State.BITE_DELAY;
					timer = between(REACT_MIN, REACT_MAX);
				} else if (bobberAge >= MAX_WAIT_TICKS) {
					reel(game);
				}
			}
			case BITE_DELAY -> {
				if (!game.bobberOut()) {
					goIdle();
				} else if (--timer <= 0) {
					reel(game);
				}
			}
			case REELING -> {
				if (!game.bobberOut()) {
					goIdle(); // caught it (or the hook is gone): cast again shortly
				} else if (--timer <= 0) {
					// The reel click did not take. Go back to watching the bobber.
					state = State.WAITING;
					bobberAge = BITE_WARMUP_TICKS;
				}
			}
		}
	}

	private void startWaiting() {
		state = State.WAITING;
		bobberAge = 0;
	}

	private void goIdle() {
		state = State.IDLE;
		timer = between(RECAST_MIN, RECAST_MAX);
	}

	private void reel(Game game) {
		game.useRod();
		state = State.REELING;
		timer = CONFIRM_TICKS;
	}

	private int between(int min, int max) {
		return min + random.nextInt(max - min + 1);
	}
}
