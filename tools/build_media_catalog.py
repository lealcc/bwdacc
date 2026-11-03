"""Package edit/upscale contracts alongside the generation catalog, without downloading weights."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import quote

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets/workflows"


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--metadata-dir",type=Path,required=True);parser.add_argument("--model-dir",type=Path,required=True);args=parser.parse_args()
    found={}
    for path in sorted(args.metadata_dir.glob("*--*.json")):
        repo=json.loads(path.read_text(encoding="utf-8"))
        for file in repo.get("siblings",[]):
            if file.get("lfs",{}).get("sha256"):found.setdefault(Path(file['rfilename']).name,(repo,file))
    models={};operations=[]
    variants=[("snofs_klein_edit_api.json","Snofs Klein 9B · Edit NSFW","image_edit",False),
              ("image_upscale_api.json","4x-UltraSharp","image_upscale",False),
              ("open_h3_upscale_compact_api.json","Open-H3 · Kijai compacto","video_upscale",True),
              ("open_h3_upscale_api.json","Open-H3 Regenerate · completo","video_upscale",False)]
    for filename,name,operation,experimental in variants:
        file=ASSETS/filename;graph=json.loads(file.read_text(encoding="utf-8"));dependencies=[]
        for node in graph.values():
            for key,value in node['inputs'].items():
                folder={"unet_name":"diffusion_models","clip_name":"text_encoders","vae_name":"vae","lora_name":"loras","curve_name":"loras","curve_table":"diffusion_models"}.get(key)
                if key=="model_name":folder="upscale_models" if node['class_type']=="UpscaleModelLoader" else "latent_upscale_models"
                if not folder or not isinstance(value,str):continue
                relative=folder+'/'+value.replace('\\','/');dependencies.append(relative)
                if relative in models:continue
                basename=Path(relative).name
                if basename in found:
                    repo,item=found[basename];models[relative]={"destination":"models/"+relative,"repository":repo['id'],"revision":repo['sha'],"sha256":item['lfs']['sha256'],"bytes":item['lfs']['size'],"url":"https://huggingface.co/"+repo['id']+"/resolve/"+repo['sha']+"/"+quote(item['rfilename'],safe='/'),"model_card":"https://huggingface.co/"+repo['id'],"license":repo.get("cardData",{}).get("license","See model card"),"gated":repo.get("gated",False)}
                else:
                    local=args.model_dir/relative
                    if not local.is_file():raise ValueError('Missing source metadata: '+relative)
                    with local.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
                    models[relative]={"destination":"models/"+relative,"sha256":digest,"bytes":local.stat().st_size,"source":"existing_installation","automatic_download":False,"install_state":"manual_model_required"}
        image=operation=='image_upscale'
        operations.append({"name":name,"operation":operation,"base_graph":filename,"sha256":hashlib.sha256(file.read_bytes()).hexdigest(),"experimental":experimental,
                           "profiles":{"24plus":"available","24":"available","16":"available" if image else "unavailable","8":"available" if image else "unavailable"},
                           "validation":"local_gpu_passed", "validation_device":"RTX 3090 24 GB", "validation_scope":"2 second video; no 8/16 GB hardware benchmark" if operation=="video_upscale" else "Neutral still-life test",
                           "model_dependencies":sorted(set(dependencies)),"node_types":sorted({n['class_type'] for n in graph.values()})})
    catalog={"schema_version":1,"version":"2026.10.30.4","automatic_download":False,"models":models,"operations":operations,
             "packages":{"momentos_workflows":{"source":"server/momentos_workflows","destination":"custom_nodes/momentos_workflows"},
                         "open_h3_regenerate":{"repository":"https://github.com/rdh9616/ComfyUI-Open-H3-Regenerate","revision":"9b686c5ffd380e9d1351f9b8beef6d20d703ca3e","required_for":"open_h3_upscale_api.json"},
                         "latent_upscaler":{"repository":"https://github.com/LBH-123-AI/Comfyui_Minimax_h3_latent_Upscaler","required_schema":"MinimaxH3LatentUpscaler3D with mode.width, mode.height, keep_proportion and offload_after_upscale"}},
             "notes":["Compact uses Kijai rank-16 refiner + existing LightX2V 8-step v1.0 rank-24 adapter, without LMS Sharp. Experimental adaptation, not equivalent to the full release.",
                      "Full release uses merged refiner/Turbo/Sharp, curve branches and the official sigma schedule. Native tiled H3 VAE replaces the optional fast decoder.",
                      "This catalog does not download weights. Explicit local installation was tested on 2026-10-30; validate each target server separately."]}
    (ASSETS/'media-operations.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(len(operations),'operations;',len(models),'model requirements; no weights downloaded')


if __name__=='__main__':main()
