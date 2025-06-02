import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

// 移动策略枚举
enum MoveStrategy {
    RANDOM,      // 随机移动
    SEEK_PLAYER, // 追踪玩家
    PATROL,      // 巡逻移动
    AMBUSH,      // 伏击位置
    RETREAT      // 撤退策略
}

// 方向枚举
enum Direction {
    UP, DOWN, LEFT, RIGHT
}

// 音效播放器类
class SoundPlayer {
    private Clip clip;
    private String soundPath;

    public SoundPlayer(String soundPath) {
        this.soundPath = soundPath;
    }

    public void play() {
        try {
            // 如果clip已存在，停止并关闭它
            if (clip != null && clip.isRunning()) {
                clip.stop();
                clip.close();
            }

            // 从资源加载音频
            InputStream audioStream = getClass().getResourceAsStream(soundPath);
            if (audioStream == null) {
                System.err.println("Sound file not found: " + soundPath);
                return;
            }

            // 将输入流转换为AudioInputStream
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioStream);

            // 获取音频格式
            AudioFormat format = audioInputStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);

            // 检查系统是否支持该音频格式
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Audio format not supported: " + soundPath);
                audioInputStream.close();
                return;
            }

            // 获取并打开clip
            clip = (Clip) AudioSystem.getLine(info);
            clip.open(audioInputStream);

            // 开始播放
            clip.start();

            // 关闭音频输入流
            audioInputStream.close();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error playing sound: " + e.getMessage());
        }
    }

    public void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }

    public void close() {
        if (clip != null) {
            clip.close();
        }
    }
}

// 坦克类
class Tank {
    protected int x, y;
    protected Direction direction;
    protected int speed = 5; // 基础速度
    protected static final int MAP_WIDTH = 800;
    protected static final int MAP_HEIGHT = 600;
    protected BufferedImage image; // 坦克图像
    protected BufferedImage[] tankImages; // 不同方向的坦克图像
    protected HashSet<Integer> pressedKeys; // 记录当前按下的键

    public Tank(int x, int y, Direction direction, BufferedImage[] tankImages) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.tankImages = tankImages;
        this.pressedKeys = new HashSet<>();
        if (tankImages != null) {
            this.image = tankImages[0]; // 默认向上
        }
    }

    public void move() {
        // 优先处理垂直方向（上/下）
        if (pressedKeys.contains(KeyEvent.VK_W)) {
            direction = Direction.UP;
            y -= speed;
            if (tankImages != null) image = tankImages[0];
        } else if (pressedKeys.contains(KeyEvent.VK_S)) {
            direction = Direction.DOWN;
            y += speed;
            if (tankImages != null) image = tankImages[1];
        }

        // 再处理水平方向（左/右）
        if (pressedKeys.contains(KeyEvent.VK_A)) {
            direction = Direction.LEFT;
            x -= speed;
            if (tankImages != null) image = tankImages[2];
        } else if (pressedKeys.contains(KeyEvent.VK_D)) {
            direction = Direction.RIGHT;
            x += speed;
            if (tankImages != null) image = tankImages[3];
        }

        // 边界检测
        x = Math.max(0, Math.min(x, MAP_WIDTH - getWidth()));
        y = Math.max(0, Math.min(y, MAP_HEIGHT - getHeight()));
    }

    public void addPressedKey(int keyCode) {
        pressedKeys.add(keyCode);
    }

    public void removePressedKey(int keyCode) {
        pressedKeys.remove(keyCode);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public int getWidth() {
        return tankImages != null ? image.getWidth() : 40; // 使用图像宽度或默认值
    }

    public int getHeight() {
        return tankImages != null ? image.getHeight() : 40; // 使用图像高度或默认值
    }

    public void draw(Graphics g) {
        if (tankImages != null) {
            g.drawImage(image, x, y, null);
        } else {
            // 如果没有图像，使用默认形状
            g.fillRect(x, y, getWidth(), getHeight());
        }
    }

    // 射击方法，调整子弹发射位置到坦克朝向的中间
    public Bullet fire(BufferedImage bulletImage) {
        int tankWidth = getWidth();
        int tankHeight = getHeight();

        // 计算坦克中心位置
        int centerX = x + tankWidth / 2;
        int centerY = y + tankHeight / 2;

        // 子弹尺寸
        int bulletSize = bulletImage != null ? bulletImage.getWidth() : 10;

        // 根据坦克方向精确调整子弹初始位置
        int bulletX = centerX - bulletSize / 2;
        int bulletY = centerY - bulletSize / 2;

        // 微调子弹位置，使其从坦克朝向的中间发射
        switch (direction) {
            case UP:
                bulletX = x + (tankWidth - bulletSize) / 2; // 水平居中
                bulletY = y - bulletSize / 2; // 上方中间
                break;
            case DOWN:
                bulletX = x + (tankWidth - bulletSize) / 2; // 水平居中
                bulletY = y + tankHeight - bulletSize / 2; // 下方中间
                break;
            case LEFT:
                bulletX = x - bulletSize / 2; // 左侧中间
                bulletY = y + (tankHeight - bulletSize) / 2; // 垂直居中
                break;
            case RIGHT:
                bulletX = x + tankWidth - bulletSize / 2; // 右侧中间
                bulletY = y + (tankHeight - bulletSize) / 2; // 垂直居中
                break;
        }

        return new Bullet(bulletX, bulletY, direction, bulletImage);
    }
}

