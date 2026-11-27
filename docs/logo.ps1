param([string]$Out = "logo.png", [int]$Size = 512)
Add-Type -AssemblyName System.Drawing
$S = $Size / 32          # one "pixel" of the 32x32 art
$bmp = New-Object Drawing.Bitmap $Size, $Size
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'

function C($hex, $a = 255) { $c = [Drawing.ColorTranslator]::FromHtml($hex); [Drawing.Color]::FromArgb($a, $c) }
function P($x, $y, $col) { $b = New-Object Drawing.SolidBrush $col; $g.FillRectangle($b, [float]($x * $S), [float]($y * $S), [float]$S, [float]$S); $b.Dispose() }
function DrawRect($x, $y, $w, $h, $col) { $b = New-Object Drawing.SolidBrush $col; $g.FillRectangle($b, [float]($x * $S), [float]($y * $S), [float]($w * $S), [float]($h * $S)); $b.Dispose() }

# background: dark slate + soft purple (fluix) glow
DrawRect 0 0 32 32 (C '#15131f')
$path = New-Object Drawing.Drawing2D.GraphicsPath
$path.AddEllipse([float](-0.2 * $Size), [float](-0.2 * $Size), [float](1.4 * $Size), [float](1.4 * $Size))
$pg = New-Object Drawing.Drawing2D.PathGradientBrush $path
$pg.CenterColor = C '#3b2a66'; $pg.SurroundColors = @((C '#15131f'))
$g.FillRectangle($pg, 0, 0, $Size, $Size)
# faint lattice dots
for ($y = 1; $y -lt 32; $y += 3) { for ($x = 1; $x -lt 32; $x += 3) { P $x $y (C '#ffffff' 14) } }

# lines (cyan, with a soft glow)
$line = C '#45e6e6'; $glow = C '#45e6e6' 45
function HLine($x0, $x1, $y) { for ($x = [Math]::Min($x0,$x1); $x -le [Math]::Max($x0,$x1); $x++) { P $x ($y-1) $glow; P $x ($y+1) $glow } ; for ($x = [Math]::Min($x0,$x1); $x -le [Math]::Max($x0,$x1); $x++) { P $x $y $line } }
function VLine($x, $y0, $y1) { for ($y = [Math]::Min($y0,$y1); $y -le [Math]::Max($y0,$y1); $y++) { P ($x-1) $y $glow; P ($x+1) $y $glow }; for ($y = [Math]::Min($y0,$y1); $y -le [Math]::Max($y0,$y1); $y++) { P $x $y $line } }
function Edge($x0, $y0, $lane, $x1, $y1) { HLine $x0 $lane $y0; VLine $lane $y0 $y1; HLine $lane $x1 $y1 }

Edge 9 5 10 11 8      # L0 -> M0
Edge 9 15 10 11 10    # L1 -> M0
Edge 9 26 10 11 21    # L2 -> M1
Edge 19 9 21 22 14    # M0 -> F
Edge 19 21 21 22 16   # M1 -> F

# card: 7x7, black outline, Minecraft bevel, 3x3 "item" in the middle
function Card($x, $y, $item, $shade) {
    DrawRect $x $y 7 7 (C '#000000')
    DrawRect ($x+1) ($y+1) 5 5 (C '#c6c6c6')
    DrawRect ($x+1) ($y+1) 5 1 (C '#ffffff'); DrawRect ($x+1) ($y+1) 1 5 (C '#ffffff')
    DrawRect ($x+1) ($y+5) 5 1 (C '#555555'); DrawRect ($x+5) ($y+1) 1 5 (C '#555555')
    P ($x+5) ($y+1) (C '#c6c6c6'); P ($x+1) ($y+5) (C '#c6c6c6')
    DrawRect ($x+2) ($y+2) 3 3 (C $item)
    P ($x+2) ($y+2) (C '#ffffff' 170)
    DrawRect ($x+3) ($y+4) 2 1 (C $shade); P ($x+4) ($y+3) (C $shade)
}
Card 2 2  '#e0a050' '#9a5f22'   # copper-ish
Card 2 12 '#d8d8e0' '#8a8a96'   # iron-ish
Card 2 23 '#e04545' '#8e1e1e'   # redstone-ish
Card 12 6  '#9be35a' '#4f8f22'
Card 12 18 '#5aa0ff' '#2a5cb0'
# final card, slightly highlighted (purple fluix)
Card 23 12 '#b57cff' '#6a3fb0'

# travelling items (bright dots with halo)
function Dot($x, $y) { foreach ($d in @(@(-1,0),@(1,0),@(0,-1),@(0,1))) { P ($x+$d[0]) ($y+$d[1]) (C '#bfffff' 110) }; P $x $y (C '#ffffff') }
Dot 10 7
Dot 10 23
Dot 21 18

$g.Dispose()
$bmp.Save($Out, [Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
