import mpi.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class BenchmarkMainDistributed {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        ParticleEngine.mpiRank = rank;
        ParticleEngine.mpiSize = size;

        MPI.COMM_WORLD.Barrier();

        if (rank == 0) {
            GUI gui = new GUI();
            Thread.sleep(500);

            runSweep(gui, ParticleEngine.ExecutionMode.DISTRIBUTED, "DISTRIBUTED_results2.csv");

            int workerCount = size - 1;
            for (int w = 1; w <= workerCount; w++) {
                MPI.COMM_WORLD.Send(new Particle[0], 0, 0, MPI.OBJECT, w, ParticleEngine.STOP_TAG);
            }
            MPI.Finalize();
            System.exit(0);
        } else {
            ParticleEngine.runWorker();
            MPI.Finalize();
        }
    }

    private static void runSweep(GUI gui, ParticleEngine.ExecutionMode mode, String filename) throws Exception {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));
        writer.println("particleCount,runIndex,avgFps");

        int particleCount = 100;

        while (true) {
            double sum = 0;

            for (int run = 0; run < 100; run++) {
                double avgFps = gui.runOnceBlocking(particleCount, mode);
                writer.println(particleCount + "," + run + "," + avgFps);
                sum += avgFps;
            }

            double overallAvg = sum / 100;
            System.out.println(mode + " - " + particleCount + " particles: avg FPS = " + overallAvg);

            if (particleCount == 3200) break;

            particleCount += 100;
        }

        writer.close();
    }
}