// 子弹类
class Bullet {
    private int x, y;
    private Direction direction;
    private int speed = 10;
    private boolean active = true; // 子弹是否有效
    private BufferedImage image; // 子弹图像

    public Bullet(int x, int y, Direction direction, BufferedImage image) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.image = image;
    }

    public boolean checkCollision(Tank tank) {
        if (!active) return false; // 无效子弹不检测碰撞

        int width = image != null ? image.getWidth() : 10;
        int height = image != null ? image.getHeight() : 10;

        Rectangle bulletRect = new Rectangle(x, y, width, height);
        Rectangle tankRect = new Rectangle(tank.getX(), tank.getY(), tank.getWidth(), tank.getHeight());
        return bulletRect.intersects(tankRect);
    }

    public void move() {
        if (!active) return; // 无效子弹不移动

        switch (direction) {
            case UP:
                y -= speed;
                if (y < 0) active = false; // 子弹超出边界
                break;
            case DOWN:
                y += speed;
                if (y > Tank.MAP_HEIGHT) active = false;
                break;
            case LEFT:
                x -= speed;
                if (x < 0) active = false;
                break;
            case RIGHT:
                x += speed;
                if (x > Tank.MAP_WIDTH) active = false;
                break;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return image != null ? image.getWidth() : 10;
    }

    public int getHeight() {
        return image != null ? image.getHeight() : 10;
    }

    public void draw(Graphics g) {
        if (image != null) {
            g.drawImage(image, x, y, null);
        } else {
            // 如果没有图像，使用默认形状
            g.fillRect(x, y, getWidth(), getHeight());
        }
    }
}

// 敌人坦克类
class EnemyTank extends Tank {
    private MoveStrategy moveStrategy = MoveStrategy.RANDOM; // 默认随机移动
    private int strategyTimer = 0; // 策略计时器
    private static final int STRATEGY_CHANGE_INTERVAL = 300; // 策略变更间隔
    private int patrolIndex = 0; // 巡逻路径索引
    private int[][] patrolPoints = { // 预设巡逻点
            {200, 150}, {600, 150}, {600, 450}, {200, 450}
    };
    private int ambushTimer = 0; // 伏击计时器
    private boolean isAmbushing = false; // 是否在伏击状态
    private int retreatTimer = 0; // 撤退计时器
    private boolean isRetreating = false; // 是否在撤退状态
    private Point retreatPoint; // 撤退目标点
    private int fireTimer = 0;
    private static final int FIRE_INTERVAL = 60; // 敌人开火间隔
    private Tank playerTank; // 玩家坦克引用

    public EnemyTank(int x, int y, Direction direction, Tank playerTank, BufferedImage[] tankImages) {
        super(x, y, direction, tankImages);
        this.playerTank = playerTank;
        retreatPoint = new Point(x, y); // 初始撤退点为出生点
        this.speed = 3; // 敌人坦克速度降低，比玩家慢
    }

