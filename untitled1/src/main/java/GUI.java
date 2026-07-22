import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class GUI {
    private JPanel drawingPanel;
    private JTextField particleCountField, rateField, xField, yField,
            windowWidthField, windowHeightField;
    private JLabel runtimeLabel, fpsLabel;
    private JComboBox<String> emitterTypeBox, modelTypeBox;
    private JButton startButton;
    private ParticleEngine engine;
    private long startTime, lastFPSUpdateTime;
    private int frameCount, totalFrames;
    private JFrame frame;
    private volatile boolean running = false;


    public GUI() {
        setupGUI();
        startButton.addActionListener(_ -> {
            reset();
            startTime = System.nanoTime();
            int numParticles = Integer.parseInt(particleCountField.getText());
            int width = Integer.parseInt(windowWidthField.getText());
            int height = Integer.parseInt(windowHeightField.getText());
            int x = Integer.parseInt(xField.getText());
            int y = Integer.parseInt(yField.getText());
            int ratePerSecond = Integer.parseInt(rateField.getText());
            String mode = Objects.requireNonNull(emitterTypeBox.getSelectedItem()).toString();
            String model = Objects.requireNonNull(modelTypeBox.getSelectedItem()).toString();
            frame.setSize(width, height);

            if(engine == null){
                this.engine = new ParticleEngine(x, y, numParticles);
            }else{
                engine.resetEngine(x, y, numParticles);
            }

            if(Objects.equals(mode, "Over-time")){
                if (Objects.equals(model, "Sequential")){
                    engine.startOverTime(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ratePerSecond, ParticleEngine.ExecutionMode.SEQUENTIAL);
                } else if (Objects.equals(model, "Parallel")){
                    engine.startOverTime(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ratePerSecond, ParticleEngine.ExecutionMode.PARALLEL);
                } else {
                    engine.startOverTime(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ratePerSecond, ParticleEngine.ExecutionMode.DISTRIBUTED);
                }
            } else if (Objects.equals(mode, "Burst")){
                if (Objects.equals(model, "Sequential")){
                    engine.startBurst(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ParticleEngine.ExecutionMode.SEQUENTIAL);
                } else if (Objects.equals(model, "Parallel")){
                    engine.startBurst(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ParticleEngine.ExecutionMode.PARALLEL);
                } else {
                    engine.startBurst(x, y, drawingPanel.getWidth(), drawingPanel.getHeight(), numParticles, ParticleEngine.ExecutionMode.DISTRIBUTED);
                }
            }

            running = true;
            Thread gameLoop = new Thread(() -> {
                while (running) {
                    long frameStart = System.nanoTime();

                    boolean isAlive = engine.updateParticles(0.016);
                    engine.renderFrame(drawingPanel.getWidth(), drawingPanel.getHeight());
                    drawingPanel.repaint();

                    long now = System.nanoTime();
                    double elapsedSeconds = (now - startTime) / 1e9;

                    frameCount++;
                    double fpsElapsed = (now - lastFPSUpdateTime) / 1e9;
                    int fpsToShow = -1;
                    if (fpsElapsed >= 1.0) {
                        fpsToShow = (int) (frameCount / fpsElapsed);
                        frameCount = 0;
                        lastFPSUpdateTime = now;
                    }

                    final int fpsForLabel = fpsToShow;
                    SwingUtilities.invokeLater(() -> {
                        runtimeLabel.setText(String.format("Run-time: %.2f s", elapsedSeconds));
                        if (fpsForLabel >= 0) {
                            fpsLabel.setText("FPS: " + fpsForLabel);
                        }
                    });

                    totalFrames++;

                    if (!isAlive) {
                        running = false;
                        engine.shutDown();
                        double averageFPS = totalFrames / elapsedSeconds;
                        SwingUtilities.invokeLater(() ->
                                fpsLabel.setText(String.format("Average FPS: %.2f", averageFPS)));
                        System.out.println("Shut down executor");
                        break;
                    }

                    long frameTime = (System.nanoTime() - frameStart) / 1_000_000;
                    long sleepTime = 16 - frameTime;
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
            gameLoop.setDaemon(true);
            gameLoop.start();
        });
    }

    private void reset() {
        totalFrames = 0;
    }

    private void setupGUI() {
        frame = new JFrame("Particle Engine");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBackground(Color.DARK_GRAY);

        particleCountField = new JTextField("1000", 5);
        xField = new JTextField("400", 5);
        yField = new JTextField("450", 5);
        windowWidthField = new JTextField("800", 5);
        windowHeightField = new JTextField("600", 5);
        rateField = new JTextField("100", 5);
        emitterTypeBox = new JComboBox<>(new String[] {"Over-time", "Burst"});
        modelTypeBox = new JComboBox<>(new String[] {"Sequential", "Parallel", "Distributed"});
        startButton = new JButton("Start!");

        topPanel.add(labeled("Particles", particleCountField));
        topPanel.add(labeled("X", xField));
        topPanel.add(labeled("Y", yField));
        topPanel.add(labeled("Width", windowWidthField));
        topPanel.add(labeled("Height", windowHeightField));
        topPanel.add(labeled("Model", modelTypeBox));
        topPanel.add(labeled("Mode", emitterTypeBox));
        topPanel.add(labeled("Rate", rateField));

        frame.add(topPanel, BorderLayout.NORTH);

        drawingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // System.out.println("Panel size: " + getWidth() + " " + getHeight());
                if (engine != null) {
                    engine.paint(g);
                }
            }
        };
        drawingPanel.setBackground(Color.BLACK);
        frame.add(drawingPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        bottomPanel.setBackground(Color.DARK_GRAY);
        fpsLabel = new JLabel("FPS:");
        runtimeLabel = new JLabel("Run-time:");
        fpsLabel.setForeground(Color.WHITE);
        runtimeLabel.setForeground(Color.WHITE);
        bottomPanel.add(fpsLabel);
        bottomPanel.add(runtimeLabel);
        bottomPanel.add(startButton, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private JPanel labeled(String text, JComponent field){
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.DARK_GRAY);

        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);

        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        field.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(label);
        p.add(field);

        return p;
    }
}
