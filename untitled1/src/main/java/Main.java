import mpi.*;

public class Main {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        ParticleEngine.mpiRank = rank;
        ParticleEngine.mpiSize = size;

        if (rank == 0) {
            GUI gui = new GUI();

            int workerCount = size - 1;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    for (int w = 1; w <= workerCount; w++) {
                        MPI.COMM_WORLD.Send(new Particle[0], 0, 0, MPI.OBJECT, w, ParticleEngine.STOP_TAG);
                    }
                    MPI.Finalize();
                } catch (Exception ignored) {
                }
            }));
        } else {
            ParticleEngine.runWorker();
            MPI.Finalize();
        }
    }
}