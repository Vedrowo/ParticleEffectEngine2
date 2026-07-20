import java.awt.*;
import java.io.Serializable;

public class Particle implements Serializable {
    public double x, y, vx, vy, size, lifetime, currentLifetime, age,
            startingX, startingY, startingSize, startingVX, startingVY;
    public double tempVy = 0;
    public int maxX, maxY;
    private final double xOffset, yOffset;
    private static final double wallDamping = 0.2;
    private static final double floorDamping = 0.15;
    private static final double buoyancy = 0.05;
    public boolean mergedThisFrame = false;
    private static final Color[] colors = createColors();

    private static Color[] createColors(){
        Color[] colors = new Color[50];

        for (int i = 0; i < colors.length; i++) {
            double age = i / (double)(colors.length - 1);

            colors[i] = new Color(
                    1.0f,
                    (float)Math.max(0, 1 - 2 * age),
                    (float)Math.max(0, 1 - 10 * age),
                    (float)(1 - age * age)
            );
        }

        return colors;
    }

    public Particle(double x, double y, int maxX, int maxY) {
        xOffset = (Math.random() - 0.5) * 70;
        yOffset = (Math.random() - 0.5) * 20;
        this.maxX = maxX;
        this.maxY = maxY;
        this.x = x + xOffset;
        this.y = y + yOffset;

        this.currentLifetime = 0;
        this.startingX = x;
        this.startingY = y;

        double angle = Math.random() * 2 * Math.PI;
        double speed = 2 + Math.random() * 8;
        double time = System.currentTimeMillis() / 1000.0;
        double phase = Math.random() * 2 * Math.PI;
        double amplitude = 1.5;
        double frequency = 10.0;
        vx = amplitude * Math.sin(2 * Math.PI * frequency * time + phase);
        vy = 0 - Math.abs(Math.sin(angle) * speed);
        if(vy > -1){
            vy = vy - 1.5;
        }
        this.startingVX = vx;
        this.startingVY = vy;

        // Random stray particles that move and burn out quickly
        if (Math.random() < 1.0 / 500) {
            this.vx = vx*8;
            this.vy = vy*4;
            this.size = 15;
            this.lifetime = 60;
        }else {
            this.size = 30;
            this.lifetime = 187.5;
        }

        this.startingSize = size;
    }

    public void movement() {
        if (tempVy != 0) {
            vy = tempVy;
        }

        age = Math.max(0, Math.min(1, currentLifetime / lifetime));
        vx *= (1 - 0.01 * age);
        vy *= (1 - 0.01 * age);

        vy -= buoyancy;

        double radius = size / 2;

        x += vx;
        y += vy;

        if (x - radius < 0) {
            x = radius;
            vx = -vx * wallDamping;
        } else if (x + radius > maxX) {
            x = maxX - radius;
            vx = -vx * wallDamping;
        }

        if (y - radius < 0) {
            y = radius;
            vy = -vy * wallDamping;
        } else if (y + radius > maxY) {
            y = maxY - radius;
            vy = -vy * floorDamping;
        }

        currentLifetime++;
        size = (age < 0.3) ? size + 0.2 : size - 0.6;
    }

    public boolean isAlive() {
        return currentLifetime < lifetime;
    }

    public void reset() {
        currentLifetime = 0;
        x = startingX + xOffset;
        y = startingY + yOffset;
        vx = startingVX;
        vy = startingVY;
        size = startingSize;
        tempVy = 0;
        mergedThisFrame = false;
        lifetime = 187.5;
        age = 0;
    }

    public void draw(Graphics2D gc, double panelWidth, double panelHeight) {

        if (x + size < 0 || x - size > panelWidth || y + size < 0 || y - size > panelHeight) return;

        gc.setColor(getColor());


        gc.fillOval(
                (int)(x - size / 2),
                (int)(y - size / 2),
                (int)size,
                (int)size
        );
    }

    public Color getColor() {
        int index = (int)(age * (colors.length - 1));

        if(index < 0) index = 0;
        if(index >= colors.length) index = colors.length - 1;

        return colors[index];
    }
}
