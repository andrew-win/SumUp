import sys
from collections import Counter
try:
    from PIL import Image
    img = Image.open(sys.argv[1]).convert('RGB')
    
    # Get all colors
    colors = img.getdata()
    counter = Counter(colors)
    
    print("Most common colors:")
    for color, count in counter.most_common(10):
        print(f"#{color[0]:02x}{color[1]:02x}{color[2]:02x} : {count}")
except Exception as e:
    print(f"Error: {e}")
