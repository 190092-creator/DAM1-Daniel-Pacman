package es.sanalberto.daniel.pacman;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * PAC-MAN — JavaFX + JDK 21
 * Totalmente migrado a JavaFX (Canvas, GraphicsContext, AnimationTimer, Scene/Stage).
 *
 * Puntuación: punto normal +10 | bola de poder +50 | fantasma +250 | fruta extra +100
 */
public final class PacMan extends Application {

    // ============================== CONSTANTES ==============================
    private enum State { MENU, PLAYING, PAUSED, LEVEL_INTRO, GAME_OVER, WIN }
    private enum Dir { UP, DOWN, LEFT, RIGHT, NONE }

    private record Point(int x, int y) {}

    private static final int CELL = 26;                 // px por celda
    private static final int HUD_H = 56;                // alto del HUD
    private static final int START_LIVES = 3;

    private static final int DOT_SCORE = 10, POWER_SCORE = 50, GHOST_SCORE = 250, FRUIT_SCORE = 100;
    private static final int PAC_BASE = 120;            // px/s
    private static final int FRUIT_INTERVAL = 12_000;   // reaparece cada 12 s (ms)
    private static final int FRUIT_LIFE = 8_000;        // vive 8 s (ms)
    private static final int GHOSTS_PER_LEVEL = 4;

    /** Datos por nivel: {nombre, velocidadFantasma(px/s), segundosModoPoder}. */
    private static final Object[][] LEVELS = {
            {"NIVEL 1 · Tranquilo",  62.0, 8.0},
            {"NIVEL 2 · Animado",    82.0, 7.0},
            {"NIVEL 3 · Rápido",    102.0, 6.0},
            {"NIVEL 4 · Frenético", 122.0, 5.0},
    };

    /** Modos scatter/chase globales (segundos). El último queda fijo en chase. */
    private static final double[] MODE_SEQ = {7, 20, 7, 20, 5, 20, 5};
    private static final boolean[] MODE_IS_CHASE = {false, true, false, true, false, true, true};

    private static final Color BG = Color.rgb(8, 8, 22);
    private static final Color WALL_BLUE = Color.rgb(45, 65, 215);
    private static final Color WALL_INNER_BLUE = Color.rgb(120, 150, 255);
    private static final Color GATE_PINK = Color.rgb(255, 150, 220);
    private static final Color PAC_YELLOW = Color.rgb(255, 222, 50);
    private static final Color FRIGHT_BLUE = Color.rgb(38, 60, 200);
    private static final Color FRIGHT_FLASH = Color.rgb(235, 235, 255);
    private static final Color DOT_COLOR = Color.rgb(255, 185, 105);
    private static final Color[] GHOST_COLORS = {
            Color.rgb(255, 60, 60),   // Blinky (rojo)
            Color.rgb(255, 170, 190), // Pinky (rosa)
            Color.rgb(70, 220, 235),  // Inky (cian)
            Color.rgb(255, 140, 40)   // Clyde (naranja)
    };

