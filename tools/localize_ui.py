"""Extract literal UI text at presentation boundaries, without rewriting data or prompt values.

Development-only migration helper (javalang). Resource files are committed/build inputs;
the app never translates via network at runtime.
"""
import argparse
import json
import re
from pathlib import Path
import javalang

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/leal/comfyremote'

def resource_key(text):
    value=0
    raw=text.encode('utf-16-be')
    for i in range(0,len(raw),2):value=(31*value+int.from_bytes(raw[i:i+2],'big'))&0xffffffff
    return 'ui_'+format(value,'x')

def literals(expr):
    if isinstance(expr,javalang.tree.Literal):yield expr
    elif isinstance(expr,javalang.tree.BinaryOperation) and expr.operator=='+':
        yield from literals(expr.operandl);yield from literals(expr.operandr)
    elif isinstance(expr,javalang.tree.TernaryExpression):
        yield from literals(expr.if_true);yield from literals(expr.if_false)
    elif isinstance(expr,javalang.tree.ArrayCreator):yield from literals(expr.initializer)
    elif isinstance(expr,javalang.tree.ArrayInitializer):
        for child in expr.initializers:yield from literals(child)
    elif isinstance(expr,javalang.tree.MethodInvocation) and expr.member=='format':
        for arg in expr.arguments:
            if isinstance(arg,javalang.tree.Literal):yield arg;break

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    target=ROOT/'translations/source.json'
    sources=json.loads(target.read_text(encoding='utf-8')) if target.exists() else {};changes=0
    for path in JAVA.glob('*.java'):
        if path.name=='UiLanguage.java':continue
        code=path.read_text(encoding='utf-8');tree=javalang.parse.parse(code);selected={};line_offsets=[0]
        for line in code.splitlines(keepends=True):line_offsets.append(line_offsets[-1]+len(line))
        helpers={}
        for _,m in tree.filter(javalang.tree.MethodDeclaration):
            if m.name in {'text','button','icon','section','field','toggle','permission','page','card','label','detailRow','videoError'}:
                helpers[m.name]=[i for i,p in enumerate(m.parameters) if p.type.name=='String' and p.name not in {'kind','key','icon','symbol','type'}]
                if m.name=='icon':helpers[m.name]=helpers[m.name][-1:]
        for ancestry,call in tree.filter(javalang.tree.MethodInvocation):
            indexes=[]
            if call.qualifier=='UiLanguage' and call.member=='t':
                for arg in call.arguments:
                    for literal in literals(arg):
                        if literal.value.startswith('"'):
                            text=json.loads(literal.value);sources[resource_key(text)]=text
                continue
            if call.member in {'setText','setHint','setTitle','setMessage','setPositiveButton','setNegativeButton','setNeutralButton','setContentDescription','setError','setItems','setSingleChoiceItems','setMultiChoiceItems','drawText'}:indexes=[0]
            elif call.member in helpers and call.qualifier in ('','this'):indexes=helpers[call.member]
            elif call.member=='makeText' and call.qualifier=='Toast':indexes=[1]
            elif call.member=='symbol':indexes=[2]
            elif call.member=='label' and call.qualifier=='BoxEditor':indexes=[1]
            elif call.member=='icon' and call.qualifier=='BoxEditor':indexes=[2]
            elif call.member=='segment':indexes=[1,4]
            elif call.member=='settingsSection':indexes=[2,3]
            elif call.member=='add' and any(isinstance(p,javalang.tree.MethodInvocation) and p.member=='getMenu' for p in ancestry):indexes=[len(call.arguments)-1]
            for index in indexes:
                if index>=len(call.arguments):continue
                for literal in literals(call.arguments[index]):
                    if literal.position:selected[literal.position]=literal
        for _,call in tree.filter(javalang.tree.ClassCreator):
            if call.type.name.endswith('Exception') or call.type.name=='Display':
                for arg in call.arguments[:2] if call.type.name=='Display' else call.arguments[:1]:
                    for literal in literals(arg):
                        if literal.position:selected[literal.position]=literal
        ui_returns={'GenerationProgress.java':{'stage'},'ServerMonitorData.java':{'title'},'MemoryProfiles.java':{'description','unavailable','extensionUnavailable'},'UpscaleWorkflow.java':{'unavailable'},'WorkflowPicker.java':{'description'},'ReferencePreset.java':{'label'},'GenerationDetails.java':{'duration'}}
        for ancestry,ret in tree.filter(javalang.tree.ReturnStatement):
            method=next((p.name for p in reversed(ancestry) if isinstance(p,javalang.tree.MethodDeclaration)),None)
            if method in ui_returns.get(path.name,set()):
                for literal in literals(ret.expression):
                    if literal.position:selected[literal.position]=literal
        replacements=[]
        for pos,literal in selected.items():
            if not literal.value.startswith('"'):continue
            text=json.loads(literal.value)
            if not re.search(r'[A-Za-zÀ-ž]',text) or re.match(r'^(https?://|image/|video/|audio/|application/)',text):continue
            if re.fullmatch(r'[xX%\d .:+/×−]+',text):continue
            key=resource_key(text)
            if key in sources and sources[key]!=text:raise ValueError('Resource key collision')
            sources[key]=text
            at=line_offsets[pos.line-1]+pos.column-1
            replacements.append((at,at+len(literal.value),'UiLanguage.t('+literal.value+')'))
        if args.apply:
            for start,end,value in sorted(replacements,reverse=True):code=code[:start]+value+code[end:]
            path.write_text(code,encoding='utf-8')
        changes+=len(replacements)
    target=ROOT/'translations/source.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(dict(sorted(sources.items())),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(len(sources),'unique UI strings;',changes,'literal presentation changes')

if __name__=='__main__':main()
