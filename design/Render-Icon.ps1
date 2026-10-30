param([string]$Output=(Join-Path $PSScriptRoot 'icon-preview.png'),[int]$Size=432)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$culture=[Globalization.CultureInfo]::InvariantCulture
$androidNamespace='http://schemas.android.com/apk/res/android'
function Read-Number($Tokens,[ref]$At) {
    $value=[single]::Parse($Tokens[$At.Value].Value,$culture)
    $At.Value=$At.Value+1
    return $value
}
function Parse-Path([string]$Data) {
    $tokens=[regex]::Matches($Data,'[MLHVQCZ]|-?\d+(?:\.\d+)?')
    $path=New-Object Drawing.Drawing2D.GraphicsPath
    $i=0;$x=0.;$y=0.;$startX=0.;$startY=0.
    while($i -lt $tokens.Count) {
        $command=$tokens[$i].Value;$i++
        switch($command) {
            'M' {$x=Read-Number $tokens ([ref]$i);$y=Read-Number $tokens ([ref]$i);$startX=$x;$startY=$y;$path.StartFigure()}
            'L' {$nx=Read-Number $tokens ([ref]$i);$ny=Read-Number $tokens ([ref]$i);$path.AddLine([single]$x,[single]$y,[single]$nx,[single]$ny);$x=$nx;$y=$ny}
            'H' {$nx=Read-Number $tokens ([ref]$i);$path.AddLine([single]$x,[single]$y,[single]$nx,[single]$y);$x=$nx}
            'V' {$ny=Read-Number $tokens ([ref]$i);$path.AddLine([single]$x,[single]$y,[single]$x,[single]$ny);$y=$ny}
            'Q' {
                $qx=Read-Number $tokens ([ref]$i);$qy=Read-Number $tokens ([ref]$i);$nx=Read-Number $tokens ([ref]$i);$ny=Read-Number $tokens ([ref]$i)
                $c1x=$x+2./3*($qx-$x);$c1y=$y+2./3*($qy-$y);$c2x=$nx+2./3*($qx-$nx);$c2y=$ny+2./3*($qy-$ny)
                $path.AddBezier([single]$x,[single]$y,[single]$c1x,[single]$c1y,[single]$c2x,[single]$c2y,[single]$nx,[single]$ny);$x=$nx;$y=$ny
            }
            'C' {
                $c1x=Read-Number $tokens ([ref]$i);$c1y=Read-Number $tokens ([ref]$i);$c2x=Read-Number $tokens ([ref]$i);$c2y=Read-Number $tokens ([ref]$i)
                $nx=Read-Number $tokens ([ref]$i);$ny=Read-Number $tokens ([ref]$i)
                $path.AddBezier([single]$x,[single]$y,[single]$c1x,[single]$c1y,[single]$c2x,[single]$c2y,[single]$nx,[single]$ny);$x=$nx;$y=$ny
            }
            'Z' {$path.CloseFigure();$x=$startX;$y=$startY}
            default {throw "Unsupported vector command: $command"}
        }
    }
    return $path
}
function Read-Attribute($Node,[string]$Name,[single]$Fallback) {
    $text=$Node.GetAttribute($Name,$androidNamespace)
    if(!$text){return $Fallback}
    return [single]::Parse($text,$culture)
}
function Read-Color([string]$Value) {
    if($Value.Length -eq 9) {
        return [Drawing.Color]::FromArgb([Convert]::ToInt32($Value.Substring(1,2),16),[Convert]::ToInt32($Value.Substring(3,2),16),[Convert]::ToInt32($Value.Substring(5,2),16),[Convert]::ToInt32($Value.Substring(7,2),16))
    }
    return [Drawing.ColorTranslator]::FromHtml($Value)
}
function Render-Node($Node,[Drawing.Graphics]$Graphics) {
    if($Node.NodeType -ne [Xml.XmlNodeType]::Element){return}
    switch($Node.LocalName) {
        'group' {
            $state=$Graphics.Save();$matrix=New-Object Drawing.Drawing2D.Matrix
            try {
                $px=Read-Attribute $Node 'pivotX' 0;$py=Read-Attribute $Node 'pivotY' 0
                $sx=Read-Attribute $Node 'scaleX' 1;$sy=Read-Attribute $Node 'scaleY' 1
                $angle=Read-Attribute $Node 'rotation' 0;$tx=Read-Attribute $Node 'translateX' 0;$ty=Read-Attribute $Node 'translateY' 0
                $order=[Drawing.Drawing2D.MatrixOrder]::Append
                $matrix.Translate(-$px,-$py,$order);$matrix.Scale($sx,$sy,$order);$matrix.Rotate($angle,$order);$matrix.Translate(($px+$tx),($py+$ty),$order)
                $Graphics.MultiplyTransform($matrix,[Drawing.Drawing2D.MatrixOrder]::Prepend)
                foreach($child in $Node.ChildNodes){Render-Node $child $Graphics}
            }finally{$matrix.Dispose();$Graphics.Restore($state)}
        }
        'clip-path' {
            $path=Parse-Path $Node.GetAttribute('pathData',$androidNamespace)
            try{$Graphics.SetClip($path,[Drawing.Drawing2D.CombineMode]::Intersect)}finally{$path.Dispose()}
        }
        'path' {
            $path=Parse-Path $Node.GetAttribute('pathData',$androidNamespace)
            try {
                $fill=$Node.GetAttribute('fillColor',$androidNamespace)
                if($fill){$color=Read-Color $fill;if($color.A -gt 0){$brush=New-Object Drawing.SolidBrush($color);try{$Graphics.FillPath($brush,$path)}finally{$brush.Dispose()}}}
                $stroke=$Node.GetAttribute('strokeColor',$androidNamespace)
                if($stroke) {
                    $pen=New-Object Drawing.Pen((Read-Color $stroke),(Read-Attribute $Node 'strokeWidth' 1))
                    $pen.StartCap=[Drawing.Drawing2D.LineCap]::Round;$pen.EndCap=[Drawing.Drawing2D.LineCap]::Round;$pen.LineJoin=[Drawing.Drawing2D.LineJoin]::Round
                    try{$Graphics.DrawPath($pen,$path)}finally{$pen.Dispose()}
                }
            }finally{$path.Dispose()}
        }
    }
}
$project=Split-Path $PSScriptRoot -Parent
[xml]$vector=[IO.File]::ReadAllText((Join-Path $project 'app\src\main\res\drawable\ic_leaf.xml'))
$canvas=New-Object Drawing.Bitmap(($Size*2),($Size*2));$graphics=[Drawing.Graphics]::FromImage($canvas)
try {
    $graphics.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([Drawing.Color]::Transparent);$graphics.ScaleTransform(($Size*2/108.),($Size*2/108.))
    foreach($node in $vector.vector.ChildNodes){Render-Node $node $graphics}
}finally{$graphics.Dispose()}
$preview=New-Object Drawing.Bitmap($Size,$Size);$graphics=[Drawing.Graphics]::FromImage($preview)
try {
    $graphics.Clear([Drawing.Color]::Transparent);$graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($canvas,(New-Object Drawing.Rectangle(0,0,$Size,$Size)))
    $preview.Save($Output,[Drawing.Imaging.ImageFormat]::Png)
}finally{$graphics.Dispose();$preview.Dispose();$canvas.Dispose()}
Write-Output "Rendered active vector icon: $Output"
