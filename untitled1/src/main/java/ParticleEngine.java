import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParticleEngine {
    private final ArrayList<Particle> particles = new ArrayList<>();
    int numParticles, count = 0;
    private int addCount;
    double x, y;
    private boolean continuous = false;
    private double emissionRate = 0;
    private double emissionAccumulator = 0;
    private double emitterX, emitterY;
    private int maxX;
    private int maxY;
    private final int workers;
    private static final double cellSize = 50;
    private final ArrayList<Particle> neighbors = new ArrayList<>();
    private final ArrayList<Particle> cluster = new ArrayList<>();
    //private final Map<CellKey, ConcurrentLinkedQueue<Particle>> grid = new ConcurrentHashMap<>();
    private final Map<CellKey, ArrayList<Particle>> grid = new HashMap<>();
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

    record CellKey(int x, int y) {}

    public void paint(Graphics g, int width, int height) {
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

        /*
        grid.clear();

        for (Particle p : particles) {
            if (!p.isAlive()) continue;

            CellKey cell = new CellKey((int)(p.x / cellSize), (int)(p.y / cellSize));

            //grid.computeIfAbsent(cell, _ -> new ConcurrentLinkedQueue<>()).add(p);
            grid.computeIfAbsent(cell, _ -> new ArrayList<>()).add(p);
        }

        for (Particle p : particles) {
            if (!p.isAlive() || p.age < 0.7) continue;
            handleCollisions(p, grid);
        } */

        for (Particle p : particles) {
            if (p.isAlive()) return true;
        }
        return false;

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

            System.out.println("chunk: " + start + " to " + end);

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

    private void handleCollisions(Particle p, Map<CellKey, ArrayList<Particle>> map) {
        if (p.mergedThisFrame) return;

        neighbors.clear();
        cluster.clear();

        cluster.add(p);
        p.mergedThisFrame = true;

        CellKey cell = new CellKey((int)(p.x / cellSize), (int)(p.y / cellSize));

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                CellKey neighborCell = new CellKey(cell.x() + dx, cell.y() + dy);
                var others = map.get(neighborCell);
                if (others != null) {
                    neighbors.addAll(others);
                }
            }
        }

        for (Particle other : neighbors) {
            if (other != p && !other.mergedThisFrame && isColliding(p, other)) {
                cluster.add(other);
                other.mergedThisFrame = true;
            }
        }

        if (cluster.size() > 1){
            mergeCluster(cluster);
        }
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