    @Override
    public void move() {
        // 随机切换策略
        strategyTimer++;
        if (strategyTimer >= STRATEGY_CHANGE_INTERVAL) {
            changeStrategyRandomly();
            strategyTimer = 0;
        }

        // 根据当前策略移动
        switch (moveStrategy) {
            case RANDOM:
                randomMove();
                break;
            case SEEK_PLAYER:
                seekPlayerMove();
                break;
            case PATROL:
                patrolMove();
                break;
            case AMBUSH:
                ambushMove();
                break;
            case RETREAT:
                retreatMove();
                break;
        }

        // 边界检测
        x = Math.max(0, Math.min(x, MAP_WIDTH - getWidth()));
        y = Math.max(0, Math.min(y, MAP_HEIGHT - getHeight()));
    }

    private void changeStrategyRandomly() {
        Random rand = new Random();
        int randomNum = rand.nextInt(5); // 0-4
        switch (randomNum) {
            case 0:
                moveStrategy = MoveStrategy.RANDOM;
                break;
            case 1:
                moveStrategy = MoveStrategy.SEEK_PLAYER;
                break;
            case 2:
                moveStrategy = MoveStrategy.PATROL;
                break;
            case 3:
                moveStrategy = MoveStrategy.AMBUSH;
                break;
            case 4:
                moveStrategy = MoveStrategy.RETREAT;
                retreatPoint = new Point(
                        rand.nextInt(MAP_WIDTH - 100) + 50,
                        rand.nextInt(MAP_HEIGHT - 100) + 50
                );
                break;
        }
    }

    private void randomMove() {
        Random rand = new Random();
        if (rand.nextInt(100) < 5) { // 5%概率改变方向
            int dir = rand.nextInt(4);
            this.setDirection(Direction.values()[dir]);
        }

        // 移动
        switch (direction) {
            case UP:
                y -= speed;
                if (tankImages != null) image = tankImages[0];
                break;
            case DOWN:
                y += speed;
                if (tankImages != null) image = tankImages[1];
                break;
            case LEFT:
                x -= speed;
                if (tankImages != null) image = tankImages[2];
                break;
            case RIGHT:
                x += speed;
                if (tankImages != null) image = tankImages[3];
                break;
        }
    }

