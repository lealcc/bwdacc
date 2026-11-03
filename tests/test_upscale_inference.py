"""Opt-in inference on a prepared graph; refuses an occupied test queue."""
import argparse
import json
from pathlib import Path
import time
import urllib.request

parser=argparse.ArgumentParser();parser.add_argument('--server',required=True);parser.add_argument('--graph',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
def get(path):return json.load(urllib.request.urlopen(args.server+path,timeout=30))
queue=get('/queue')
if queue['queue_running'] or queue['queue_pending']:raise RuntimeError('Test server is busy')
body=json.dumps({'prompt':json.loads(args.graph.read_text(encoding='utf-8')),'client_id':'momentos-upscale-qa'}).encode()
try:response=json.load(urllib.request.urlopen(urllib.request.Request(args.server+'/prompt',body,{'Content-Type':'application/json'}),timeout=30))
except urllib.error.HTTPError as e:print(e.read().decode(),flush=True);raise
pid=response['prompt_id'];print('submitted',pid,flush=True);start=time.monotonic();last=start
while time.monotonic()-start<1800:
    job=get('/history/'+pid).get(pid)
    if job and (job['status']['completed'] or job['status']['status_str']=='error'):
        job['qa_elapsed_seconds']=round(time.monotonic()-start,1);args.report.write_text(json.dumps(job),encoding='utf-8');print(job['status']['status_str'],job['qa_elapsed_seconds'],flush=True)
        if job['status']['status_str']!='success':
            print([(event,data.get('exception_message','')) for event,data in job['status']['messages'] if event=='execution_error'],flush=True);raise RuntimeError('Upscale inference failed')
        break
    if time.monotonic()-last>=30:print('elapsed',round(time.monotonic()-start),'seconds',flush=True);last=time.monotonic()
    time.sleep(3)
else:raise TimeoutError(pid)
