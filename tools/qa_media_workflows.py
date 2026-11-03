"""Explicit local GPU smoke tests; uses a neutral still-life source and waits for an idle queue."""
import argparse
import json
import time
import shutil
import urllib.request
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server',required=True,help='Explicit isolated commercial ComfyUI URL')
parser.add_argument('--comfy-root',required=True,type=Path,help='Isolated commercial ComfyUI installation')
parser.add_argument('--source',required=True,type=Path,help='Neutral still-life image used by the test')
args=parser.parse_args()
SERVER=args.server.rstrip('/')
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'runtime'/'qa-media'

def get(path):
    return json.load(urllib.request.urlopen(SERVER+path,timeout=30))

def run(name,graph):
    start=time.time()
    while True:
        queue=get('/queue')
        if not queue['queue_running'] and not queue['queue_pending']:break
        if time.time()-start>1800:raise RuntimeError('Server queue stayed busy; no jobs were interrupted')
        time.sleep(5)
    body=json.dumps({'prompt':graph,'client_id':'momentos-neutral-upscale-qa'}).encode()
    try:
        response=json.load(urllib.request.urlopen(urllib.request.Request(SERVER+'/prompt',body,{'Content-Type':'application/json'}),timeout=30))
    except urllib.error.HTTPError as e:
        print(e.read().decode(),flush=True);raise
    pid=response['prompt_id'];print(name,pid,flush=True);start=time.time()
    while time.time()-start<1200:
        job=get('/history/'+pid).get(pid)
        if job and job.get('status',{}).get('completed'):
            (OUT/(name+'-result.json')).write_text(json.dumps(job),encoding='utf-8');print(name,job['status']['status_str'],round(time.time()-start,1),flush=True);return
        if job and job.get('status',{}).get('status_str')=='error':
            (OUT/(name+'-result.json')).write_text(json.dumps(job),encoding='utf-8');raise RuntimeError(name+' failed')
        time.sleep(3)
    raise TimeoutError(pid)

source=args.source.resolve(strict=True)
comfy_root=args.comfy_root.resolve(strict=True)
if not (comfy_root/'main.py').is_file():raise ValueError('ComfyUI installation not found')
schema=get('/object_info')
OUT.mkdir(parents=True,exist_ok=True)
target=comfy_root/'input'/'Momentos'/'qa_still_life.png';target.parent.mkdir(exist_ok=True,parents=True);shutil.copy2(source,target)
for name,asset in [('snofs','snofs_klein_edit_api.json'),('ultrasharp','image_upscale_api.json')]:
    g=json.loads((ROOT/'app/src/main/assets/workflows'/asset).read_text(encoding='utf-8'))
    if name=='snofs':
        g['1']['inputs']['image']='Momentos/qa_still_life.png'
        g['6']['inputs']['text']='Change the red cup to a matte green cup. Keep the blue vase, flowers, table, framing and lighting unchanged.'
        g['12']['inputs']['noise_seed']=12345
        g['19']['inputs']['filename_prefix']='Momentos/QA/Snofs/edit'
    else:
        g['momentos_upscale_image']['inputs']['image']='Momentos/qa_still_life.png'
        g['size']['inputs'].update(width=2016,height=2016)
        g['save']['inputs']['filename_prefix']='Momentos/QA/Upscale/UltraSharp2x'
    for node in g.values():
        definitions=schema[node['class_type']]['input']
        for key,value in list(node['inputs'].items()):
            if not isinstance(value,str):continue
            spec=definitions.get('required',{}).get(key,definitions.get('optional',{}).get(key))
            choices=spec[0] if spec and isinstance(spec[0],list) else spec[1].get('options',[]) if spec and len(spec)>1 and isinstance(spec[1],dict) else []
            for choice in choices:
                if isinstance(choice,str) and value.replace('\\','/')==choice.replace('\\','/'):node['inputs'][key]=choice;break
    (OUT/(name+'-submitted.json')).write_text(json.dumps(g),encoding='utf-8');run(name,g)