    private void seekPlayerMove() {
        if (playerTank == null) return;

        int dx = playerTank.getX() - this.x;
        int dy = playerTank.getY() - this.y;

        // 距离判断
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 150) {
            // 接近玩家时，增加侧向移动概率，更难被击中
            Random rand = new Random();
            if (rand.nextBoolean()) {
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dy > 0) {
                        this.setDirection(Direction.DOWN);
                        y += speed;
                        if (tankImages != null) image = tankImages[1];
                    } else {
                        this.setDirection(Direction.UP);
                        y -= speed;
                        if (tankImages != null) image = tankImages[0];
                    }
                } else {
                    if (dx > 0) {
                        this.setDirection(Direction.RIGHT);
                        x += speed;
                        if (tankImages != null) image = tankImages[3];
                    } else {
                        this.setDirection(Direction.LEFT);
                        x -= speed;
                        if (tankImages != null) image = tankImages[2];
                    }
                }
            } else {
                // 正常追踪
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) {
                        this.setDirection(Direction.RIGHT);
                        x += speed;
                        if (tankImages != null) image = tankImages[3];
                    } else {
                        this.setDirection(Direction.LEFT);
                        x -= speed;
                        if (tankImages != null) image = tankImages[2];
                    }
                } else {
                    if (dy > 0) {
                        this.setDirection(Direction.DOWN);
                        y += speed;
                        if (tankImages != null) image = tankImages[1];
                    } else {
                        this.setDirection(Direction.UP);
                        y -= speed;
                        if (tankImages != null) image = tankImages[0];
                    }
                }
            }
        } else {
            // 正常追踪
            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx > 0) {
                    this.setDirection(Direction.RIGHT);
                    x += speed;
                    if (tankImages != null) image = tankImages[3];
                } else {
                    this.setDirection(Direction.LEFT);
                    x -= speed;
                    if (tankImages != null) image = tankImages[2];
                }
            } else {
                if (dy > 0) {
                    this.setDirection(Direction.DOWN);
                    y += speed;
                    if (tankImages != null) image = tankImages[1];
                } else {
                    this.setDirection(Direction.UP);
                    y -= speed;
                    if (tankImages != null) image = tankImages[0];
                }
            }
        }
    }

    private void patrolMove() {
        int targetX = patrolPoints[patrolIndex][0];
        int targetY = patrolPoints[patrolIndex][1];

        if (x < targetX) {
            setDirection(Direction.RIGHT);
            x += speed;
            if (tankImages != null) image = tankImages[3];
        } else if (x > targetX) {
            setDirection(Direction.LEFT);
            x -= speed;
            if (tankImages != null) image = tankImages[2];
        } else if (y < targetY) {
            setDirection(Direction.DOWN);
            y += speed;
            if (tankImages != null) image = tankImages[1];
        } else if (y > targetY) {
            setDirection(Direction.UP);
            y -= speed;
            if (tankImages != null) image = tankImages[0];
        }

        if (x == targetX && y == targetY) {
            patrolIndex = (patrolIndex + 1) % patrolPoints.length;
        }
    }

    private void ambushMove() {
        // 简单实现，暂时不做具体逻辑
        if (!isAmbushing) {
            isAmbushing = true;
            ambushTimer = 0;
        }
        ambushTimer++;
        if (ambushTimer >= 120) { // 伏击一段时间后结束
            isAmbushing = false;
            changeStrategyRandomly();
        }
    }

    private void retreatMove() {
        if (!isRetreating) {
            isRetreating = true;
            retreatTimer = 0;
        }
        retreatTimer++;

        int targetX = retreatPoint.x;
        int targetY = retreatPoint.y;

        if (x < targetX) {
            setDirection(Direction.RIGHT);
            x += speed;
            if (tankImages != null) image = tankImages[3];
        } else if (x > targetX) {
            setDirection(Direction.LEFT);
            x -= speed;
            if (tankImages != null) image = tankImages[2];
        } else if (y < targetY) {
            setDirection(Direction.DOWN);
            y += speed;
            if (tankImages != null) image = tankImages[1];
        } else if (y > targetY) {
            setDirection(Direction.UP);
            y -= speed;
            if (tankImages != null) image = tankImages[0];
        }

        if (x == targetX && y == targetY) {
            isRetreating = false;
            changeStrategyRandomly();
        }
    }

    public Bullet autoFire(BufferedImage bulletImage) {
        fireTimer++;
        if (fireTimer >= FIRE_INTERVAL) {
            fireTimer = 0;
            return fire(bulletImage);
        }
        return null;
    }
}

// 玩家坦克类
class PlayerTank extends Tank {
    private int health;

    public PlayerTank(int x, int y, Direction direction, int health, BufferedImage[] tankImages) {
        super(x, y, direction, tankImages);
        this.health = health;
    }

    public int getHealth() {
        return health;
    }

    public void reduceHealth() {
        health--;
    }

    public void increaseHealth() {
        health++;
    }

    public boolean isAlive() {
        return health > 0;
    }
}

// 游戏面板类
class GamePanel extends JPanel implements ActionListener, KeyListener {
    private PlayerTank playerTank;
    private List<EnemyTank> enemyTanks;
    private List<Bullet> playerBullets;
    private List<Bullet> enemyBullets;
    private Timer timer;
    private int enemySpawnTimer;
    private int currentLevel;
    private int[] enemySpawnIntervals = {10000, 5000, 1000}; // 每关敌人生成间隔（毫秒）
    private int[] playerInitialHealths = {2, 5, 5}; // 每关玩家初始血量
    private boolean gameOver;
    private BufferedImage[] playerTankImages; // 玩家坦克不同方向的图像
    private BufferedImage[] enemyTankImages; // 敌人坦克不同方向的图像
    private BufferedImage bulletImage; // 子弹图像
    private boolean imagesLoaded = false; // 图像是否加载成功
    private SoundPlayer hitSound; // 打击音效

