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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * PAC-MAN — JavaFX + JDK 21
 * Totalmente adaptado para JavaFX con selector de niveles, balance dinámico de velocidad,
 * hitbox estricto en paredes y pathfinding BFS directo para el regreso de los fantasmas al respawn.
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

    /** Datos por nivel: {nombre, velocidadFantasmaBase(px/s), segundosModoPoder}. */
    private static final Object[][] LEVELS = {
            {"NIVEL 1 · Tranquilo",  62.0, 8.0},
            {"NIVEL 2 · Animado",    82.0, 7.0},
            {"NIVEL 3 · Rápido",    102.0, 6.0},
            {"NIVEL 4 · Frenético", 122.0, 5.0},
    };

    /** Proporción de velocidad del fantasma asustado respecto a Pac-Man por nivel (siempre por debajo de pacman). */
    private static final double[] FRIGHTENED_RATIOS = {0.76, 0.83, 0.89, 0.94};

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
                    "#.#.#.#.#.#.#.#---#.#.#.#.#",
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
                    "#.#.#.####.......####.#.#.#",
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
    private boolean isTestingSingleLevel = false;
    private double powerTime, modeClock, fruitClock, fruitLife, introClock, flashClock;
    private double spawnGrace = 0.0;
    private int modeIndex, ghostsEatenChain, deathAnimT;
    private Point fruitCell, pacSpawn, ghostSpawnPoint;

    private final double[] px = new double[GHOSTS_PER_LEVEL];
    private final double[] py = new double[GHOSTS_PER_LEVEL];
    private final Dir[] gdir = new Dir[GHOSTS_PER_LEVEL];
    private final int[] ghostMode = new int[GHOSTS_PER_LEVEL]; // 0 respawn/casa 1 saliendo 2 fuera 3 ojos volviendo
    private final boolean[] ghostFrightened = new boolean[GHOSTS_PER_LEVEL];
    private final boolean[] ghostImmuneToPower = new boolean[GHOSTS_PER_LEVEL];
    private final double[] ghostHouseTime = new double[GHOSTS_PER_LEVEL];
    private final int[] scatterTX = new int[GHOSTS_PER_LEVEL];
    private final int[] scatterTY = new int[GHOSTS_PER_LEVEL];

    private int[][] distToGhostSpawn;

    private double pacX, pacY;
    private double mouthTimer = 0.0;
    private Dir pacDir = Dir.NONE, pacQueued = Dir.NONE;
    private Dir pacLastDir = Dir.RIGHT;

    private final List<double[]> popupFx = new ArrayList<>(); // {x, y, age, score}
    private Button btnStart, btnPause, btnMenu, btnTestLevel;
    private ComboBox<String> comboLevelTest;

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

        // Botones y controles
        btnStart = makeButton("▶  INICIAR", this::startGame, 115);
        btnPause = makeButton("⏸ PAUSA", this::togglePause, 105);
        btnMenu  = makeButton("MENÚ", () -> {
            state = State.MENU;
            syncButtons();
        }, 90);

        comboLevelTest = new ComboBox<>();
        comboLevelTest.getItems().addAll("Nivel 1", "Nivel 2", "Nivel 3", "Nivel 4");
        comboLevelTest.setValue("Nivel 1");
        comboLevelTest.setFocusTraversable(false);
        comboLevelTest.setPrefSize(105, 34);
        comboLevelTest.setStyle(
                "-fx-background-color: #22223a; " +
                "-fx-mark-color: #ffde32; " +
                "-fx-font-family: 'Monospaced', monospace; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
                "-fx-background-radius: 16px; " +
                "-fx-cursor: hand;"
        );
        comboLevelTest.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setTextFill(Color.web("#ffde32"));
                setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
                setStyle("-fx-background-color: transparent;");
            }
        });

        btnTestLevel = makeButton("🧪 PROBAR", () -> {
            int selected = comboLevelTest.getSelectionModel().getSelectedIndex();
            if (selected < 0) selected = 0;
            startLevelTest(selected);
        }, 115);

        btnPause.setDisable(true);
        btnMenu.setDisable(true);

        HBox bar = new HBox(10, btnStart, comboLevelTest, btnTestLevel, btnPause, btnMenu);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 10, 0));
        bar.setStyle("-fx-background-color: #0e0e22;");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #080816;");
        root.setCenter(canvas);
        root.setBottom(bar);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> handleKey(e.getCode()));

        // Clics interactivos en el menú para testear niveles
        canvas.setOnMouseClicked(e -> {
            if (state == State.MENU) {
                double x = e.getX();
                double y = e.getY() - HUD_H;
                double w = canvas.getWidth();
                double badgeY = 410;
                double badgeH = 46;
                if (y >= badgeY && y <= badgeY + badgeH) {
                    for (int lvl = 0; lvl < 4; lvl++) {
                        double bx = w / 2.0 - 245 + lvl * 125;
                        if (x >= bx && x <= bx + 115) {
                            startLevelTest(lvl);
                            break;
                        }
                    }
                }
            }
        });

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

    private Button makeButton(String text, Runnable action, double prefWidth) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setPrefSize(prefWidth, 34);

        String baseStyle = "-fx-text-fill: black; " +
                "-fx-font-family: 'Monospaced', monospace; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
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
        if (state == State.MENU) {
            switch (code) {
                case DIGIT1, NUMPAD1 -> { startLevelTest(0); return; }
                case DIGIT2, NUMPAD2 -> { startLevelTest(1); return; }
                case DIGIT3, NUMPAD3 -> { startLevelTest(2); return; }
                case DIGIT4, NUMPAD4 -> { startLevelTest(3); return; }
                default -> {}
            }
        }

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
        boolean inGame = (state == State.PLAYING || state == State.PAUSED);
        btnStart.setText(inGame ? "↻ REINICIAR" : "▶  INICIAR");
        btnPause.setDisable(!inGame);
        btnPause.setText(state == State.PAUSED ? "▶ SEGUIR" : "⏸ PAUSA");
        btnMenu.setDisable(state == State.MENU);
        comboLevelTest.setDisable(state != State.MENU);
        btnTestLevel.setDisable(state != State.MENU);
    }

    // ============================== NIVELES ================================
    private void startGame() {
        score = 0;
        lives = START_LIVES;
        levelIdx = 0;
        isTestingSingleLevel = false;
        spawnGrace = 0;
        loadLevel(0);
        state = State.LEVEL_INTRO;
        introClock = 2.0;
        syncButtons();
        canvas.requestFocus();
    }

    private void startLevelTest(int idx) {
        if (idx < 0 || idx >= MAZES.length) idx = 0;
        score = 0;
        lives = START_LIVES;
        levelIdx = idx;
        isTestingSingleLevel = true;
        spawnGrace = 0;
        loadLevel(idx);
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
                    default -> {}
                }
                grid[r][c] = ch;
            }
        }
        if (pacSpawn == null) pacSpawn = new Point(1, 1);

        // Centro / Punto de respawn para los fantasmas
        Point door = findDoorCenter();
        if (!gSpawn.isEmpty()) {
            int sumX = 0, sumY = 0;
            for (Point p : gSpawn) { sumX += p.x; sumY += p.y; }
            ghostSpawnPoint = new Point(sumX / gSpawn.size(), sumY / gSpawn.size());
        } else if (door != null) {
            ghostSpawnPoint = new Point(door.x, door.y + 2);
        } else {
            ghostSpawnPoint = new Point(cols / 2, rows / 2);
        }

        // Rellenar pasillos con bolas normales '.' excepto en el cuadrado central de spawn,
        // pero asegurando que alrededor del spawn y por las paredes contiguas haya bolas
        boolean[][] visited = new boolean[rows][cols];
        Queue<Point> q = new ArrayDeque<>();
        q.add(pacSpawn);
        visited[pacSpawn.y][pacSpawn.x] = true;

        while (!q.isEmpty()) {
            Point p = q.poll();
            for (Dir d : new Dir[]{Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT}) {
                int nr = p.y + drow(d);
                int nc = p.x + dcol(d);
                if (isTunnelRow(p.y)) {
                    if (nc < 0) nc = cols - 1;
                    else if (nc >= cols) nc = 0;
                }
                if (inside(nr, nc) && !visited[nr][nc]) {
                    char cChar = grid[nr][nc];
                    // Pac-Man no entra al spawn de fantasmas (puerta '-' y muros '#')
                    if (cChar != '#' && cChar != '-') {
                        visited[nr][nc] = true;
                        q.add(new Point(nc, nr));
                        // Si está vacío y no es spawn de Pac-Man ni fruta, rellenar con '.'
                        if (cChar == ' ' && !(nc == pacSpawn.x && nr == pacSpawn.y)
                                && (fruitCell == null || !(nc == fruitCell.x && nr == fruitCell.y))) {
                            grid[nr][nc] = '.';
                        }
                    }
                }
            }
        }

        // Recuento total de bolas
        dotsLeft = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = grid[r][c];
                if (ch == '.' || ch == 'o') dotsLeft++;
            }
        }

        // Precalcular distancias BFS para que los ojos vuelvan DIRECTOS al spawn
        computeGhostSpawnBFS();

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
            scatterTX[i] = corners[i][0];
            scatterTY[i] = corners[i][1];
        }

        modeClock = 0;
        modeIndex = 0;
        fruitClock = 0;
        fruitVisible = false;
        bonusChainActive = false;
        resetPositions();
    }

    private void computeGhostSpawnBFS() {
        distToGhostSpawn = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(distToGhostSpawn[r], Integer.MAX_VALUE);
        }

        Queue<Point> queue = new ArrayDeque<>();
        distToGhostSpawn[ghostSpawnPoint.y][ghostSpawnPoint.x] = 0;
        queue.add(ghostSpawnPoint);

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            int currDist = distToGhostSpawn[p.y][p.x];

            for (Dir d : new Dir[]{Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT}) {
                int nr = p.y + drow(d);
                int nc = p.x + dcol(d);

                if (isTunnelRow(p.y)) {
                    if (nc < 0) nc = cols - 1;
                    else if (nc >= cols) nc = 0;
                }

                if (inside(nr, nc)) {
                    // Los ojos pueden atravesar puertas ('-') pero nunca muros ('#')
                    if (isWalk(nr, nc, true) && distToGhostSpawn[nr][nc] > currDist + 1) {
                        distToGhostSpawn[nr][nc] = currDist + 1;
                        queue.add(new Point(nc, nr));
                    }
                }
            }
        }
    }

    private void resetPositions() {
        pacX = pacSpawn.x * CELL + CELL / 2.0;
        pacY = pacSpawn.y * CELL + CELL / 2.0;
        pacDir = Dir.NONE;
        pacQueued = Dir.NONE;
        pacLastDir = Dir.RIGHT;
        deathAnimT = 0;
        spawnGrace = 0.0;
        mouthTimer = 0.0; // Inicia la animación de comer inmediatamente

        // Todos los fantasmas salen exactamente en el centro / punto de respawn al empezar nivel
        for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
            px[i] = ghostSpawnPoint.x * CELL + CELL / 2.0;
            py[i] = ghostSpawnPoint.y * CELL + CELL / 2.0;
            ghostMode[i] = 0; // En respawn
            ghostHouseTime[i] = 0;
            ghostFrightened[i] = false;
            ghostImmuneToPower[i] = false;
            gdir[i] = Dir.UP;
        }
        powerActive = false;
        powerTime = 0;
    }

    // ============================== BUCLE ==================================
    private void tick(double dt) {
        mouthTimer += dt; // Animación de la boca comiendo activa continuamente
        if (spawnGrace > 0) spawnGrace -= dt;

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
            if (powerTime <= 0) {
                powerActive = false;
                for (int i = 0; i < GHOSTS_PER_LEVEL; i++) {
                    ghostFrightened[i] = false;
                    ghostImmuneToPower[i] = false;
                }
            }
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
        double speed = PAC_BASE + levelIdx * 8;

        // 1. Intentar girar o cambiar de sentido hacia pacQueued
        if (pacQueued != Dir.NONE && pacQueued != pacDir) {
            if (pacQueued == opposite(pacDir)) {
                // Giro de 180°: inmediato sin necesidad de llegar a la intersección
                pacDir = pacQueued;
                pacLastDir = pacDir;
            } else {
                // Giro perpendicular: solo si la celda objetivo es transitable y está alineado al pasillo
                int curC = (int) Math.round((pacX - CELL / 2.0) / CELL);
                int curR = (int) Math.round((pacY - CELL / 2.0) / CELL);
                double targetX = curC * CELL + CELL / 2.0;
                double targetY = curR * CELL + CELL / 2.0;

                int nextR = curR + drow(pacQueued);
                int nextC = curC + dcol(pacQueued);

                boolean pathOpen = isWalk(nextR, nextC, false);
                boolean aligned = (dcol(pacQueued) != 0)
                        ? Math.abs(pacY - targetY) < 6.0
                        : Math.abs(pacX - targetX) < 6.0;

                if (pathOpen && aligned) {
                    pacDir = pacQueued;
                    pacLastDir = pacDir;
                    // Centrar firmemente en el eje perpendicular para no chocar ni salirse
                    if (dcol(pacDir) != 0) pacY = targetY;
                    else pacX = targetX;
                }
            }
        }

        if (pacDir == Dir.NONE) return;

        double remaining = speed * dt;
        while (remaining > 0.0001 && pacDir != Dir.NONE) {
            int curC = (int) Math.round((pacX - CELL / 2.0) / CELL);
            int curR = (int) Math.round((pacY - CELL / 2.0) / CELL);
            double targetX = curC * CELL + CELL / 2.0;
            double targetY = curR * CELL + CELL / 2.0;

            int nextR = curR + drow(pacDir);
            int nextC = curC + dcol(pacDir);
            boolean wallAhead = !isWalk(nextR, nextC, false);

            if (wallAhead) {
                // Hay muro adelante: no se puede pasar del centro de la celda actual (hitbox estricto)
                double distToStop = (dcol(pacDir) != 0)
                        ? (targetX - pacX) * dcol(pacDir)
                        : (targetY - pacY) * drow(pacDir);

                if (distToStop <= 0.001) {
                    pacX = targetX;
                    pacY = targetY;
                    pacDir = Dir.NONE;
                    break;
                }
                double step = Math.min(remaining, distToStop);
                pacX += dcol(pacDir) * step;
                pacY += drow(pacDir) * step;
                remaining -= step;
                if (step >= distToStop) {
                    pacX = targetX;
                    pacY = targetY;
                    pacDir = Dir.NONE;
                    break;
                }
            } else {
                // Camino libre: avanzar hacia el centro de la siguiente celda
                double distToNext = (dcol(pacDir) != 0)
                        ? ((targetX + dcol(pacDir) * CELL) - pacX) * dcol(pacDir)
                        : ((targetY + drow(pacDir) * CELL) - pacY) * drow(pacDir);

                double step = Math.min(remaining, Math.max(0.1, distToNext));
                pacX += dcol(pacDir) * step;
                pacY += drow(pacDir) * step;
                remaining -= step;
            }

            // Efecto túnel seguro
            int currentRow = (int) Math.floor(pacY / CELL);
            if (isTunnelRow(currentRow)) {
                if (pacX < -CELL / 2.0) pacX = cols * CELL + CELL / 2.0 - 1;
                else if (pacX > cols * CELL + CELL / 2.0) pacX = -CELL / 2.0 + 1;
            } else {
                // Pared exterior impenetrable: Pac-Man nunca se puede salir del mapa
                pacX = Math.max(CELL / 2.0, Math.min(cols * CELL - CELL / 2.0, pacX));
                pacY = Math.max(CELL / 2.0, Math.min(rows * CELL - CELL / 2.0, py[0] != 0 ? pacY : pacY));
            }
        }
    }

    private boolean isWalk(int r, int c, boolean ghostThroughDoor) {
        if (r < 0 || r >= rows) return false;
        if (c < 0 || c >= cols) {
            return isTunnelRow(r);
        }
        char ch = grid[r][c];
        if (ch == '#') return false; // Muro: NUNCA atravesable por nada ni nadie
        return ch != '-' || ghostThroughDoor;
    }

    private boolean isTunnelRow(int r) {
        return r >= 0 && r < rows && grid[r][0] != '#' && grid[r][cols - 1] != '#';
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
        double pacSpeed = PAC_BASE + levelIdx * 8;

        // Velocidad:
        // - Si Pac-Man está en modo normal: fantasmas 1 px/s más rápidos que Pac-Man
        // - Si Pac-Man está bajo la bola de poder: fantasmas más lentos según la tabla de proporciones programada
        // - Si ha respawneado ignorando el modo poder: velocidad normal (1 px/s más rápido que Pac-Man)
        double speed;
        if (ghostMode[i] == 3) {
            speed = Math.max(pacSpeed * 1.8, 220.0); // Ojos: vuelven directo y rápido al respawn
        } else if (ghostMode[i] == 2) {
            if (ghostFrightened[i] && !ghostImmuneToPower[i]) {
                double ratio = (levelIdx >= 0 && levelIdx < FRIGHTENED_RATIOS.length)
                        ? FRIGHTENED_RATIOS[levelIdx]
                        : 0.94;
                speed = pacSpeed * ratio; // Bajo efectos de bola de poder: más lentos que Pac-Man
            } else {
                speed = pacSpeed + 1.0; // En modo normal o inmune tras respawn: 1px/s más rápido que Pac-Man
            }
        } else {
            speed = (pacSpeed + 1.0) * 0.8;
        }

        if (inTunnelRow(i)) speed *= 0.6;

        // Modo 0: en el centro/respawn esperando su turno de salida escalonada
        if (ghostMode[i] == 0) {
            ghostHouseTime[i] += dt;
            py[i] += (gdir[i] == Dir.UP ? -1 : 1) * (pacSpeed * 0.5) * dt;
            double cy = ghostSpawnPoint.y * CELL + CELL / 2.0;
            if (gdir[i] == Dir.UP && py[i] <= cy - 3) gdir[i] = Dir.DOWN;
            if (gdir[i] == Dir.DOWN && py[i] >= cy + 3) gdir[i] = Dir.UP;

            if (ghostHouseTime[i] > i * 1.8) {
                ghostMode[i] = 1; // Salir de la casa hacia el laberinto
                gdir[i] = Dir.UP;
            }
            return;
        }

        // Modo 1: salir de la casa a través de la puerta hacia el laberinto
        if (ghostMode[i] == 1) {
            Point door = findDoorCenter();
            if (door != null) {
                double doorX = door.x * CELL + CELL / 2.0;
                double doorY = door.y * CELL + CELL / 2.0;
                double exitY = (door.y - 1) * CELL + CELL / 2.0;

                // Primero alinearse horizontalmente con el centro de la puerta
                if (Math.abs(px[i] - doorX) > 0.8) {
                    double step = Math.min(speed * dt, Math.abs(px[i] - doorX));
                    px[i] += (px[i] < doorX ? 1 : -1) * step;
                    gdir[i] = (px[i] < doorX) ? Dir.RIGHT : Dir.LEFT;
                    return;
                } else {
                    px[i] = doorX;
                    gdir[i] = Dir.UP;
                    py[i] -= speed * dt;
                    if (py[i] <= exitY) {
                        py[i] = exitY;
                        ghostMode[i] = 2; // Ya cruzó la puerta, entra en juego para atacar
                        chooseGhostDir(i, door.y - 1, door.x, false);
                    }
                    return;
                }
            } else {
                ghostMode[i] = 2;
            }
        }

        // Modo 3: Ojos regresando al respawn
        if (ghostMode[i] == 3) {
            double distToRespawn = Math.hypot(px[i] - (ghostSpawnPoint.x * CELL + CELL / 2.0),
                    py[i] - (ghostSpawnPoint.y * CELL + CELL / 2.0));
            if (distToRespawn < CELL * 0.75) {
                // Llegaron al centro/punto de respawn:
                // Vuelven a salir como fantasmas normales y podrán volver a atacar a Pac-Man
                px[i] = ghostSpawnPoint.x * CELL + CELL / 2.0;
                py[i] = ghostSpawnPoint.y * CELL + CELL / 2.0;
                ghostMode[i] = 1;           // Sale inmediatamente hacia la puerta
                ghostFrightened[i] = false;  // Normal, no vulnerable
                gdir[i] = Dir.UP;           // Sube hacia la salida

                // Al salir de nuevo tras haber respawneado, ignora que Pac-Man esté en modo poder
                // únicamente hasta que salga de ese modo poder
                if (powerActive) {
                    ghostImmuneToPower[i] = true;
                }
                return;
            }
        }

        // Modos 2 y 3: Hitbox estricto contra todas las paredes ('#'), solo permitiendo puerta ('-') si es modo 3
        boolean throughDoor = (ghostMode[i] == 3);
        double remaining = speed * dt;
        int maxSteps = 10;

        while (remaining > 0.0001 && maxSteps-- > 0 && gdir[i] != Dir.NONE) {
            int curC = (int) Math.round((px[i] - CELL / 2.0) / CELL);
            int curR = (int) Math.round((py[i] - CELL / 2.0) / CELL);
            if (isTunnelRow(curR)) {
                if (curC < 0) curC = cols - 1;
                else if (curC >= cols) curC = 0;
            } else {
                curC = Math.max(0, Math.min(cols - 1, curC));
                curR = Math.max(0, Math.min(rows - 1, curR));
            }

            double targetX = curC * CELL + CELL / 2.0;
            double targetY = curR * CELL + CELL / 2.0;

            // Centrado perpendicular estricto en el pasillo: nunca roza ni entra en paredes laterales
            if (dcol(gdir[i]) != 0) {
                py[i] = targetY;
            } else if (drow(gdir[i]) != 0) {
                px[i] = targetX;
            }

            int nextR = curR + drow(gdir[i]);
            int nextC = curC + dcol(gdir[i]);
            if (isTunnelRow(curR)) {
                if (nextC < 0) nextC = cols - 1;
                else if (nextC >= cols) nextC = 0;
            }

            boolean wallAhead = !isWalk(nextR, nextC, throughDoor);

            if (wallAhead) {
                // Hay muro adelante: el fantasma no puede pasar del centro de la celda actual (hitbox estricto)
                double distToStop = (dcol(gdir[i]) != 0)
                        ? (targetX - px[i]) * dcol(gdir[i])
                        : (targetY - py[i]) * drow(gdir[i]);

                if (distToStop <= 0.001) {
                    px[i] = targetX;
                    py[i] = targetY;
                    chooseGhostDir(i, curR, curC, throughDoor);
                    int testR = curR + drow(gdir[i]);
                    int testC = curC + dcol(gdir[i]);
                    if (isTunnelRow(curR)) {
                        if (testC < 0) testC = cols - 1;
                        else if (testC >= cols) testC = 0;
                    }
                    if (!isWalk(testR, testC, throughDoor)) {
                        break; // Bloqueado, esperar en el centro exacto sin atravesar la pared
                    }
                    continue;
                }

                double step = Math.min(remaining, distToStop);
                px[i] += dcol(gdir[i]) * step;
                py[i] += drow(gdir[i]) * step;
                remaining -= step;

                if (step >= distToStop) {
                    px[i] = targetX;
                    py[i] = targetY;
                    chooseGhostDir(i, curR, curC, throughDoor);
                }
            } else {
                // Camino libre hacia el centro de la siguiente celda
                double nextCenterX = targetX + dcol(gdir[i]) * CELL;
                double nextCenterY = targetY + drow(gdir[i]) * CELL;

                double distToNext = (dcol(gdir[i]) != 0)
                        ? (nextCenterX - px[i]) * dcol(gdir[i])
                        : (nextCenterY - py[i]) * drow(gdir[i]);

                if (distToNext <= 0.001) {
                    int reachedC = curC + dcol(gdir[i]);
                    int reachedR = curR + drow(gdir[i]);
                    if (isTunnelRow(reachedR)) {
                        if (reachedC < 0) reachedC = cols - 1;
                        else if (reachedC >= cols) reachedC = 0;
                    }
                    px[i] = reachedC * CELL + CELL / 2.0;
                    py[i] = reachedR * CELL + CELL / 2.0;
                    chooseGhostDir(i, reachedR, reachedC, throughDoor);
                    continue;
                }

                double step = Math.min(remaining, distToNext);
                px[i] += dcol(gdir[i]) * step;
                py[i] += drow(gdir[i]) * step;
                remaining -= step;

                if (step >= distToNext) {
                    int reachedC = curC + dcol(gdir[i]);
                    int reachedR = curR + drow(gdir[i]);
                    if (isTunnelRow(reachedR)) {
                        if (reachedC < 0) reachedC = cols - 1;
                        else if (reachedC >= cols) reachedC = 0;
                    }
                    px[i] = reachedC * CELL + CELL / 2.0;
                    py[i] = reachedR * CELL + CELL / 2.0;
                    chooseGhostDir(i, reachedR, reachedC, throughDoor);
                }
            }

            // Efecto túnel seguro y límites exteriores impenetrables
            int currentRow = (int) Math.floor(py[i] / CELL);
            if (isTunnelRow(currentRow)) {
                if (px[i] < -CELL / 2.0) px[i] = cols * CELL + CELL / 2.0 - 1;
                else if (px[i] > cols * CELL + CELL / 2.0) px[i] = -CELL / 2.0 + 1;
            } else {
                px[i] = Math.max(CELL / 2.0, Math.min(cols * CELL - CELL / 2.0, px[i]));
                py[i] = Math.max(CELL / 2.0, Math.min(rows * CELL - CELL / 2.0, py[i]));
            }
        }
    }

    private boolean inTunnelRow(int i) {
        int r = (int) (py[i] / CELL);
        return isTunnelRow(r);
    }

    private Point findDoorCenter() {
        int count = 0;
        int sumC = 0;
        int doorR = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == '-') {
                    sumC += c;
                    doorR = r;
                    count++;
                }
            }
        }
        if (count > 0) {
            return new Point(sumC / count, doorR);
        }
        return null;
    }

    private void chooseGhostDir(int i, int r, int c, boolean throughDoor) {
        List<Dir> opts = new ArrayList<>();
        for (Dir d : Dir.values()) {
            if (d == Dir.NONE || d == opposite(gdir[i])) continue;
            int nr = r + drow(d), nc = c + dcol(d);
            if (!inside(nr, nc)) {
                if (isTunnelRow(r) && (d == Dir.LEFT || d == Dir.RIGHT)) opts.add(d);
                continue;
            }
            if (isWalk(nr, nc, throughDoor)) opts.add(d);
        }
        if (opts.isEmpty()) {
            Dir opp = opposite(gdir[i]);
            int nr = r + drow(opp), nc = c + dcol(opp);
            if (isWalk(nr, nc, throughDoor) || (isTunnelRow(r) && (opp == Dir.LEFT || opp == Dir.RIGHT))) {
                gdir[i] = opp;
            } else {
                for (Dir d : Dir.values()) {
                    if (d == Dir.NONE) continue;
                    int dnr = r + drow(d), dnc = c + dcol(d);
                    if (isWalk(dnr, dnc, throughDoor)) {
                        gdir[i] = d;
                        break;
                    }
                }
            }
            return;
        }

        Dir best;
        if (ghostMode[i] == 3) {
            // Ojos: van DIRECTOS al centro/punto de respawn siguiendo el camino BFS más corto
            best = opts.get(0);
            int minD = Integer.MAX_VALUE;
            for (Dir d : opts) {
                int nr = r + drow(d);
                int nc = c + dcol(d);
                if (isTunnelRow(r)) {
                    if (nc < 0) nc = cols - 1;
                    else if (nc >= cols) nc = 0;
                }
                if (inside(nr, nc)) {
                    int dVal = distToGhostSpawn[nr][nc];
                    if (dVal < minD) {
                        minD = dVal;
                        best = d;
                    }
                }
            }
            gdir[i] = best;
            return;
        } else if (ghostFrightened[i] && !ghostImmuneToPower[i] && ghostMode[i] == 2) {
            // Huir de Pac-Man (solo si está asustado y no es inmune)
            best = opts.get(new Random().nextInt(opts.size()));
            double bd = -1;
            for (Dir d : opts) {
                int nr = r + drow(d), nc = c + dcol(d);
                double dist = Math.hypot(nc * CELL - pacX, nr * CELL - pacY);
                if (dist > bd) { bd = dist; best = d; }
            }
        } else {
            // Modo normal scatter / chase
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
                    // Si Pac-Man se toma otra bola de poder, vuelven a ser comestibles
                    ghostImmuneToPower[i] = false;
                    if (ghostMode[i] == 2) {
                        ghostFrightened[i] = true;
                        gdir[i] = opposite(gdir[i]);
                    } else if (ghostMode[i] == 0 || ghostMode[i] == 1) {
                        ghostFrightened[i] = true;
                    }
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
            if (Math.hypot(px[i] - pacX, py[i] - pacY) < CELL * 0.72) {
                if (ghostFrightened[i] && !ghostImmuneToPower[i]) {
                    // Pac-Man come al fantasma (solo si está asustado y no es inmune)
                    ghostsEatenChain++;
                    int pts = GHOST_SCORE * ghostsEatenChain; // 250, 500, 1000...
                    addScore(pts, (int) (px[i] / CELL), (int) (py[i] / CELL));
                    ghostMode[i] = 3;           // Pasa a ser ojos
                    ghostFrightened[i] = false;  // Ya no es comestible mientras va como ojos
                } else if (spawnGrace <= 0) {
                    // Fantasma normal (o que ya revivió en el respawn ignorando el modo poder): mata a Pac-Man
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
        if (lives <= 0) {
            deathAnimT = 60;
            state = State.GAME_OVER;
            syncButtons();
        } else {
            // Solo Pac-Man reaparece en su spawn; los fantasmas NO respawnean y el juego NO se para
            pacX = pacSpawn.x * CELL + CELL / 2.0;
            pacY = pacSpawn.y * CELL + CELL / 2.0;
            pacDir = Dir.NONE;
            pacQueued = Dir.NONE;
            pacLastDir = Dir.RIGHT;
            spawnGrace = 1.5; // Breve protección de spawn para no morir al instante
            state = State.PLAYING; // El juego sigue corriendo sin detenerse
            // Los fantasmas se quedan exactamente en su última posición (px, py, gdir, ghostMode intactos)
        }
    }

    private void nextLevel() {
        if (isTestingSingleLevel) {
            // En modo test de un nivel individual, al completarlo se muestra victoria de esa prueba
            state = State.WIN;
            syncButtons();
            return;
        }
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
                String msg = isTestingSingleLevel ? "¡Nivel superado con éxito!" : "¡Enhorabuena!";
                drawOverlay("¡HAS GANADO!", "Puntuación final: " + score + " · " + msg);
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
        String lvlTitle = (String) LEVELS[levelIdx][0];
        if (isTestingSingleLevel) lvlTitle += " [TEST]";
        gc.fillText(lvlTitle, w / 2.0, 20);

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

        // Pac-Man: al respawnear sigue teniendo la animación de comer
        if (state == State.GAME_OVER && deathAnimT > 0) drawPacDeath();
        else drawPac();

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
        if (spawnGrace > 0 && ((int) (spawnGrace * 8)) % 2 == 0) {
            gc.setGlobalAlpha(0.35); // Efecto sutil de invulnerabilidad tras reaparecer
        } else {
            gc.setGlobalAlpha(1.0);
        }

        double mouthAngle = Math.abs(Math.sin(mouthTimer * 12.0)) * 42.0;
        Dir facing = (pacDir != Dir.NONE) ? pacDir : pacLastDir;
        double base = switch (facing) {
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
        gc.setGlobalAlpha(1.0);
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
        } else if (ghostFrightened[i] && !ghostImmuneToPower[i] && ghostMode[i] == 2) {
            body = (powerTime < 2.0 && (((int) (powerTime * 5)) % 2 == 0)) ? FRIGHT_FLASH : FRIGHT_BLUE;
        } else {
            body = GHOST_COLORS[i]; // color normal (incluye fantasmas inmunes que salieron del respawn)
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

        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 14));
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
                "VELOCIDAD:",
                "   Modo normal: fantasmas 1 px/s más rápidos que Pac-Man",
                "   Modo poder:  fantasmas más lentos que Pac-Man (según nivel)",
                "",
                "Pulsa ENTER, ESPACIO o el botón INICIAR para jugar todo"
        };
        double y = 135;
        for (String s : lines) {
            gc.fillText(s, w / 2.0 - 245, y);
            y += 21;
        }

        // Sección interactiva de Test de Niveles
        gc.setFill(Color.rgb(120, 200, 255));
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 15));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("🧪 TESTEAR NIVEL INDEPENDIENTE (Clic o Teclas 1-4):", w / 2.0, 390);

        for (int lvl = 0; lvl < 4; lvl++) {
            double bx = w / 2.0 - 245 + lvl * 125;
            double by = 412;
            gc.setFill(Color.rgb(24, 24, 52));
            gc.fillRoundRect(bx, by, 115, 46, 14, 14);
            gc.setStroke(Color.rgb(80, 140, 255));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(bx, by, 115, 46, 14, 14);

            gc.setFill(PAC_YELLOW);
            gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
            gc.fillText("NIVEL " + (lvl + 1), bx + 57, by + 18);
            gc.setFill(Color.rgb(180, 210, 255));
            gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
            gc.fillText("Tecla [" + (lvl + 1) + "]", bx + 57, by + 34);
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