    // ============================ LABERINTOS ===============================
    // #=muro  .=punto  o=bola de poder  ' '=vacío  -=puerta  G=spawn fantasma
    // T=fruta (bajo la casa)  P=spawn Pac-Man
    private static final String[][] MAZES = {
            { // -------- NIVEL 1: clásico, pasillos anchos --------
                    "###########################",
                    "#............#............#",
                    "#.####.#####.#.#####.####.#",
                    "#o####.#####.#.#####.####o#",
                    "#.####.#####.#.#####.####.#",
                    "#..........................#",
                    "#.####.#.#######.#.####.###",
                    "#.####.#.#######.#.####.###",
                    "#......#....#....#......  #",
                    "######.###  #  ###.###### #",
                    "     #.###  #  ###.#####  #",
                    "     #.##       ##.#####  #",
                    "     #.## ##-## ##.#####  #",
                    "######.## #   # ##.###### #",
                    "#.....    #   #    ...... #",
                    "######.## #   # ##.###### #",
                    "     #.## ##### ##.#####  #",
                    "     #.##       ##.#####  #",
                    "     #.## ##### ##.#####  #",
                    "######.## ##### ##.###### #",
                    "#............#............#",
                    "#.####.#####.#.#####.####.#",
                    "#.####.#####.#.#####.####.#",
                    "#o..##.......P.......##..o#",
                    "###.##.#.#######.#.##.#####",
                    "###.##.#.#######.#.##.#####",
                    "#......#....#....#........#",
                    "#.#########.#.#########.###",
                    "#.#########.#.#########.###",
                    "#..........................#",
                    "###########################"},
            { // -------- NIVEL 2: más pasillos y cruces --------
                    "###########################",
                    "#o...#........#........#..o#",
                    "#.##.#.######.#.######.#.##",
                    "#.#..#.#....#.#.#....#.#..#",
                    "#.#.##.#.##.#.#.#.##.#.##.#",
                    "#.#....#.#.....#.#....#...#",
                    "#.####.#.#.#####.#.##.###.#",
                    "#......#.#...#...##........#",
                    "######.#####.#.#####.######",
                    "#....#.....#.#.#.....#....#",
                    "#.##.#####.#.#.#.#####.##.#",
                    "#.#......#.#.#.#.#......#.#",
                    "#.#.####.#.....#.#.####.#.#",
                    "#.#.#  ###--###  #.#..#.#.#",
                    "#.#.#  #     #  #.#.#...#.#",
                    "#.#.#  # G G #  #.#.###.#.#",
                    "#.#.#  #     #  #.#.#...#.#",
                    "#.#.#  #######  #.#.#.#.#.#",
                    "#.#.####.....####.#.#.#.#.#",
                    "#.#....#.#.#.#....#.#.#.#.#",
                    "#.####.#.#.#.#.####.#.#.#.#",
                    "#......#.#.#.#......#.#...#",
                    "#.######.#.#.######.#.###.#",
                    "#....#...#.#.#...#...#....#",
                    "####.#.###.#.#.#.###.#.####",
                    "#....#.#...#.#.#.#...#....#",
                    "#.####.#.###.#.#.#.####.#.#",
                    "#.#....#.....#.....#....#.#",
                    "#.#.####################.#.#",
                    "#o..........P............o#",
                    "###########################"},
            { // -------- NIVEL 3: denso, muchos giros --------
                    "###########################",
                    "#.........#...#...........#",
                    "#.#######.#.#.#.#########.#",
                    "#.#.....#.#.#.#.....#...#.#",
                    "#.#.###.#.#.#.#####.#.#.#.#",
                    "#.#.#...#.#.#.....#.#.#.#.#",
                    "#.#.#.###.#.#####.#.#.#.#.#",
                    "#.#.#.#...#.....#.#.#.#.#.#",
                    "#.#.#.#.#######.#.#.#.#.#.#",
                    "#.#.#.#.#.....#.#.#.#.#.#.#",
                    "#.#.#.#.#.###.#.#.#.#.#.#.#",
                    "#.#.#.#.#.#o..#.#.#.#.#.#.#",
                    "#.#.#.#.#.#.###.#.#.#.#.#.#",
                    "#.#.#.#.#.#.#...#.#.#.#.#.#",
                    "#.#.#.#.#.#.#.###.#.#.#.#.#",
                    "#.#.#.#.#.#.#.# GG#.#.#.#.#",
                    "#.#.#.#.#.#.#.#   #.#.#.#.#",
                    "#.#.#.#.#.#.#.#####.#.#.#.#",
                    "#.#.#.#.#.#.#.......#.#.#.#",
                    "#.#.#.#.#.#.#########.#.#.#",
                    "#.#.#.#.#.#...........#.#.#",
                    "#.#.#.#.#.#############.#.#",
                    "#.#.#.#.#...............#.#",
                    "#.#.#.#.#################.#",
                    "#.#.#.#...................#",
                    "#.#.#.#####################",
                    "#.#.#.....................#",
                    "#.#.#####################.#",
                    "#.#.......................#",
                    "#o#####.#######.#########o#",
                    "#############P#############"},
            { // -------- NIVEL 4: abierto y traicionero --------
                    "###########################",
                    "#......#.....#.....#......#",
                    "#.####.#.###.#.###.#.####.#",
                    "#.#........................#",
                    "#.#.####.#####.#####.####.#",
                    "#.#....#.#...#.#...#.#....#",
                    "#.####.#.#.#.#.#.#.#.#.####",
                    "#......#.#.#.#.#.#.#......#",
                    "#.####.#.#.#.#.#.#.#.####.#",
                    "#.#....#.#.#.#.#.#.#....#.#",
                    "#.#.####.#.#.#.#.#.####.#.#",
                    "#.#.#....#.......#....#.#.#",
                    "#.#.#.####.#####.####.#.#.#",
                    "#.#.#.#  ##-----##  #.#.#.#",
                    "#.#...#  #       #  #...#.#",
                    "#.#.###  #   G   #  ###.#.#",
                    "#.#.#    #       #    #.#.#",
                    "#.#.#.###############.#.#.#",
                    "#.#.#.................#.#.#",
                    "#.#.###################.#.#",
                    "#.#.....................#.#",
                    "#.#######################.#",
                    "#.........................#",
                    "#.#####.###.###.###.#####.#",
                    "#.#...#.#...#.#.#...#...#.#",
                    "#.#.#.#.#.###.#.###.#.#.#.#",
                    "#.#.#...#.....#.....#.#.#.#",
                    "#.#.###################.#.#",
                    "#.#.....................#.#",
                    "#o..........P...........o.#",
                    "###########################"}
    };

    // ============================== ESTADO =================================
    private State state = State.MENU;
    private AnimationTimer timer;
    private Canvas canvas;
    private GraphicsContext gc;

    private char[][] grid;
    private int rows, cols, levelIdx, score, lives, dotsLeft;
    private boolean powerActive, fruitVisible, bonusChainActive;
    private double powerTime, modeClock, fruitClock, fruitLife, introClock, flashClock;
    private int modeIndex, ghostsEatenChain, deathAnimT;
    private Point fruitCell, pacSpawn;

    private final double[] px = new double[GHOSTS_PER_LEVEL];
    private final double[] py = new double[GHOSTS_PER_LEVEL];
    private final Dir[] gdir = new Dir[GHOSTS_PER_LEVEL];
    private final int[] ghostMode = new int[GHOSTS_PER_LEVEL]; // 0 casa 1 saliendo 2 fuera 3 comido 4 entrando
    private final double[] ghostHouseTime = new double[GHOSTS_PER_LEVEL];
    private final int[] scatterTX = new int[GHOSTS_PER_LEVEL];
    private final int[] scatterTY = new int[GHOSTS_PER_LEVEL];