    public GamePanel() {
        setPreferredSize(new Dimension(Tank.MAP_WIDTH, Tank.MAP_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        // 初始化打击音效
        hitSound = new SoundPlayer("/ciallo.wav");

        loadImages();

        // 如果图像加载成功，则开始游戏
        if (imagesLoaded) {
            selectLevel();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to load images. The game will use default shapes.", "Error", JOptionPane.ERROR_MESSAGE);
            selectLevel();
        }

        timer = new Timer(10, this);
        timer.start();
    }

    private void loadImages() {
        try {
            // 加载玩家坦克图像
            BufferedImage playerBaseImage = ImageIO.read(getClass().getResource("/zhutanke.png"));
            playerTankImages = new BufferedImage[4];
            playerTankImages[0] = playerBaseImage; // 向上
            playerTankImages[1] = rotateImage(playerBaseImage, 180); // 向下
            playerTankImages[2] = rotateImage(playerBaseImage, 270); // 向左
            playerTankImages[3] = rotateImage(playerBaseImage, 90); // 向右

            // 加载敌人坦克图像
            BufferedImage enemyBaseImage = ImageIO.read(getClass().getResource("/ai.png"));
            enemyTankImages = new BufferedImage[4];
            enemyTankImages[0] = enemyBaseImage; // 向上
            enemyTankImages[1] = rotateImage(enemyBaseImage, 180); // 向下
            enemyTankImages[2] = rotateImage(enemyBaseImage, 270); // 向左
            enemyTankImages[3] = rotateImage(enemyBaseImage, 90); // 向右

            // 加载子弹图像
            bulletImage = ImageIO.read(getClass().getResource("/bullet.png"));

            // 调整子弹图像大小为15x15像素
            if (bulletImage != null) {
                BufferedImage resizedBullet = new BufferedImage(15, 15, bulletImage.getType());
                Graphics2D g2d = resizedBullet.createGraphics();
                g2d.drawImage(bulletImage, 0, 0, 15, 15, null);
                g2d.dispose();
                bulletImage = resizedBullet;
            }

            imagesLoaded = true;
        } catch (IOException | NullPointerException e) {
            System.err.println("Error loading images: " + e.getMessage());
            imagesLoaded = false;
        }
    }

    private BufferedImage rotateImage(BufferedImage image, int degrees) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage rotated = new BufferedImage(width, height, image.getType());
        Graphics2D g2d = rotated.createGraphics();

        // 设置旋转中心和角度
        g2d.rotate(Math.toRadians(degrees), width / 2, height / 2);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    private void selectLevel() {
        String[] options = {"Level 1", "Level 2", "Level 3"};
        int choice = JOptionPane.showOptionDialog(null, "Select a level:", "Level Selection",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        currentLevel = choice + 1;

        initializeGame();
    }

    private void initializeGame() {
        if (imagesLoaded) {
            playerTank = new PlayerTank(Tank.MAP_WIDTH / 2, Tank.MAP_HEIGHT - 50, Direction.UP, playerInitialHealths[currentLevel - 1], playerTankImages);
        } else {
            // 如果图像加载失败，使用默认的坦克图像
            playerTank = new PlayerTank(Tank.MAP_WIDTH / 2, Tank.MAP_HEIGHT - 50, Direction.UP, playerInitialHealths[currentLevel - 1], null);
        }

        enemyTanks = new ArrayList<>();
        playerBullets = new ArrayList<>();
        enemyBullets = new ArrayList<>();
        enemySpawnTimer = 0;
        gameOver = false;

        // 每关只生成1个初始敌人
        Random rand = new Random();
        int x = rand.nextInt(Tank.MAP_WIDTH - 40);
        if (imagesLoaded) {
            EnemyTank enemyTank = new EnemyTank(x, 0, Direction.DOWN, playerTank, enemyTankImages);
            enemyTanks.add(enemyTank);
        } else {
            // 如果图像加载失败，使用默认的坦克图像
            EnemyTank enemyTank = new EnemyTank(x, 0, Direction.DOWN, playerTank, null);
            enemyTanks.add(enemyTank);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (gameOver) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("Game Over!", Tank.MAP_WIDTH / 2 - 100, Tank.MAP_HEIGHT / 2);
            return;
        }

        // 显示当前关卡
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("Level: " + currentLevel, 10, 60);

        // 绘制玩家坦克
        if (imagesLoaded) {
            playerTank.draw(g);
        } else {
            g.setColor(Color.GREEN);
            playerTank.draw(g);
        }

        // 绘制敌人坦克
        for (EnemyTank enemyTank : enemyTanks) {
            if (imagesLoaded) {
                enemyTank.draw(g);
            } else {
                g.setColor(Color.RED);
                enemyTank.draw(g);
            }
        }

        // 绘制玩家子弹
        for (Bullet bullet : playerBullets) {
            if (imagesLoaded) {
                bullet.draw(g);
            } else {
                g.setColor(Color.YELLOW);
                bullet.draw(g);
            }
        }

        // 绘制敌人子弹
        for (Bullet bullet : enemyBullets) {
            if (imagesLoaded) {
                bullet.draw(g);
            } else {
                g.setColor(Color.MAGENTA);
                bullet.draw(g);
            }
        }

        // 显示玩家血量
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("Health: " + playerTank.getHealth(), 10, 30);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver) return;

        // 敌人生成
        enemySpawnTimer += 10;
        if (enemySpawnTimer >= enemySpawnIntervals[currentLevel - 1]) {
            enemySpawnTimer = 0;
            Random rand = new Random();
            int x = rand.nextInt(Tank.MAP_WIDTH - 40);
            if (imagesLoaded) {
                EnemyTank enemyTank = new EnemyTank(x, 0, Direction.DOWN, playerTank, enemyTankImages);
                enemyTanks.add(enemyTank);
            } else {
                EnemyTank enemyTank = new EnemyTank(x, 0, Direction.DOWN, playerTank, null);
                enemyTanks.add(enemyTank);
            }
        }

        // 玩家坦克移动
        playerTank.move();

        // 敌人坦克移动和射击
        Iterator<EnemyTank> enemyIterator = enemyTanks.iterator();
        while (enemyIterator.hasNext()) {
            EnemyTank enemyTank = enemyIterator.next();
            enemyTank.move();
            Bullet enemyBullet = enemyTank.autoFire(bulletImage);
            if (enemyBullet != null) {
                enemyBullets.add(enemyBullet);
            }
        }

        // 玩家子弹移动和碰撞检测
        Iterator<Bullet> playerBulletIterator = playerBullets.iterator();
        while (playerBulletIterator.hasNext()) {
            Bullet bullet = playerBulletIterator.next();
            bullet.move();
            if (!bullet.isActive()) {
                playerBulletIterator.remove();
            } else {
                Iterator<EnemyTank> enemyTankIterator = enemyTanks.iterator();
                while (enemyTankIterator.hasNext()) {
                    EnemyTank enemyTank = enemyTankIterator.next();
                    if (bullet.checkCollision(enemyTank)) {
                        bullet.setActive(false);
                        enemyTankIterator.remove();

                        // 播放打击音效
                        hitSound.play();

                        // 玩家击败敌人后增加一滴血量
                        playerTank.increaseHealth();
                        break;
                    }
                }
            }
        }

        // 敌人子弹移动和碰撞检测
        Iterator<Bullet> enemyBulletIterator = enemyBullets.iterator();
        while (enemyBulletIterator.hasNext()) {
            Bullet bullet = enemyBulletIterator.next();
            bullet.move();
            if (!bullet.isActive()) {
                enemyBulletIterator.remove();
            } else if (bullet.checkCollision(playerTank)) {
                bullet.setActive(false);
                playerTank.reduceHealth();
                if (!playerTank.isAlive()) {
                    gameOver = true;
                }
            }
        }

        // 检查是否击败所有敌人
        if (enemyTanks.isEmpty()) {
            gameOver = true;
            int choice = JOptionPane.showConfirmDialog(null, "You won! Do you want to select another level?", "Level Cleared", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                selectLevel();
            }
        }

        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver) return;

        int keyCode = e.getKeyCode();

        // 处理移动键
        switch (keyCode) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_S:
            case KeyEvent.VK_A:
            case KeyEvent.VK_D:
                playerTank.addPressedKey(keyCode);
                break;

            // 处理空格键（射击）
            case KeyEvent.VK_SPACE:
                Bullet bullet = playerTank.fire(bulletImage);
                playerBullets.add(bullet);
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (gameOver) return;

        int keyCode = e.getKeyCode();

        // 处理移动键释放
        switch (keyCode) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_S:
            case KeyEvent.VK_A:
            case KeyEvent.VK_D:
                playerTank.removePressedKey(keyCode);
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // 不需要实现
    }
}

// 主游戏类
public class TankGame extends JFrame {
    public TankGame() {
        setTitle("Tank Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel gamePanel = new GamePanel();
        add(gamePanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TankGame());
    }
}