# Draws the Lattice logo (32x32 pixel art, scaled up). Usage: logo.ps1 -Out icon.png [-Size 512]
param([string]$Out = "icon.png", [int]$Size = 512)
Add-Type -AssemblyName System.Drawing
$S = $Size / 32
$bmp = New-Object Drawing.Bitmap $Size, $Size
$gfx = [Drawing.Graphics]::FromImage($bmp)

function Col($hex, $a = 255) { [Drawing.Color]::FromArgb($a, [Drawing.ColorTranslator]::FromHtml($hex)) }
function Box($x, $y, $w, $h, $col) {
    $b = New-Object Drawing.SolidBrush $col
    $gfx.FillRectangle($b, [float]($x * $S), [float]($y * $S), [float]($w * $S), [float]($h * $S)); $b.Dispose()
}

# background + soft fluix glow behind the output card
Box 0 0 32 32 (Col '#15131f')
$gp = New-Object Drawing.Drawing2D.GraphicsPath
$gp.AddEllipse([float](-0.25 * $Size), [float](-0.25 * $Size), [float](1.5 * $Size), [float](1.5 * $Size))
$pg = New-Object Drawing.Drawing2D.PathGradientBrush $gp
$pg.CenterPoint = New-Object Drawing.PointF ([float](0.6 * $Size)), ([float](0.5 * $Size))
$pg.CenterColor = Col '#3d2b6b'; $pg.SurroundColors = @((Col '#15131f'))
$gfx.FillRectangle($pg, 0, 0, $Size, $Size)
for ($y = 2; $y -le 29; $y += 3) { for ($x = 2; $x -le 29; $x += 3) { Box $x $y 1 1 (Col '#ffffff' 12) } }

# lines: 2px cyan with a 1px glow, drawn as rectangles (x0,y0,x1,y1 inclusive)
$flat = @(10,5,15,6,  14,5,15,13,  14,12,19,13,      # top input
          10,15,19,16,                               # middle input
          10,25,15,26, 14,18,15,26, 14,18,19,19)     # bottom input (mirror)
$segs = for ($i = 0; $i -lt $flat.Count; $i += 4) { ,@($flat[$i], $flat[$i+1], $flat[$i+2], $flat[$i+3]) }
# foreach ($sg in $segs) { Box ($sg[0] - 1) ($sg[1] - 1) ($sg[2] - $sg[0] + 3) ($sg[3] - $sg[1] + 3) (Col '#45e6e6' 40) }
foreach ($sg in $segs) { Box $sg[0] $sg[1] ($sg[2] - $sg[0] + 1) ($sg[3] - $sg[1] + 1) (Col '#45e6e6') }

# items travelling along the lines
foreach ($d in @(@(14, 8), @(14, 22), @(16, 15))) {
    Box ($d[0] - 1) $d[1] 4 2 (Col '#d0ffff' 120); Box $d[0] ($d[1] - 1) 2 4 (Col '#d0ffff' 120)
    Box $d[0] $d[1] 2 2 (Col '#ffffff')
}

# card: n x n, black outline, Minecraft bevel, item in the middle
function Card($x, $y, $n, $item, $shade) {
    Box $x $y $n $n (Col '#000000')
    $i = $n - 2
    Box ($x + 1) ($y + 1) $i $i (Col '#c6c6c6')
    Box ($x + 1) ($y + 1) ($i - 1) 1 (Col '#ffffff'); Box ($x + 1) ($y + 1) 1 ($i - 1) (Col '#ffffff')
    Box ($x + 2) ($y + $n - 2) ($i - 1) 1 (Col '#555555'); Box ($x + $n - 2) ($y + 2) 1 ($i - 1) (Col '#555555')
    $k = $n - 4
    Box ($x + 2) ($y + 2) $k $k (Col $item)
    Box ($x + 2) ($y + 2) 1 1 (Col '#ffffff' 180)
    Box ($x + 3) ($y + $n - 3) ($k - 1) 1 (Col $shade); Box ($x + $n - 3) ($y + 3) 1 ($k - 1) (Col $shade)
}
Card 2 2  8 '#e0a050' '#9a5f22'
Card 2 12 8 '#d8d8e0' '#8a8a96'
Card 2 22 8 '#e04545' '#8e1e1e'
Card 20 11 10 '#b57cff' '#6a3fb0'

$gfx.Dispose()
$bmp.Save($Out, [Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