    private double pacX, pacY;
    private int mouthT;
    private Dir pacDir = Dir.NONE, pacQueued = Dir.NONE;

    private final List<double[]> popupFx = new ArrayList<>(); // {x, y, age, score}
    private Button btnStart, btnPause, btnMenu;

    // ============================== ENTRY POINT ============================
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        int canvasW = MAZES[0][0].length() * CELL;
        int canvasH = HUD_H + MAZES[0].length * CELL;

        canvas = new Canvas(canvasW, canvasH);
        gc = canvas.getGraphicsContext2D();

        btnStart = makeButton("▶  INICIAR", this::startGame);
        btnPause = makeButton("⏸ PAUSA", this::togglePause);
        btnMenu  = makeButton("MENÚ", () -> {
            state = State.MENU;
            syncButtons();
        });
        btnPause.setDisable(true);
        btnMenu.setDisable(true);

        HBox bar = new HBox(14, btnStart, btnPause, btnMenu);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 10, 0));
        bar.setStyle("-fx-background-color: #0e0e22;");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #080816;");
        root.setCenter(canvas);
        root.setBottom(bar);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> handleKey(e.getCode()));

        stage.setTitle("PAC-MAN · JavaFX");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> timer.stop());
        stage.show();

        canvas.requestFocus();

        timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double dt = Math.min(0.05, (now - lastNano) / 1_000_000_000.0);
                lastNano = now;

                tick(dt);
                render();
            }
        };
        timer.start();
    }

    private Button makeButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setPrefSize(150, 34);

        String baseStyle = "-fx-text-fill: black; " +
                "-fx-font-family: 'Monospaced', monospace; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 14px; " +
                "-fx-background-radius: 16px; ";

        String normalStyle = baseStyle + "-fx-background-color: #ffde32; -fx-cursor: hand;";
        String hoverStyle = baseStyle + "-fx-background-color: #ffe866; -fx-cursor: hand;";
        String pressedStyle = baseStyle + "-fx-background-color: #e6b800; -fx-cursor: hand;";
        String disabledStyle = baseStyle + "-fx-background-color: #444455; -fx-text-fill: #777788;";

        b.setStyle(normalStyle);
        b.setOnMouseEntered(e -> { if (!b.isDisabled()) b.setStyle(hoverStyle); });
        b.setOnMouseExited(e -> { if (!b.isDisabled()) b.setStyle(normalStyle); });
        b.setOnMousePressed(e -> { if (!b.isDisabled()) b.setStyle(pressedStyle); });
        b.setOnMouseReleased(e -> { if (!b.isDisabled()) b.setStyle(hoverStyle); });
        b.disabledProperty().addListener((obs, oldVal, newVal) -> b.setStyle(newVal ? disabledStyle : normalStyle));
        b.setOnAction(e -> action.run());
        return b;
    }

    // ============================== TECLADO ================================
    private void handleKey(KeyCode code) {
        Dir d = switch (code) {
            case UP, W    -> Dir.UP;
            case DOWN, S  -> Dir.DOWN;
            case LEFT, A  -> Dir.LEFT;
            case RIGHT, D -> Dir.RIGHT;
            default       -> null;
        };
        if (d != null) {
            if (state == State.MENU) startGame();
            else if (state == State.PLAYING || state == State.PAUSED) pacQueued = d;
            if (state == State.PAUSED) { state = State.PLAYING; syncButtons(); }
            return;
        }
        switch (code) {
            case P, ESCAPE -> {
                if (state == State.PLAYING || state == State.PAUSED) togglePause();
            }
            case ENTER, SPACE -> {
                if (state == State.MENU) startGame();
                else if (state == State.GAME_OVER || state == State.WIN) {
                    state = State.MENU;
                    syncButtons();
                }
            }
            default -> {}
        }
    }

    private void togglePause() {
        if (state == State.PLAYING) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.PLAYING;
        syncButtons();
    }

    private void syncButtons() {
        btnStart.setText(state == State.PLAYING || state == State.PAUSED ? "↻ REINICIAR" : "▶  INICIAR");
        btnPause.setDisable(state != State.PLAYING && state != State.PAUSED);
        btnPause.setText(state == State.PAUSED ? "▶ SEGUIR" : "⏸ PAUSA");
        btnMenu.setDisable(state == State.MENU);
    }

    // ============================== NIVELES ================================
    private void startGame() {
        score = 0;
        lives = START_LIVES;
        levelIdx = 0;
        loadLevel(0);
        state = State.LEVEL_INTRO;
        introClock = 2.0;
        syncButtons();
        canvas.requestFocus();
    }

    private void loadLevel(int idx) {
        String[] raw = MAZES[idx];
        rows = raw.length;
        cols = Arrays.stream(raw).mapToInt(String::length).max().orElse(0);
        grid = new char[rows][cols];
        dotsLeft = 0;
        fruitCell = null;

        List<Point> gSpawn = new ArrayList<>();
        int minGR = Integer.MAX_VALUE, maxGR = -1, minGC = Integer.MAX_VALUE, maxGC = -1;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = c < raw[r].length() ? raw[r].charAt(c) : '#';
                switch (ch) {
                    case 'P' -> { pacSpawn = new Point(c, r); ch = ' '; }
                    case 'G' -> {
                        gSpawn.add(new Point(c, r));
                        minGR = Math.min(minGR, r); maxGR = Math.max(maxGR, r);
                        minGC = Math.min(minGC, c); maxGC = Math.max(maxGC, c);
                        ch = ' ';
                    }
                    case 'T' -> { fruitCell = new Point(c, r); ch = ' '; }
                    case '.', 'o' -> dotsLeft++;
                    default -> {}
                }
                grid[r][c] = ch;
            }
        }
        if (pacSpawn == null) pacSpawn = new Point(1, 1);
        if (fruitCell == null && maxGR >= 0) { // respaldo: buscar hueco bajo la casa
            outer:
            for (int r = maxGR + 1; r < rows; r++) {
                for (int c = minGC; c <= maxGC; c++) {
                    if (isWalk(r, c, false)) { fruitCell = new Point(c, r); break outer; }
                }
            }
        }

        // Esquinas scatter (con margen del borde)
        int[][] corners = {{1, 1}, {cols - 2, 1}, {1, rows - 2}, {cols - 2, rows - 2}};
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
            Point sp = gSpawn.isEmpty() ? new Point(cols / 2, rows / 2) : gSpawn.get(i % gSpawn.size());
            px[i] = sp.x * CELL + CELL / 2.0;
            py[i] = sp.y * CELL + CELL / 2.0;
            ghostMode[i] = 0;
            ghostHouseTime[i] = 0;
            gdir[i] = Dir.UP;
            scatterTX[i] = corners[i][0];
            scatterTY[i] = corners[i][1];
        }

        powerActive = false;
        powerTime = 0;
        modeClock = 0;
        modeIndex = 0;
        fruitClock = 0;
        fruitVisible = false;
        bonusChainActive = false;
        resetPositions();
    }

    private void resetPositions() {
        pacX = pacSpawn.x * CELL + CELL / 2.0;
        pacY = pacSpawn.y * CELL + CELL / 2.0;
        pacDir = Dir.NONE;
        pacQueued = Dir.NONE;
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
            Point sp = findHouseInterior(i);
            px[i] = sp.x * CELL + CELL / 2.0;
            py[i] = sp.y * CELL + CELL / 2.0;
            ghostMode[i] = 0;
            ghostHouseTime[i] = 0;
            gdir[i] = Dir.UP;
        }
        powerActive = false;
        powerTime = 0;
    }

    private Point findHouseInterior(int i) {
        int bestD = Integer.MAX_VALUE;
        Point best = new Point(1, 1);
        int cx = cols / 2, cy = rows / 2;
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                if (grid[r][c] == ' ' && isSurroundedish(r, c)) {
                    int d = Math.abs(r - cy) + Math.abs(c - cx) + ((i + c) % 2);
                    if (d < bestD) { bestD = d; best = new Point(c, r); }
                }
            }
        }
        return best;
    }

    private boolean isSurroundedish(int r, int c) {
        int walls = 0;
        for (Dir d : new Dir[]{Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT}) {
            int nr = r + drow(d), nc = c + dcol(d);
            if (!inside(nr, nc) || grid[nr][nc] == '#' || grid[nr][nc] == '-') walls++;
        }
        return walls >= 3;
    }

    // ============================== BUCLE ==================================
    private void tick(double dt) {
        if (state == State.PLAYING) {
            update(dt);
        } else if (state == State.LEVEL_INTRO) {
            introClock -= dt;
            if (introClock <= 0) state = State.PLAYING;
        }
        if (state == State.GAME_OVER && deathAnimT > 0) {
            deathAnimT--;
        }
        popupFx.removeIf(p -> (p[2] += dt * 60) > 70);
        if (flashClock > 0) flashClock -= dt;
    }

    private void update(double dt) {
        // Modo poder
        if (powerActive) {
            powerTime -= dt;
            if (powerTime <= 0) powerActive = false;
        }
        // Secuencia scatter/chase
        if (modeIndex < MODE_SEQ.length) {
            modeClock += dt;
            if (modeClock >= MODE_SEQ[modeIndex]) {
                modeClock = 0;
                modeIndex++;
                reverseAll();
            }
        }
        // Fruta
        fruitClock += dt * 1000;
        if (!fruitVisible && fruitClock >= FRUIT_INTERVAL && fruitCell != null) {
            fruitVisible = true;
            fruitLife = FRUIT_LIFE;
            fruitClock = 0;
        } else if (fruitVisible) {
            fruitLife -= dt * 1000;
            if (fruitLife <= 0) fruitVisible = false;
        }

        movePac(dt);
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) moveGhost(i, dt);
        checkCollisions();

        if (dotsLeft <= 0) nextLevel();
    }

    // ============================== PAC-MAN ================================
    private void movePac(double dt) {
        int speed = PAC_BASE + levelIdx * 8;
        if (pacDir != Dir.NONE && pacQueued != pacDir) {
            if (canMove(pacX, pacY, pacQueued, false)) pacDir = pacQueued;
        }
        if (pacDir == Dir.NONE && pacQueued != Dir.NONE && canMove(pacX, pacY, pacQueued, false)) {
            pacDir = pacQueued;
        }

        double remaining = speed * dt;
        while (remaining > 0.0001 && pacDir != Dir.NONE) {
            double distToCenter = distToAxisCenter(pacX, pacY, pacDir);
            double step = Math.min(remaining, Math.max(distToCenter, 0.0001));
            if (distToCenter < 0.5) {
                if (pacQueued != pacDir && canMove(pacX, pacY, pacQueued, false)) pacDir = pacQueued;
                if (!canMove(pacX, pacY, pacDir, false)) { pacDir = Dir.NONE; break; }
                step = Math.min(remaining, CELL / 2.0);
            }
            pacX += dcol(pacDir) * step;
            pacY += drow(pacDir) * step;
            remaining -= step;

            // efecto túnel
            if (pacX < -CELL / 2.0) pacX = cols * CELL + CELL / 2.0 - 1;
            if (pacX > cols * CELL + CELL / 2.0) pacX = -CELL / 2.0 + 1;
            mouthT++;
        }
    }

    private double distToAxisCenter(double x, double y, Dir d) {
        double cx = Math.floor(x / CELL) * CELL + CELL / 2.0;
        double cy = Math.floor(y / CELL) * CELL + CELL / 2.0;
        return switch (d) {
            case RIGHT -> ((cx + CELL) - x);
            case LEFT  -> (x - (cx - CELL));
            case DOWN  -> ((cy + CELL) - y);
            case UP    -> (y - (cy - CELL));
            default    -> 0.0;
        };
    }

    private boolean canMove(double x, double y, Dir d, boolean ghostThroughDoor) {
        double nx = x + dcol(d) * (CELL / 2.0 + 0.5);
        double ny = y + drow(d) * (CELL / 2.0 + 0.5);
        int c = (int) Math.floor(nx / CELL);
        int r = (int) Math.floor(ny / CELL);
        if (!inside(r, c)) return true; // túnel
        return isWalk(r, c, ghostThroughDoor);
    }

    private boolean isWalk(int r, int c, boolean ghostThroughDoor) {
        char ch = grid[r][c];
        if (ch == '#') return false;
        return ch != '-' || ghostThroughDoor;
    }

    // ============================== FANTASMAS ==============================
    private boolean globalChase() {
        return modeIndex >= MODE_SEQ.length || MODE_IS_CHASE[modeIndex];
    }

    private void reverseAll() {
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
            if (ghostMode[i] == 2) gdir[i] = opposite(gdir[i]);
        }
    }

    private void moveGhost(int i, double dt) {
        double base = (double) LEVELS[levelIdx][1];
        double speed = switch (ghostMode[i]) {
            case 3 -> base * 2.1;               // ojos: muy rápidos
            case 2 -> powerActive ? base * 0.55 : base;
            default -> base * 0.8;
        };
        if (inTunnelRow(i)) speed *= 0.6;

        if (ghostMode[i] == 0) { // rebota en la casa y espera turno
            ghostHouseTime[i] += dt;
            py[i] += (gdir[i] == Dir.UP ? -1 : 1) * base * 0.5 * dt;
            int cy = (int) ((py[i] - CELL / 2.0) / CELL);
            if (gdir[i] == Dir.UP && py[i] <= cy * CELL + CELL / 2.0 + 2) gdir[i] = Dir.DOWN;
            if (gdir[i] == Dir.DOWN && py[i] >= cy * CELL + CELL / 2.0 - 2) gdir[i] = Dir.UP;
            if (powerTime <= 0 && ghostHouseTime[i] > i * 2.2) ghostMode[i] = 1;
            return;
        }

        boolean throughDoor = ghostMode[i] == 1 || ghostMode[i] == 4;
        double remaining = speed * dt;
        int guard = 0;
        while (remaining > 0.0001 && guard++ < 8) {
            int c = (int) (px[i] / CELL), r = (int) (py[i] / CELL);
            double cx = c * CELL + CELL / 2.0, cyy = r * CELL + CELL / 2.0;
            double dx = Math.abs(px[i] - cx), dy = Math.abs(py[i] - cyy);
            if (dx < 0.75 && dy < 0.75) {
                px[i] = cx; py[i] = cyy;
                chooseGhostDir(i, r, c, throughDoor);
            }
            double step = Math.min(remaining, CELL / 2.0);
            px[i] += dcol(gdir[i]) * step;
            py[i] += drow(gdir[i]) * step;
            remaining -= step;
            if (px[i] < -CELL / 2.0) px[i] = cols * CELL + CELL / 2.0 - 1;
            if (px[i] > cols * CELL + CELL / 2.0) px[i] = -CELL / 2.0 + 1;
        }

        // llegada a la puerta / interior
        if (ghostMode[i] == 1) {
            Point door = findDoor();
            if (door != null && py[i] < door.y * CELL) ghostMode[i] = 2;
        }
        if (ghostMode[i] == 4) {
            Point door = findDoor();
            if (door != null && Math.hypot(px[i] - (door.x * CELL + CELL / 2.0), py[i] - (door.y * CELL + CELL)) < CELL * 0.6) {
                ghostMode[i] = 0;
                ghostHouseTime[i] = 0;
                gdir[i] = Dir.UP;
            }
        }
    }

    private boolean inTunnelRow(int i) {
        int r = (int) (py[i] / CELL);
        return r >= 0 && r < rows && grid[r][0] != '#' && grid[r][cols - 1] != '#';
    }

    private Point findDoor() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == '-') return new Point(c, r);
            }
        }
        return null;
    }

    private void chooseGhostDir(int i, int r, int c, boolean throughDoor) {
        List<Dir> opts = new ArrayList<>();
        for (Dir d : Dir.values()) {
            if (d == Dir.NONE || d == opposite(gdir[i])) continue;
            int nr = r + drow(d), nc = c + dcol(d);
            if (!inside(nr, nc)) { opts.add(d); continue; }
            if (isWalk(nr, nc, throughDoor)) opts.add(d);
        }
        if (opts.isEmpty()) { gdir[i] = opposite(gdir[i]); return; }

        Dir best;
        if (ghostMode[i] == 3 || ghostMode[i] == 4) {          // ojos → puerta
            Point t = findDoor(); if (t == null) t = new Point(c, r);
            best = bestToward(opts, r, c, t.y, t.x);
        } else if (ghostMode[i] == 1) {                        // salir de casa
            Point t = findDoor(); if (t == null) t = new Point(c, r - 1);
            best = bestToward(opts, r, c, t.y - 1, t.x);
        } else if (powerActive && ghostMode[i] == 2) {          // huir: alejarse de Pac-Man
            best = opts.get(new Random().nextInt(opts.size())); // huida caótica
            double bd = -1;
            for (Dir d : opts) {
                int nr = r + drow(d), nc = c + dcol(d);
                double dist = Math.hypot(nc * CELL - pacX, nr * CELL - pacY);
                if (dist > bd) { bd = dist; best = d; }
            }
        } else {                                               // scatter / chase
            int[] t = ghostTarget(i);
            best = bestToward(opts, r, c, t[0], t[1]);
        }
        gdir[i] = best;
    }

    private int[] ghostTarget(int i) {
        if (!globalChase()) return new int[]{scatterTY[i], scatterTX[i]};
        int pr = (int) (pacY / CELL), pc = (int) (pacX / CELL);
        return switch (i) {
            case 0 -> new int[]{pr, pc};                                   // Blinky: directo
            case 1 -> new int[]{pr + drow(pacDir) * 4, pc + dcol(pacDir) * 4}; // Pinky: emboscada
            case 2 -> new int[]{pr + drow(pacDir) * 2 - ((int)(py[0] / CELL) - pr),
                    pc + dcol(pacDir) * 2 - ((int)(px[0] / CELL) - pc)};  // Inky: vector doble
            default -> {                                                    // Clyde: tímido
                double d = Math.hypot((px[3] / CELL) - pc, (py[3] / CELL) - pr);
                yield d > 8 ? new int[]{pr, pc} : new int[]{scatterTY[3], scatterTX[3]};
            }
        };
    }

    private Dir bestToward(List<Dir> opts, int r, int c, int tr, int tc) {
        Dir best = opts.get(0);
        double bd = Double.MAX_VALUE;
        for (Dir d : opts) {
            int nr = r + drow(d), nc = c + dcol(d);
            double dist = (nr - tr) * (nr - tr) + (nc - tc) * (nc - tc);
            if (dist < bd) { bd = dist; best = d; }
        }
        return best;
    }

    // ============================ COLISIONES ===============================
    private void checkCollisions() {
        int pr = (int) Math.floor(pacY / CELL), pc = (int) Math.floor(pacX / CELL);
        if (inside(pr, pc)) {
            char ch = grid[pr][pc];
            if (ch == '.') {
                grid[pr][pc] = ' ';
                dotsLeft--;
                addScore(DOT_SCORE, pc, pr);
            } else if (ch == 'o') {
                grid[pr][pc] = ' ';
                dotsLeft--;
                addScore(POWER_SCORE, pc, pr);
                powerActive = true;
                powerTime = (double) LEVELS[levelIdx][2];
                ghostsEatenChain = 0;
                for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
                    if (ghostMode[i] == 2) gdir[i] = opposite(gdir[i]);
                }
            }
        }
        // Fruta extra (+100)
        if (fruitVisible && fruitCell != null && pr == fruitCell.y && pc == fruitCell.x
                && Math.hypot(pacX - (fruitCell.x * CELL + CELL / 2.0), pacY - (fruitCell.y * CELL + CELL / 2.0)) < CELL * 0.7) {
            fruitVisible = false;
            bonusChainActive = true;
            addScore(FRUIT_SCORE, fruitCell.x, fruitCell.y);
        }
        // Fantasmas
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
            if (ghostMode[i] != 2) continue;
            if (Math.hypot(px[i] - pacX, py[i] - pacY) < CELL * 0.7) {
                if (powerActive) {
                    ghostsEatenChain++;
                    int pts = GHOST_SCORE * ghostsEatenChain; // 250, 500, 1000...
                    addScore(pts, (int) (px[i] / CELL), (int) (py[i] / CELL));
                    ghostMode[i] = 3;
                } else {
                    pacDie();
                    return;
                }
            }
        }
    }

    private void addScore(int pts, int c, int r) {
        score += pts;
        popupFx.add(new double[]{c * CELL + CELL / 2.0, r * CELL + CELL / 2.0, 0, pts});
        if (pts >= GHOST_SCORE) flashClock = 0.18;
    }

    private void pacDie() {
        lives--;
        deathAnimT = 60;
        if (lives <= 0) {
            state = State.GAME_OVER;
            syncButtons();
        } else {
            resetPositions();
        }
    }

    private void nextLevel() {
        if (levelIdx + 1 >= MAZES.length) {
            state = State.WIN;
            syncButtons();
            return;
        }
        levelIdx++;
        loadLevel(levelIdx);
        state = State.LEVEL_INTRO;
        introClock = 2.0;
    }

    // ============================== UTILIDADES =============================
    private boolean inside(int r, int c) { return r >= 0 && r < rows && c >= 0 && c < cols; }
    private int drow(Dir d) { return d == Dir.UP ? -1 : d == Dir.DOWN ? 1 : 0; }
    private int dcol(Dir d) { return d == Dir.LEFT ? -1 : d == Dir.RIGHT ? 1 : 0; }
    private Dir opposite(Dir d) {
        return switch (d) {
            case UP -> Dir.DOWN;
            case DOWN -> Dir.UP;
            case LEFT -> Dir.RIGHT;
            case RIGHT -> Dir.LEFT;
            default -> Dir.NONE;
        };
    }

    // ============================== PINTADO ================================
    private void render() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawHud();

        gc.save();
        gc.translate(0, HUD_H);

        switch (state) {
            case MENU -> drawMenu();
            case LEVEL_INTRO -> {
                drawBoard(false);
                drawCenterText((String) LEVELS[levelIdx][0], "¡PREPÁRATE!", Color.YELLOW, Color.WHITE);
            }
            case PLAYING, PAUSED -> {
                drawBoard(true);
                if (state == State.PAUSED) {
                    drawOverlay("PAUSA", "Pulsa P o el botón para continuar");
                }
            }
            case GAME_OVER -> {
                drawBoard(false);
                drawOverlay("GAME OVER", "Puntuación final: " + score);
            }
            case WIN -> {
                drawBoard(false);
                drawOverlay("¡HAS GANADO!", "Puntuación final: " + score + " · ¡Enhorabuena!");
            }
        }

        if (flashClock > 0) {
            gc.setFill(Color.rgb(255, 255, 255, 0.35));
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight() - HUD_H);
        }

        gc.restore();
    }

    private void drawHud() {
        double w = canvas.getWidth();
        gc.setFill(Color.rgb(14, 14, 34));
        gc.fillRect(0, 0, w, HUD_H);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("PUNTOS: " + score, 16, 20);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText((String) LEVELS[levelIdx][0], w / 2.0, 20);

        // Vidas restantes
        gc.setFill(PAC_YELLOW);
        for (int i = 0; i < lives; i++) {
            gc.fillArc(w - 36 - i * 26, 10, 20, 20, 30, 300, ArcType.ROUND);
        }

        if (powerActive) {
            gc.setFill(Color.rgb(120, 200, 255));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("¡PODER!", 16, 42);
        }
    }

    private void drawBoard(boolean entities) {
        double w = canvas.getWidth(), h = canvas.getHeight() - HUD_H;
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        // Muros y elementos del laberinto
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = grid[r][c];
                double x = c * CELL, y = r * CELL;
                if (ch == '#') {
                    gc.setFill(WALL_BLUE);
                    gc.fillRoundRect(x + 2, y + 2, CELL - 4, CELL - 4, 8, 8);
                    gc.setStroke(WALL_INNER_BLUE);
                    gc.setLineWidth(1.4);
                    gc.strokeRoundRect(x + 4, y + 4, CELL - 8, CELL - 8, 6, 6);
                } else if (ch == '-') {
                    gc.setFill(GATE_PINK);
                    gc.fillRect(x + 2, y + CELL / 2.0 - 2, CELL - 4, 4);
                } else if (ch == '.') {
                    gc.setFill(DOT_COLOR);
                    gc.fillOval(x + CELL / 2.0 - 3, y + CELL / 2.0 - 3, 6, 6);
                } else if (ch == 'o') {
                    gc.setFill(DOT_COLOR);
                    double pulse = 2 * Math.sin(System.currentTimeMillis() / 120.0);
                    double s = 12 + pulse;
                    gc.fillOval(x + CELL / 2.0 - s / 2.0, y + CELL / 2.0 - s / 2.0, s, s);
                }
            }
        }

        // Fruta extra (+100)
        if (fruitVisible && fruitCell != null) {
            double fx = fruitCell.x * CELL + CELL / 2.0, fy = fruitCell.y * CELL + CELL / 2.0;
            int blink = ((int) (fruitLife / 250)) % 2 == 0 ? 1 : 0;
            gc.setFill(blink == 1 ? Color.rgb(255, 80, 80) : Color.rgb(255, 200, 60));
            gc.fillOval(fx - 8, fy - 8, 16, 16);
            gc.setFill(Color.rgb(60, 200, 90));
            gc.fillRect(fx - 1, fy - 13, 3, 6);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 10));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("+100", fx, fy + 20);
        }

        if (!entities) return;

        // Fantasmas
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) drawGhost(i);

        // Pac-Man
        if (deathAnimT > 0) drawPacDeath(); else drawPac();

        // Popups de puntos
        for (double[] p : popupFx) {
            double alpha = Math.max(0.0, Math.min(1.0, (70.0 - p[2]) / 70.0));
            gc.setFill(Color.color(1.0, 1.0, 1.0, alpha));
            gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("+" + (int) p[3], p[0], p[1] - p[2] / 3.0);
        }
    }

    private void drawPac() {
        double mouthAngle = Math.abs(Math.sin(mouthT * 0.25)) * 42.0;
        double base = switch (pacDir) {
            case RIGHT -> 0.0;
            case UP    -> 90.0;
            case LEFT  -> 180.0;
            case DOWN  -> 270.0;
            default    -> 0.0;
        };
        gc.setFill(PAC_YELLOW);
        double start = base + mouthAngle;
        double extent = 360.0 - 2 * mouthAngle;
        gc.fillArc(pacX - CELL / 2.0 + 3, pacY - CELL / 2.0 + 3, CELL - 6, CELL - 6, start, extent, ArcType.ROUND);
    }

    private void drawPacDeath() {
        double t = deathAnimT / 60.0; // 1.0 down to 0.0
        double openDeg = (1.0 - t) * 180.0;
        double extent = Math.max(0.0, 360.0 - 2 * openDeg);
        double start = 90.0 + openDeg;
        gc.setFill(PAC_YELLOW);
        gc.fillArc(pacX - CELL / 2.0 + 3, pacY - CELL / 2.0 + 3, CELL - 6, CELL - 6, start, extent, ArcType.ROUND);
    }

    private void drawGhost(int i) {
        double x = px[i], y = py[i];
        Color body;
        if (ghostMode[i] == 3) {
            body = null; // solo ojos
        } else if (powerActive && ghostMode[i] == 2) {
            body = (powerTime < 2.0 && (((int) (powerTime * 5)) % 2 == 0)) ? FRIGHT_FLASH : FRIGHT_BLUE;
        } else {
            body = GHOST_COLORS[i];
        }

        if (body != null) {
            gc.setFill(body);
            gc.fillArc(x - CELL / 2.0 + 3, y - CELL / 2.0 + 3, CELL - 6, CELL - 6, 0, 180, ArcType.ROUND);
            gc.fillRect(x - CELL / 2.0 + 3, y, CELL - 6, CELL / 2.0 - 3);
            // ondas inferiores
            double waveW = (CELL - 6) / 3.0;
            for (int w = 0; w < 3; w++) {
                gc.fillArc(x - CELL / 2.0 + 3 + w * waveW, y + CELL / 2.0 - 8, waveW, 8, 180, 180, ArcType.ROUND);
            }
        }

        // ojos
        if (ghostMode[i] != 0 || body != null) {
            gc.setFill(Color.WHITE);
            gc.fillOval(x - 8, y - 6, 7, 8);
            gc.fillOval(x + 1, y - 6, 7, 8);
            gc.setFill(Color.rgb(30, 40, 160));
            int ox = dcol(gdir[i]) * 2, oy = drow(gdir[i]) * 2;
            gc.fillOval(x - 6 + ox, y - 4 + oy, 3, 4);
            gc.fillOval(x + 3 + ox, y - 4 + oy, 3, 4);
        }
    }

    private void drawMenu() {
        double w = canvas.getWidth(), h = canvas.getHeight() - HUD_H;
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        gc.setFill(PAC_YELLOW);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 52));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("PAC-MAN", w / 2.0, 70);

        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 15));
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        String[] lines = {
                "CONTROLES:",
                "   Mover:  W A S D   o   Flechas de dirección",
                "   Pausa:  P  o  ESC",
                "",
                "PUNTUACIÓN:",
                "   Punto normal  +10      Bola de poder  +50",
                "   Fantasma      +250     Fruta extra    +100",
                "",
                "4 niveles · laberintos y velocidades distintas",
                "",
                "Pulsa ENTER, ESPACIO o el botón INICIAR"
        };
        double y = 140;
        for (String s : lines) {
            gc.fillText(s, w / 2.0 - 230, y);
            y += 24;
        }

        // Animación decorativa inferior
        double tt = System.currentTimeMillis() / 1000.0;
        gc.setFill(PAC_YELLOW);
        double pacDecorX = (w / 2.0 - 160 + (tt * 50 % 320)) - 12;
        gc.fillArc(pacDecorX, h - 90, 24, 24, 30, 300, ArcType.ROUND);

        for (int i = 0; i < 4; i++) {
            gc.setFill(GHOST_COLORS[i]);
            double gx = w / 2.0 - 180 + i * 60;
            gc.fillArc(gx, h - 60, 24, 24, 0, 180, ArcType.ROUND);
            gc.fillRect(gx, h - 48, 24, 12);
            gc.setFill(Color.WHITE);
            gc.fillOval(gx + 4, h - 55, 6, 7);
            gc.fillOval(gx + 14, h - 55, 6, 7);
            gc.setFill(Color.rgb(30, 40, 160));
            gc.fillOval(gx + 6, h - 53, 3, 4);
            gc.fillOval(gx + 16, h - 53, 3, 4);
        }
    }

    private void drawOverlay(String title, String sub) {
        double w = canvas.getWidth(), h = canvas.getHeight() - HUD_H;
        gc.setFill(Color.rgb(0, 0, 0, 0.67));
        gc.fillRect(0, 0, w, h);
        drawCenterText(title, sub, Color.YELLOW, Color.WHITE);
    }

    private void drawCenterText(String t1, String t2, Color c1, Color c2) {
        double w = canvas.getWidth(), h = canvas.getHeight() - HUD_H;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        gc.setFill(c1);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 40));
        gc.fillText(t1, w / 2.0, h / 2.0 - 15);

        gc.setFill(c2);
        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 16));
        gc.fillText(t2, w / 2.0, h / 2.0 + 25);
    }
}
