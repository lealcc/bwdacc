"""Explicit opt-in installer for this workstation's missing media-operation weights."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import time
import urllib.request


def install(root,entry):
    target=(root/entry['destination']).resolve()
    if not target.is_relative_to((root/'models').resolve()):raise ValueError('Destination outside model directory')
    if target.exists():return target.name+' already installed'
    if not entry.get('url'):raise ValueError('Manual model required: '+target.name)
    target.parent.mkdir(parents=True,exist_ok=True);partial=Path(str(target)+'.part')
    for attempt in range(3):
        offset=partial.stat().st_size if partial.exists() else 0
        if offset==entry['bytes']:break
        request=urllib.request.Request(entry['url'],headers={'Range':'bytes='+str(offset)+'-'} if offset else {})
        try:
            with urllib.request.urlopen(request,timeout=90) as response:
                resumed=offset>0 and response.status==206
                if resumed and not response.headers.get('Content-Range','').startswith('bytes '+str(offset)+'-'):raise ValueError('Unexpected byte range')
                received=offset if resumed else 0;last=time.monotonic()
                with partial.open('ab' if resumed else 'wb') as stream:
                    while block:=response.read(4*1024*1024):
                        stream.write(block);received+=len(block)
                        if time.monotonic()-last>30:
                            print(target.name,round(received/entry['bytes']*100,1),'%',flush=True);last=time.monotonic()
            if partial.stat().st_size==entry['bytes']:break
        except Exception:
            if attempt==2:raise
    if partial.stat().st_size!=entry['bytes']:raise ValueError('Incomplete download: '+target.name)
    with partial.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    if digest!=entry['sha256']:raise ValueError('SHA-256 mismatch: '+target.name)
    if target.exists():raise ValueError('Destination appeared while downloading: '+target.name)
    partial.rename(target)
    return target.name+' verified and installed'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--catalog',type=Path,required=True);parser.add_argument('--comfy-root',type=Path,required=True);args=parser.parse_args()
    catalog=json.loads(args.catalog.read_text(encoding='utf-8'));missing=[v for v in catalog['models'].values() if not (args.comfy_root/v['destination']).exists()]
    print('Missing files:',len(missing),'GB:',round(sum(x['bytes'] for x in missing)/1e9,2),flush=True)
    with ThreadPoolExecutor(max_workers=2) as pool:
        for result in pool.map(lambda entry:install(args.comfy_root.resolve(),entry),missing):print(result,flush=True)


if __name__=='__main__':main()
