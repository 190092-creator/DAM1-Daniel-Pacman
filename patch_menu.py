"""
Patch PacMan.java to use custom sprites in the decorative menu animation.
Reads the file as bytes, finds the decorative block, and replaces it.
"""
import re

path = r"src\main\java\es\sanalberto\daniel\pacman\PacMan.java"

with open(path, "r", encoding="utf-8", errors="replace") as f:
    content = f.read()

# Pattern to find: the decorative block at the bottom of drawMenu()
OLD = '''        // Animaci\u00c3\u00b3n decorativa inferior
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
    }'''

NEW = '''        // Animaci\u00f3n decorativa inferior
        double tt = System.currentTimeMillis() / 1000.0;
        double pacDecorX = (w / 2.0 - 160 + (tt * 50 % 320)) - 12;
        if (imgPacOpen != null) {
            gc.drawImage(imgPacOpen, pacDecorX, h - 93, 26, 26);
        } else {
            gc.setFill(PAC_YELLOW);
            gc.fillArc(pacDecorX, h - 90, 24, 24, 30, 300, ArcType.ROUND);
        }

        for (int i = 0; i < 4; i++) {
            double gx = w / 2.0 - 180 + i * 60;
            if (imgGhosts[i] != null) {
                gc.drawImage(imgGhosts[i], gx, h - 63, 26, 26);
            } else {
                gc.setFill(GHOST_COLORS[i]);
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
    }'''

# Try normalized version (replace garbled unicode)
content_norm = content.replace('\u00c3\u00b3', '\u00f3')  # Animaci\u00f3n

if OLD in content:
    content = content.replace(OLD, NEW)
    print("Patched using OLD (garbled encoding)")
elif OLD.replace('\u00c3\u00b3', '\u00f3') in content:
    content = content.replace(OLD.replace('\u00c3\u00b3', '\u00f3'), NEW)
    print("Patched using normalized encoding")
else:
    # Try to find and replace using regex with the keyword
    idx = content.find("// Animaci")
    if idx != -1:
        end_idx = content.find("    }\n\n    private void drawOverlay", idx)
        if end_idx != -1:
            block = content[idx:end_idx]
            print("Found block at index", idx, "to", end_idx)
            print("Block preview:", block[:200])
            new_block = '''// Animaci\u00f3n decorativa inferior
        double tt = System.currentTimeMillis() / 1000.0;
        double pacDecorX = (w / 2.0 - 160 + (tt * 50 % 320)) - 12;
        if (imgPacOpen != null) {
            gc.drawImage(imgPacOpen, pacDecorX, h - 93, 26, 26);
        } else {
            gc.setFill(PAC_YELLOW);
            gc.fillArc(pacDecorX, h - 90, 24, 24, 30, 300, ArcType.ROUND);
        }

        for (int i = 0; i < 4; i++) {
            double gx = w / 2.0 - 180 + i * 60;
            if (imgGhosts[i] != null) {
                gc.drawImage(imgGhosts[i], gx, h - 63, 26, 26);
            } else {
                gc.setFill(GHOST_COLORS[i]);
                gc.fillArc(gx, h - 60, 24, 24, 0, 180, ArcType.ROUND);
                gc.fillRect(gx, h - 48, 24, 12);
                gc.setFill(Color.WHITE);
                gc.fillOval(gx + 4, h - 55, 6, 7);
                gc.fillOval(gx + 14, h - 55, 6, 7);
                gc.setFill(Color.rgb(30, 40, 160));
                gc.fillOval(gx + 6, h - 53, 3, 4);
                gc.fillOval(gx + 16, h - 53, 3, 4);
            }
        }'''
            content = content[:idx] + new_block + content[end_idx:]
            print("Patched using index-based approach")
        else:
            print("Could not find end of decorative block")
    else:
        print("Could not find decorative block at all")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("Done. File written.")
