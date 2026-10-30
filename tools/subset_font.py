"""Rebuild the licensed font asset. Requires fonttools==4.68.0, not an Android runtime dependency."""
import argparse, hashlib, json, shutil
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools import subset

parser=argparse.ArgumentParser()
parser.add_argument('--font',type=Path,required=True,help='Official SourceHanSerifCN-Regular.otf')
parser.add_argument('--license',type=Path,required=True,help='Unmodified upstream LICENSE.txt')
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/assets/fonts')
args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
characters=set(range(32,256))|set(range(0x2000,0x2070))|set(range(0x3000,0x3040))|set(range(0xff00,0xfff0))
for first in range(0xa1,0xf8):
    for second in range(0xa1,0xff):
        try:characters.add(ord(bytes((first,second)).decode('gb2312')))
        except UnicodeDecodeError:pass
font=TTFont(args.font,recalcTimestamp=False)
options=subset.Options();options.layout_features=['*'];options.name_IDs=['*'];options.name_legacy=True;options.name_languages=['*'];options.recalc_timestamp=False
builder=subset.Subsetter(options=options);builder.populate(unicodes=characters);builder.subset(font)
for name in font['name'].names:
    if name.nameID in (1,2,3,4,6,16,17,18):
        value='Regular' if name.nameID in (2,17) else 'RongxueSerif-Regular' if name.nameID==6 else 'Rongxue Reader Serif'
        name.string=value.encode(name.getEncoding(),errors='replace')
if 'CFF ' in font:
    font['CFF '].cff.fontNames=['RongxueSerif-Regular'];top=font['CFF '].cff.topDictIndex[0];top.FamilyName='Rongxue Reader Serif';top.FullName='Rongxue Reader Serif Regular'
destination=args.output/'RongxueSerif-Regular.otf';font.save(destination);font.close()
with TTFont(destination) as checked:count=len(checked.getBestCmap())
shutil.copyfile(args.license,args.output/'OFL.txt')
metadata={'family':'Rongxue Reader Serif','upstream':'Adobe Source Han Serif CN Regular 2.003','url':'https://github.com/adobe-fonts/source-han-serif','download':'https://raw.githubusercontent.com/adobe-fonts/source-han-serif/release/SubsetOTF/CN/SourceHanSerifCN-Regular.otf','license':'SIL OFL 1.1','modified':'GB2312 plus Latin/punctuation subset; reserved font name replaced','sourceSha256':hashlib.sha256(args.font.read_bytes()).hexdigest(),'subsetSha256':hashlib.sha256(destination.read_bytes()).hexdigest(),'mappedCodepoints':count,'bytes':destination.stat().st_size,'tool':'fonttools 4.68.0'}
(args.output/'ABOUT.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(metadata,indent=2))
