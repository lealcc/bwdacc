"""Read-only readiness audit of an explicitly selected, running ComfyUI installation."""
import argparse
import hashlib
import json
import urllib.request
from pathlib import Path


def audit(root, server):
    repo = Path(__file__).resolve().parents[1]
    schema = json.load(urllib.request.urlopen(server.rstrip('/') + '/object_info', timeout=60))
    missing, graphs, resources = {}, 0, []
    model_keys = {'unet_name', 'clip_name', 'vae_name', 'lora_name', 'model_name',
                  'bg_removal_name', 'curve_name', 'curve_table', 'ckpt_name'}
    def choices(node, key):
        for section in ('required', 'optional'):
            spec = schema.get(node, {}).get('input', {}).get(section, {}).get(key)
            if spec:
                if isinstance(spec[0], list): return spec[0]
                if len(spec) > 1 and isinstance(spec[1], dict): return spec[1].get('options')
        return None
    for path in (repo / 'app/src/main/assets/workflows').rglob('*.json'):
        graph = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(graph, dict) or not graph or not all(isinstance(n, dict) and 'class_type' in n for n in graph.values()): continue
        graphs += 1
        errors = []
        for node in graph.values():
            kind, inputs = node['class_type'], node.get('inputs', {})
            if kind not in schema: errors.append('Node: ' + kind); continue
            for key, value in inputs.items():
                options = choices(kind, key) if key in model_keys else None
                if options and isinstance(value, str) and value.replace('\\', '/') not in [str(v).replace('\\', '/') for v in options]: errors.append(key + ': ' + value)
                if kind == 'Power Lora Loader (rgthree)' and isinstance(value, dict) and value.get('on'):
                    options = choices('LoraLoader', 'lora_name') or []
                    if value.get('lora', '').replace('\\', '/') not in [str(v).replace('\\', '/') for v in options]: errors.append('LoRA: ' + value.get('lora', ''))
        if errors: missing[str(path.relative_to(repo))] = sorted(set(errors))
    visual = repo / 'desktop/Bwdacc.Desktop/Payload/visual-models.json'
    for item in json.loads(visual.read_text())['files']:
        file = root / item['destination']
        ok = file.is_file() and file.stat().st_size == item['bytes']
        if ok and item.get('sha256'):
            with file.open('rb') as stream: ok = hashlib.file_digest(stream, 'sha256').hexdigest() == item['sha256']
        resources.append({'file': item['destination'], 'ok': ok, 'verification': 'size+sha256'})
        if not ok: missing[item['destination']] = ['Missing or damaged file']
    for manifest in ('catalog.json', 'media-operations.json'):
        catalog = json.loads((repo / 'app/src/main/assets/workflows' / manifest).read_text(encoding='utf-8'))
        for key, item in catalog.get('models', {}).items():
            destination = item.get('destination')
            if not destination: continue
            file = root / destination
            ok = file.is_file() and (not item.get('bytes') or file.stat().st_size == item['bytes'])
            resources.append({'file': destination, 'ok': ok, 'verification': 'size'})
            if not ok: missing[destination] = ['Missing or unexpected size (may be an alternative memory profile)']
    package_file = root / 'custom_nodes/bwdacc_musetalk/package.json'
    muse_root = Path(json.loads(package_file.read_text(encoding='utf-8'))['path']) if package_file.exists() else root / 'models/bwdacc_musetalk'
    for item in json.loads((repo / 'server/musetalk/manifest.json').read_text())['files']:
        file = muse_root / item['path']
        ok = file.is_file() and file.stat().st_size == item['bytes']
        if ok:
            with file.open('rb') as stream: ok = hashlib.file_digest(stream, 'sha256').hexdigest() == item['sha256']
        resources.append({'file': str(file), 'ok': ok, 'verification': 'size+sha256'})
        if not ok: missing[str(file)] = ['Missing or damaged MuseTalk resource']
    for name in ('ready.json', 'runner.py', 'environment/Scripts/python.exe'):
        if not (muse_root / name).is_file(): missing[str(muse_root / name)] = ['MuseTalk runtime missing']
    required = ['BwdaccMuseTalk', 'BwdaccVisualExtract', 'BwdaccReferenceFocus', 'DepthAnythingV2Preprocessor', 'DWPreprocessor',
                'LineArtPreprocessor', 'Canny', 'HEDPreprocessor', 'BAE-NormalMapPreprocessor',
                'ScribblePreprocessor', 'OneFormer-ADE20K-SemSegPreprocessor', 'LoadBackgroundRemovalModel',
                'RemoveBackground', 'LlamaServerVisionCaption', 'MomentosPromptGuard', 'MomentosVideoRange',
                'MomentosVideoExtensionMerge', 'MomentosUpscaleSource', 'MomentosUpscaleAV', 'MomentosUpscaleFinish']
    for node in required:
        if node not in schema: missing[node] = ['Node not loaded']
    return {'graphs': graphs, 'nodes_loaded': len(schema), 'resources': resources, 'missing': missing}


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--comfy-root', required=True, type=Path); p.add_argument('--server', required=True); p.add_argument('--output', required=True, type=Path)
    args = p.parse_args(); report = audit(args.comfy_root.resolve(strict=True), args.server)
    args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'resources'}, ensure_ascii=False))
