import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BlackboardWolf implements Wolf {

    // -------------------------------------------
    //  A "prey chase record" on the blackboard
    // -------------------------------------------
    static class PreySighting {
        int relRow;
        int relCol;
        long time;      // e.g. the time (tick) of the latest known location
        boolean isChased;
        long chaseDeadline; // When does the chase end?

        public PreySighting(int r, int c, long t, boolean chased, long deadline) {
            this.relRow         = r;
            this.relCol         = c;
            this.time           = t;
            this.isChased       = chased;
            this.chaseDeadline  = deadline;
        }
    }

    // -------------------------------------------
    //  Shared blackboard for all wolves
    //  Typically just store a single active chase
    //  record. But we allow multiple for extension.
    // -------------------------------------------
    private static List<PreySighting> blackboard = Collections.synchronizedList(new ArrayList<>());

    // Each wolf’s local state
    private boolean iAmManager  = false;
    private boolean inChaseMode = false;

    // The local “best known location” and time we learned it
    private int  bestPreyRow    = 0;
    private int  bestPreyCol    = 0;
    private long bestPreyTime   = -1;

    // We’ll track a global time for demonstration 
    // (could also come from the environment).
    private static long globalTime = 0;

    // A parameter controlling how long we chase one prey
    private static final long CHASE_DURATION = 50; 
    // You can adjust this based on your environment speed.

    @Override
    public int[] moveAll(List<int[]> wolvesSight, List<int[]> preysSight) {
        globalTime++;

        // 1) Clean up or check if an active chase has ended
        //    If the existing chase is beyond the deadline, mark isChased=false
        expireOldChases();

        // 2) Sort by distance so we can pick the *closest* prey if we see any
        preysSight.sort(Comparator.comparingInt(o -> manhattanDistance(o[0], o[1], 0, 0)));

        if (!preysSight.isEmpty()) {
            // We see a prey. If no chase is active, we start a new chase as manager
            PreySighting active = getActiveChase();
            if (active == null) {
                // Start a new chase
                int[] closestPrey = preysSight.get(0);
                bestPreyRow       = closestPrey[0];
                bestPreyCol       = closestPrey[1];
                bestPreyTime      = globalTime;

                iAmManager        = true;
                inChaseMode       = true;

                // Add a new chase record to blackboard
                long newDeadline  = globalTime + CHASE_DURATION;
                blackboard.add(
                    new PreySighting(bestPreyRow, bestPreyCol, bestPreyTime,
                                     true, newDeadline)
                );
            } else {
                // If we do see a prey, but there's already an active chase,
                // maybe we ignore it, or we can refine the existing chase 
                // if *this* wolf is the manager. For example:

                if (iAmManager) {
                    // I'm the manager, so I can update the blackboard with the 
                    // prey's new location each tick. 
                    // Only if we know it's the same prey (assuming it hasn't changed
                    // drastically, maybe the prey moves at most 1 step).
                    int[] closestPrey = preysSight.get(0);
                    if (distanceIsReasonable(active, closestPrey)) {
                        active.relRow = closestPrey[0];
                        active.relCol = closestPrey[1];
                        active.time   = globalTime;
                        // We keep isChased=true until the deadline 
                        // or until we decide we’ve captured it.
                    }
                }

                // We might also join the chase if we’re not already in chase mode
                if (!inChaseMode) {
                    inChaseMode = true;
                }
            }
        }

        // 3) If we do not see a prey ourselves, we might learn from the blackboard
        //    if we are in “communication range” of other wolves
        if (wolvesInCommunicationRange(wolvesSight, 1)) {
            PreySighting active = getActiveChase();
            if (active != null && active.time > bestPreyTime && active.isChased) {
                bestPreyRow  = active.relRow;
                bestPreyCol  = active.relCol;
                bestPreyTime = active.time;
                inChaseMode  = true;
            }
        }

        // 4) If we’re manager but the chase ended, we revert
        PreySighting chase = getActiveChase();
        if (iAmManager && (chase == null || !chase.isChased)) {
            iAmManager  = false;
            inChaseMode = false;
        }

        // 5) If we have an active chase in local memory, check if it’s still valid
        if (bestPreyTime >= 0 && (globalTime - bestPreyTime) < CHASE_DURATION) {
            // still chase it
            inChaseMode = true;
        } else {
            // no valid memory => drop out of chase mode
            inChaseMode = false;
        }

        // 6) Move
        if (inChaseMode) {
            return moveToward(bestPreyRow, bestPreyCol);
        } else {
            return randomMove();
        }
    }

    @Override
    public int moveLim(List<int[]> wolvesSight, List<int[]> preysSight) {
        // Exactly the same logic as moveAll, 
        // except you return cardinal directions in {0,1,2,3,4}.
        // For brevity, we’ll just call moveAll and convert the result:
        int[] diag = moveAll(wolvesSight, preysSight);
        return moveLimToward(diag[0], diag[1]);
    }

    // ----------------------------------------------------------------
    //   HELPER FUNCTIONS
    // ----------------------------------------------------------------

    private void expireOldChases() {
        synchronized (blackboard) {
            for (PreySighting ps : blackboard) {
                if (globalTime > ps.chaseDeadline) {
                    // The chase has expired
                    ps.isChased = false;
                }
            }
            // You could also remove them from the list if you'd prefer
            // blackboard.removeIf(ps -> !ps.isChased);
        }
    }

    private PreySighting getActiveChase() {
        // Return the first chase that isChased = true, or null if none
        synchronized (blackboard) {
            for (PreySighting ps : blackboard) {
                if (ps.isChased) {
                    return ps;
                }
            }
        }
        return null;
    }

    // E.g. if the last known prey offset was (x,y), 
    // and the new sighting is (x+1,y) or (x,y+1), 
    // we assume it’s the “same prey.”
    private boolean distanceIsReasonable(PreySighting active, int[] newPrey) {
        int oldR = active.relRow;
        int oldC = active.relCol;
        int newR = newPrey[0];
        int newC = newPrey[1];
        // If the prey can only move 1 tile each turn, 
        // the difference should be <= 1 in row or col
        return (Math.abs(newR - oldR) <= 2 && Math.abs(newC - oldC) <= 1);
    }

    private boolean wolvesInCommunicationRange(List<int[]> wolvesSight, int range) {
        for (int[] offset : wolvesSight) {
            int dist = Math.abs(offset[0]) + Math.abs(offset[1]);
            if (dist <= range) {
                return true;
            }
        }
        return false;
    }

    // 0=NoMove,1=North,2=East,3=South,4=West
    private int moveLimToward(int rowDelta, int colDelta) {
        if (rowDelta < 0) return 1;   // north
        if (rowDelta > 0) return 3;   // south
        if (colDelta < 0) return 4;   // west
        if (colDelta > 0) return 2;   // east
        return 0;
    }

    private int[] moveToward(int relRow, int relCol) {
        int dRow = Integer.compare(relRow, 0); // -1 if relRow<0, +1 if relRow>0, else 0
        int dCol = Integer.compare(relCol, 0);
        return new int[]{ dRow, dCol };
    }

    private int[] randomMove() {
        int r = (int)(Math.random() * 3) - 1;
        int c = (int)(Math.random() * 3) - 1;
        return new int[]{ r, c };
    }

    private int manhattanDistance(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}
