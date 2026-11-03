"""Build offline Android string resources and reject incomplete language packs by default."""
import argparse
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT=Path(__file__).resolve().parents[1]
LANGUAGES={'en':'values-en','ru':'values-ru','de':'values-de','ja':'values-ja','zh':'values-b+zh+Hans','es':'values-es'}

def placeholders(text):return sorted(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]',text))

def emit(folder,values):
    directory=ROOT/'app/src/main/res'/folder;directory.mkdir(parents=True,exist_ok=True)
    entries=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for key,value in sorted(values.items()):entries.append('    <string name="'+key+'" formatted="false">'+escape(json.dumps(value,ensure_ascii=False))+'</string>')
    entries.append('</resources>');(directory/'strings.xml').write_text('\n'.join(entries)+'\n',encoding='utf-8')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--allow-incomplete',action='store_true');args=parser.parse_args()
    source=json.loads((ROOT/'translations/source.json').read_text(encoding='utf-8'))
    portuguese=ROOT/'translations/pt-BR.json'
    overrides=json.loads(portuguese.read_text(encoding='utf-8')) if portuguese.exists() else {}
    for key,value in overrides.items():
        if key not in source or placeholders(source[key])!=placeholders(value):raise ValueError('Invalid Portuguese override: '+key)
    emit('values',{**source,**overrides})
    for code,folder in LANGUAGES.items():
        file=ROOT/f'translations/{code}.json';values=json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
        missing=set(source)-set(values)
        if missing and not args.allow_incomplete:raise ValueError(f'{code}: {len(missing)} translations missing')
        for key,value in values.items():
            if key not in source:continue
            if not isinstance(value,str) or not value.strip():raise ValueError(f'{code}/{key}: empty translation')
            if placeholders(source[key])!=placeholders(value):raise ValueError(f'{code}/{key}: changed format placeholders')
        emit(folder,{k:values.get(k,v) for k,v in source.items()});print(code,len(source)-len(missing),'/',len(source))
        if code=='zh':emit('values-zh-rCN',{k:values.get(k,v) for k,v in source.items()})

if __name__=='__main__':main()
