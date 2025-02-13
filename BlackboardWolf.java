import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BlackboardWolf implements Wolf {

    // ---------------------------------------------------------------
    // 1) A simple structure to store sightings (“calls for help”).
    // ---------------------------------------------------------------
    static class PreySighting {
        int relRow;
        int relCol;
        long time;
        public PreySighting(int r, int c, long t) {
            this.relRow = r;
            this.relCol = c;
            this.time   = t;
        }
    }

    // ---------------------------------------------------------------
    // 2) A static shared blackboard (no IDs).
    // ---------------------------------------------------------------
    private static List<PreySighting> blackboard = Collections.synchronizedList(new ArrayList<>());

    // Each wolf’s local state
    private int  bestPreyRow  = 0;
    private int  bestPreyCol  = 0;
    private long bestPreyTime = -1; 
    private boolean iAmManager   = false;
    private boolean inChaseMode  = false;

    // Global time for freshness logic; ideally comes from environment
    private static long globalTime = 0;

    // --------------------------------------
    //  moveAll: diagonal movement allowed
    // --------------------------------------
    @Override
    public int[] moveAll(List<int[]> wolvesSight, List<int[]> preysSight) {
        globalTime++;

        // 1) Sort preysSight by distance from this wolf
        //    Each entry is [relRow, relCol], so we define a comparator by distance.
        preysSight.sort(Comparator.comparingInt(o -> manhattanDistance(o[0], o[1], 0, 0)));

        // 2) If we see at least one prey, pick the *closest* one
        if (!preysSight.isEmpty()) {
            int[] closestPrey = preysSight.get(0);
            bestPreyRow  = closestPrey[0];
            bestPreyCol  = closestPrey[1];
            bestPreyTime = globalTime;

            // The wolf that sees the prey becomes manager
            iAmManager   = true;
            inChaseMode  = true;

            // "Broadcast" by adding to the blackboard
            blackboard.add(new PreySighting(bestPreyRow, bestPreyCol, globalTime));
        } else {
            // If we *don't* see a prey right now, we might still be chasing the last known location
            // or might pick up info from the blackboard if we can "communicate".
            iAmManager = false;  // no direct sight => not manager this turn
        }

        // 3) Possibly pick up a new sighting if we see a wolf that might be “in chase.”
        //    Because we don't have a direct “wolf says it is chasing” property, 
        //    we simulate communication range—if any wolf is within range, we can read blackboard.
        if (wolvesInCommunicationRange(wolvesSight, 1)) {
            // read blackboard for more recent sightings
            PreySighting fresh = getMostRecentSighting();
            if (fresh != null && fresh.time > bestPreyTime) {
                bestPreyRow  = fresh.relRow;
                bestPreyCol  = fresh.relCol;
                bestPreyTime = fresh.time;
                // If we see a fresh sighting from the blackboard, 
                // assume we enter chase mode too.
                inChaseMode = true;
            }
        }

        // 4) If we have no direct sight but we recently saw a prey, keep chasing
        if (bestPreyTime > 0 && (globalTime - bestPreyTime) < 50) {
            // chase
            inChaseMode = true;
        } else {
            inChaseMode = false;
        }

        // 5) Decide movement
        if (inChaseMode) {
            // Move toward best known prey location
            return moveToward(bestPreyRow, bestPreyCol);
        } else {
            // Just wander or perhaps move to cluster with other wolves
            return randomMove();
        }
    }

    // -------------------------------------------
    //  moveLim: no diagonal movement allowed
    // -------------------------------------------
    @Override
    public int moveLim(List<int[]> wolvesSight, List<int[]> preysSight) {
        // Same logic: sort, pick the closest prey, 
        // set manager/chase booleans, etc., then return a single direction.
        globalTime++;

        // Sort by distance
        preysSight.sort(Comparator.comparingInt(o -> manhattanDistance(o[0], o[1], 0, 0)));

        if (!preysSight.isEmpty()) {
            int[] closestPrey = preysSight.get(0);
            bestPreyRow  = closestPrey[0];
            bestPreyCol  = closestPrey[1];
            bestPreyTime = globalTime;
            iAmManager   = true;
            inChaseMode  = true;
            blackboard.add(new PreySighting(bestPreyRow, bestPreyCol, globalTime));
        } else {
            iAmManager = false;
        }

        if (wolvesInCommunicationRange(wolvesSight, 1)) {
            PreySighting fresh = getMostRecentSighting();
            if (fresh != null && fresh.time > bestPreyTime) {
                bestPreyRow  = fresh.relRow;
                bestPreyCol  = fresh.relCol;
                bestPreyTime = fresh.time;
                inChaseMode  = true;
            }
        }

        if (bestPreyTime > 0 && (globalTime - bestPreyTime) < 50) {
            inChaseMode = true;
        } else {
            inChaseMode = false;
        }

        if (inChaseMode) {
            return moveLimToward(bestPreyRow, bestPreyCol);
        } else {
            return randomMoveLim();
        }
    }

    // -------------------------------------------
    //  Utility: sort the lists & measure distance
    // -------------------------------------------
    private int manhattanDistance(int row1, int col1, int row2, int col2) {
        return Math.abs(row1 - row2) + Math.abs(col1 - col2);
    }

    // If any wolf is within “range” (Manhattan distance), 
    // assume we can share blackboard info.
    private boolean wolvesInCommunicationRange(List<int[]> wolvesSight, int range) {
        for (int[] offset : wolvesSight) {
            int dist = Math.abs(offset[0]) + Math.abs(offset[1]);
            if (dist <= range) {
                return true;
            }
        }
        return false;
    }

    // Pick the most recent sighting from the shared blackboard
    private PreySighting getMostRecentSighting() {
        PreySighting latest = null;
        synchronized (blackboard) {
            for (PreySighting ps : blackboard) {
                if (latest == null || ps.time > latest.time) {
                    latest = ps;
                }
            }
        }
        return latest;
    }

    // -------------------------------------------
    //  Movement helpers
    // -------------------------------------------
    private int[] moveToward(int relRow, int relCol) {
        // Diagonal: row & col each in {-1, 0, +1}
        int dRow = Integer.compare(relRow, 0); // -1 if relRow<0, +1 if relRow>0, else 0
        int dCol = Integer.compare(relCol, 0);
        return new int[]{ dRow, dCol };
    }

    private int[] randomMove() {
        int r = (int)(Math.random() * 3) - 1;
        int c = (int)(Math.random() * 3) - 1;
        return new int[]{ r, c };
    }

    // moveLim => no diagonal
    private int moveLimToward(int relRow, int relCol) {
        // 0=NoMove,1=North,2=East,3=South,4=West
        // Example approach: pick whichever axis is bigger
        // If row offset is bigger, move up/down; else left/right
        int absRow = Math.abs(relRow);
        int absCol = Math.abs(relCol);
        if (absRow > absCol) {
            // move vertically
            return (relRow < 0) ? 1 : 3; // north or south
        } else if (absCol > 0) {
            // move horizontally
            return (relCol < 0) ? 4 : 2; // west or east
        } else {
            // Already aligned => no move
            return 0;
        }
    }

    private int randomMoveLim() {
        // 0=stay,1=North,2=East,3=South,4=West
        return (int)(Math.random() * 5);
    }
}
