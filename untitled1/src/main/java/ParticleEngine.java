import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mpi.*;

public class ParticleEngine {
    private final ArrayList<Particle> particles = new ArrayList<>();
    int numParticles, count = 0;
    private int addCount;
    double x, y;
    private boolean continuous = false;
    private double emissionRate = 0;
    private double emissionAccumulator = 0;
    private double emitterX, emitterY;
    private int maxX, maxY;
    private final int workers;
    private static final double cellSize = 50;
    private final ArrayList<ArrayList<Particle>> neighborResults = new ArrayList<>();
    private final ArrayList<Particle> cluster = new ArrayList<>();
    record CellKey(int x, int y) {}
    private final Map<CellKey, ArrayList<Particle>> map = new HashMap<>();
    private ExecutorService executor;

    enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL,
        DISTRIBUTED
    }
    private ExecutionMode model = ExecutionMode.SEQUENTIAL;

    private BufferedImage bufferA, bufferB;
    private volatile BufferedImage displayBuffer;
    private boolean useA = true;
    private int bufferWidth = -1, bufferHeight = -1;

    public static int mpiRank = 0;
    public static int mpiSize = 1;
    static final int WORK_TAG = 1;
    static final int STOP_TAG = 2;



    public ParticleEngine(double x, double y, int numParticles) {
        this.numParticles = numParticles;
        this.x = x;
        this.y = y;

        int cores = Runtime.getRuntime().availableProcessors();
        workers = Math.max(1, cores - 1);
        executor = Executors.newFixedThreadPool(workers);
    }

    private void ensureBuffers(int width, int height) {
        if (bufferWidth != width || bufferHeight != height) {
            bufferA = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            bufferB = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            bufferWidth = width;
            bufferHeight = height;
        }
    }

    public void renderFrame(int width, int height) {
        ensureBuffers(width, height);

        BufferedImage target = useA ? bufferA : bufferB;
        useA = !useA;

        Graphics2D g2d = target.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Particle particle : particles) {
            if (particle.isAlive()) {
                particle.draw(g2d, width, height);
            }
        }

        g2d.dispose();
        displayBuffer = target;
    }

    public void paint(Graphics g) {
        BufferedImage snapshot = displayBuffer;
        if (snapshot != null) {
            g.drawImage(snapshot, 0, 0, null);
        }
    }

    public boolean updateParticles(double deltaTime){
        for (Particle p : particles) {
            p.mergedThisFrame = false;
        }

        if (continuous) {
            emitOrRecycle(deltaTime);
        }

        updateMovement();

        map.clear();

        for (Particle p : particles) {
            if (!p.isAlive()) continue;
            CellKey cell = new CellKey((int)(p.x / cellSize), (int)(p.y / cellSize));
            map.computeIfAbsent(cell, _ -> new ArrayList<>()).add(p);
        }

        detectCollisions(map);

        mergeCollisions();

        for (Particle p : particles) {
            if (p.isAlive()) return true;
        }
        return false;

    }

    private void detectCollisions(Map<CellKey, ArrayList<Particle>> map){
        if (model == ExecutionMode.SEQUENTIAL){
            detectCollisionsSequential(map);
        } else if (model == ExecutionMode.PARALLEL){
            detectCollisionsParallel(map);
        } else {
            detectCollisionsDistributed(map);
        }
    }

    private void detectCollisionsSequential(Map<CellKey, ArrayList<Particle>> map) {
        int n = particles.size();

        neighborResults.clear();
        for (int i = 0; i < n; i++) {
            neighborResults.add(new ArrayList<>());
        }

        for (int j = 0; j < n; j++) {
            checkNeighbours(map, j);
        }
    }

    private void detectCollisionsParallel(Map<CellKey, ArrayList<Particle>> map) {
        int n = particles.size();

        neighborResults.clear();
        for (int i = 0; i < n; i++) {
            neighborResults.add(new ArrayList<>());
        }

        int chunkSize = (n + workers - 1) / workers;
        if (chunkSize == 0) return;

        ArrayList<Runnable> tasks = new ArrayList<>();

        for (int i = 0; i < n; i += chunkSize) {
            int start = i;
            int end = Math.min(i + chunkSize, n);

            tasks.add(() -> {
                for (int j = start; j < end; j++) {
                    checkNeighbours(map, j);
                }
            });
        }

        try {
            executor.invokeAll(
                    tasks.stream().map(Executors::callable).toList()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void detectCollisionsDistributed(Map<CellKey, ArrayList<Particle>> map){
        detectCollisionsSequential(map);
    }

    private void checkNeighbours(Map<CellKey, ArrayList<Particle>> map, int j) {
        Particle p = particles.get(j);
        if (!p.isAlive() || p.age < 0.7) return;

        ArrayList<Particle> found = neighborResults.get(j);
        CellKey cell = new CellKey((int)(p.x / cellSize), (int)(p.y / cellSize));

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                CellKey neighborCell = new CellKey(cell.x() + dx, cell.y() + dy);
                ArrayList<Particle> others = map.get(neighborCell);
                if (others == null) continue;

                for (Particle other : others) {
                    if (other != p && isColliding(p, other)) {
                        found.add(other);
                    }
                }
            }
        }
    }

    private void mergeCollisions(){
        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            if (!p.isAlive() || p.age < 0.7 || p.mergedThisFrame) continue;

            cluster.clear();
            cluster.add(p);
            p.mergedThisFrame = true;

            for (Particle other : neighborResults.get(i)) {
                if (!other.mergedThisFrame) {
                    cluster.add(other);
                    other.mergedThisFrame = true;
                }
            }

            if (cluster.size() > 1) {
                mergeCluster(cluster);
            }
        }
    }

    private void emitOrRecycle(double deltaTime){
        int resetCount = 0;
        for (Particle p : particles) {
            if(!p.isAlive() && count < addCount){
                p.reset();
                count++;
                resetCount++;
            }
        }
        if(count < addCount){
            emissionAccumulator += emissionRate * deltaTime - resetCount;
            if(emissionAccumulator > 0){
                int emitNow = (int) emissionAccumulator;
                emissionAccumulator -= emitNow;
                for (int i = 0; i < emitNow; i++) {
                    particles.add(new Particle(emitterX, emitterY, maxX, maxY));
                    count++;
                }
            }
        }
    }

    private void updateMovement(){
        if (model == ExecutionMode.SEQUENTIAL){
            updateMovementSequential();
        } else if (model == ExecutionMode.PARALLEL){
            updateMovementParallel();
        } else {
            updateMovementDistributed();
        }
    }

    private void updateMovementSequential(){
        for(Particle p : particles){
            if (p.isAlive()){
                p.movement();
            }
        }
    }

    private void updateMovementParallel(){
        int chunkSize = (particles.size() + workers - 1) / workers;

        ArrayList<Runnable> tasks = new ArrayList<>();

        for (int i = 0; i < particles.size(); i += chunkSize) {
            int start = i;
            int end = Math.min(i + chunkSize, particles.size());

            //System.out.println("chunk: " + start + " to " + end);

            tasks.add(() -> {
                for (int j = start; j < end; j++) {
                    Particle p = particles.get(j);
                    if (p.isAlive()) {
                        p.movement();
                    }
                }
            });
        }

        try {
            executor.invokeAll(
                    tasks.stream().map(Executors::callable).toList()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateMovementDistributed(){
        int workerCount = mpiSize - 1;
        if (workerCount <= 0) {
            updateMovementSequential();
            System.out.println("Fallback to sequential");
            return;
        }

        int n = particles.size();
        int chunkSize = (n + workerCount - 1) / workerCount;

        try {
            //System.out.println("Distributing movement to " + workerCount + " MPI workers.");

            for (int w = 0; w < workerCount; w++) {
                int start = w * chunkSize;
                int end = Math.min(start + chunkSize, n);
                if (start >= end) continue;

                Particle[] chunk = new Particle[end - start];
                for (int i = start; i < end; i++) {
                    chunk[i - start] = particles.get(i);
                }

                MPI.COMM_WORLD.Send(chunk, 0, chunk.length, MPI.OBJECT, w + 1, WORK_TAG);
            }

            for (int w = 0; w < workerCount; w++) {
                int start = w * chunkSize;
                int end = Math.min(start + chunkSize, n);
                if (start >= end) continue;

                Particle[] result = new Particle[end - start];
                MPI.COMM_WORLD.Recv(result, 0, result.length, MPI.OBJECT, w + 1, WORK_TAG);

                for (int i = start; i < end; i++) {
                    particles.set(i, result[i - start]);
                }
            }
        } catch (MPIException e) {
            e.printStackTrace();
        }
    }

    public static void runWorker() {
        while (true) {
            try {
                Status probeStatus = MPI.COMM_WORLD.Probe(0, MPI.ANY_TAG);
                int incomingCount = probeStatus.Get_count(MPI.OBJECT);

                if (probeStatus.tag == STOP_TAG) {
                    Particle[] dummy = new Particle[incomingCount];
                    MPI.COMM_WORLD.Recv(dummy, 0, incomingCount, MPI.OBJECT, 0, STOP_TAG);
                    break;
                }

                Particle[] buffer = new Particle[incomingCount];
                Status status = MPI.COMM_WORLD.Recv(buffer, 0, incomingCount, MPI.OBJECT, 0, probeStatus.tag);

                int count = status.Get_count(MPI.OBJECT);

                for (int i = 0; i < count; i++) {
                    if (buffer[i].isAlive()) {
                        buffer[i].movement();
                    }
                }

                MPI.COMM_WORLD.Send(buffer, 0, count, MPI.OBJECT, 0, WORK_TAG);
            } catch (MPIException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void startBurst(double x, double y, int maxX, int maxY, int addCount, ExecutionMode model){
        this.continuous = false;
        this.model = model;
        this.emitterX = x;
        this.emitterY = y;
        this.maxX = maxX;
        this.maxY = maxY;
        this.addCount = addCount;
        for (int i = 0; i < addCount; i++){
            particles.add(new Particle(x, y, maxX, maxY));
        }
    }

    public void startOverTime(double x, double y, int maxX, int maxY, int addCount, double ratePerSecond, ExecutionMode model){
        this.continuous = true;
        this.model = model;
        this.emissionRate = ratePerSecond;
        this.emitterX = x;
        this.emitterY = y;
        this.maxX = maxX;
        this.maxY = maxY;
        this.addCount = addCount;
    }

    private void mergeCluster(ArrayList<Particle> cluster) {
        if (cluster.isEmpty()) return;

        double avgX = 0;
        double avgVy = 0;
        int size = cluster.size();

        for (Particle p : cluster) {
            avgX += p.x;
            avgVy += p.vy;
        }

        avgX /= size;
        avgVy /= size;

        for (Particle p : cluster) {
            p.x += (avgX - p.x) / 10;
            p.tempVy = avgVy;
        }
    }

    public boolean isColliding(Particle p1, Particle p2) {
        double dx = Math.abs(p1.x - p2.x);
        double dy = Math.abs(p1.y - p2.y);
        double distanceSquared = dx * dx + dy * dy;
        double radius = cellSize / 4;
        return distanceSquared < radius * radius;
    }

    /*
    public static void mergeParticles(Particle p1, Particle p2) {
        double avgX = (p1.x + p2.x) / 2;
        p1.x += (avgX - p1.x) / 10;
        p2.x += (avgX - p2.x) / 10;

        double avgVy = (p1.vy + p2.vy) / 2;
        p1.tempVy = avgVy;
        p2.tempVy = avgVy;
    }
    */

    public void resetEngine(double x, double y, int numParticles) {
        this.numParticles = numParticles;
        this.x = x;
        this.y = y;
        count = 0;
        addCount = 0;
        particles.clear();
        continuous = false;
        emissionRate = 0;
        emissionAccumulator = 0;
        emitterX = 0;
        emitterY = 0;
        maxX = 0;
        maxY = 0;

        if (executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(workers);
        }
    }

    public void shutDown(){
        executor.shutdown();
    }
}
