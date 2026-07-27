import java.io.FileWriter;
import java.io.PrintWriter;

public class BenchmarkMain {
    public static void main(String[] args) throws Exception {
        GUI gui = new GUI();
        Thread.sleep(500);

        runSweep(gui, ParticleEngine.ExecutionMode.SEQUENTIAL, "SEQUENTIAL_results2.csv");
        //runSweep(gui, ParticleEngine.ExecutionMode.PARALLEL, "PARALLEL_results.csv");
    }

    private static void runSweep(GUI gui, ParticleEngine.ExecutionMode mode, String filename) throws Exception {
        PrintWriter writer = new PrintWriter(new FileWriter(filename));
        writer.println("particleCount,runIndex,avgFps");

        int particleCount = 2800;

        while (true) {
            double sum = 0;

            for (int run = 0; run < 100; run++) {
                double avgFps = gui.runOnceBlocking(particleCount, mode);
                writer.println(particleCount + "," + run + "," + avgFps);
                sum += avgFps;
            }

            double overallAvg = sum / 100;
            System.out.println(mode + " - " + particleCount + " particles: avg FPS = " + overallAvg);

            //if (overallAvg < 60) break;
            if (particleCount == 3200) break;
            particleCount += 100;
        }

        writer.close();
    }
}