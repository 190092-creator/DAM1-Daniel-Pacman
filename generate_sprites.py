import os
from PIL import Image, ImageDraw

def create_pacman_frames():
    frames = []
    # angles for mouth: 0 (closed), 28, 48
    angles = [0, 28, 48]
    for angle in angles:
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        # Main body with subtle dark outline
        draw.ellipse([2, 2, 29, 29], fill=(195, 140, 0, 255))
        # Inner body
        draw.ellipse([3, 3, 28, 28], fill=(255, 222, 50, 255))
        # Highlight on top-left
        draw.chord([5, 5, 23, 23], start=180, end=270, fill=(255, 248, 160, 255))
        
        # If mouth open, cut slice
        if angle > 0:
            draw.pieslice([1, 1, 30, 30], start=-angle, end=angle, fill=(0, 0, 0, 0))
        
        # Classic arcade eye with pupil & shine
        draw.ellipse([14, 6, 18, 11], fill=(30, 15, 0, 255))
        draw.point((15, 7), fill=(255, 255, 255, 255))
        frames.append(img)
    return frames

def create_ghost(main_color, dark_color, light_color, is_frightened=False):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Outer silhouette / shading outline
    draw.pieslice([3, 3, 28, 28], start=180, end=0, fill=dark_color)
    draw.rectangle([3, 15, 28, 26], fill=dark_color)
    
    # Main body
    draw.pieslice([4, 4, 27, 27], start=180, end=0, fill=main_color)
    draw.rectangle([4, 15, 27, 25], fill=main_color)
    
    # Top-left highlight dome
    draw.arc([6, 6, 25, 25], start=190, end=270, fill=light_color, width=2)
    
    # Bottom wavy skirt / tentacles (3 scalloped waves)
    # Wave 1 (left)
    draw.polygon([(4, 25), (11, 25), (7, 29)], fill=main_color)
    draw.line([(4, 25), (7, 29)], fill=dark_color, width=1)
    # Wave 2 (middle)
    draw.polygon([(12, 25), (20, 25), (16, 29)], fill=main_color)
    # Wave 3 (right)
    draw.polygon([(21, 25), (28, 25), (25, 29)], fill=main_color)
    draw.line([(28, 25), (25, 29)], fill=dark_color, width=1)
    
    if not is_frightened:
        # Expressive eyes
        # Left eye white
        draw.ellipse([7, 9, 15, 18], fill=(255, 255, 255, 255))
        # Left pupil (blue with shine)
        draw.ellipse([10, 12, 14, 17], fill=(25, 50, 190, 255))
        draw.point((11, 13), fill=(255, 255, 255, 255))
        
        # Right eye white
        draw.ellipse([17, 9, 25, 18], fill=(255, 255, 255, 255))
        # Right pupil
        draw.ellipse([20, 12, 24, 17], fill=(25, 50, 190, 255))
        draw.point((21, 13), fill=(255, 255, 255, 255))
    else:
        # Frightened face (small eyes + squiggly mouth)
        draw.rectangle([9, 12, 12, 15], fill=(255, 230, 120, 255))
        draw.rectangle([20, 12, 23, 15], fill=(255, 230, 120, 255))
        # Squiggly mouth
        draw.line([(7, 21), (11, 18), (16, 21), (21, 18), (25, 21)], fill=(255, 230, 120, 255), width=2)

    return img

out_dir = os.path.join("src", "main", "resources", "sprites")
os.makedirs(out_dir, exist_ok=True)

# Generate Pacman
p_frames = create_pacman_frames()
p_frames[0].save(os.path.join(out_dir, "pacman_closed.png"))
p_frames[1].save(os.path.join(out_dir, "pacman_half.png"))
p_frames[2].save(os.path.join(out_dir, "pacman_open.png"))

# Ghosts
ghost_defs = {
    "blinky": ((235, 45, 45, 255), (150, 15, 15, 255), (255, 130, 130, 255), False),
    "pinky":  ((255, 160, 200, 255), (190, 80, 130, 255), (255, 215, 235, 255), False),
    "inky":   ((40, 215, 235, 255), (15, 130, 160, 255), (150, 248, 255, 255), False),
    "clyde":  ((250, 165, 45, 255), (170, 90, 15, 255), (255, 215, 120, 255), False),
    "frightened": ((50, 80, 230, 255), (20, 35, 135, 255), (130, 165, 255, 255), True),
    "frightened_white": ((230, 230, 250, 255), (145, 145, 175, 255), (255, 255, 255, 255), True)
}

for name, (main_c, dark_c, light_c, fright) in ghost_defs.items():
    g = create_ghost(main_c, dark_c, light_c, is_frightened=fright)
    g.save(os.path.join(out_dir, f"ghost_{name}.png"))

print("Generated detailed models in", out_dir)
