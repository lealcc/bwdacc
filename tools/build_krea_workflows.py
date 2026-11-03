"""Clean Krea 2 recipes using already installed, publicly pinned FP8 weights.

RAW follows krea-ai/krea-2 sampling.py: resolution-dependent mu between image
sequence lengths 256 and 6400, and v=cond+3.5*(cond-uncond), i.e. Comfy CFG 4.5.
ModelSamplingFlux uses endpoints 256 and 4096, so max_shift=.90625 reproduces
the same line. Turbo keeps the native fixed mu=1.15 and CFG-free Euler sampling.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/workflows'


def node(kind, title=None, **inputs):
    value = {'class_type': kind, 'inputs': inputs}
    if title: value['_meta'] = {'title': title}
    return value


def main():
    for kind, steps in [('raw',52), ('turbo',8), ('turbo_4step',4)]:
        raw = kind == 'raw'
        graph = {
            '1': node('UNETLoader', unet_name=f'krea2\\krea2_{"raw" if raw else "turbo"}_fp8_scaled.safetensors', weight_dtype='default'),
            '2': node('CLIPLoader', clip_name='qwen3vl_4b_fp8_scaled.safetensors', type='krea2', device='default'),
            '3': node('Power Lora Loader (rgthree)', 'Krea 2 LoRAs', model=['1',0], clip=['2',0]),
            '4': node('CLIPTextEncode', 'Positive Prompt', text='A small blue paper boat on a wooden table, soft window light, still life photography.', clip=['3',1]),
            '5': node('CLIPTextEncode', 'Negative Prompt', text='', clip=['3',1]) if raw else node('ConditioningZeroOut', conditioning=['4',0]),
            '6': node('EmptyLatentImage', width=1024, height=1024, batch_size=1),
            '7': node('KSampler', seed=0, steps=steps, cfg=4.5 if raw else 1.0, sampler_name='euler', scheduler='simple', denoise=1.0, model=['3',0], positive=['4',0], negative=['5',0], latent_image=['6',0]),
            '8': node('VAELoader', vae_name='qwen_image_vae.safetensors'),
            '9': node('VAEDecode', samples=['7',0], vae=['8',0]),
            '10': node('SaveImage', filename_prefix='Momentos/Krea2_'+kind, images=['9',0]),
        }
        graph['4']['_meta']['momentos_prompt_input'] = 'text'
        if raw:
            graph['11'] = node('ModelSamplingFlux', 'RAW resolution-aware schedule', model=['3',0], max_shift=.90625, base_shift=.5, width=1024, height=1024)
            graph['7']['inputs']['model'] = ['11',0]
        elif kind == 'turbo_4step':
            graph['11'] = node('LoraLoaderModelOnly', 'Structural 4-step distillation', model=['1',0], lora_name='krea2\\krea2_turbo_4step_rank_64_lora_comfyui.safetensors', strength_model=1.0)
            graph['3']['inputs']['model'] = ['11',0]
        (ROOT / f'krea2_{kind}_api.json').write_text(json.dumps(graph,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Wrote 3 clean Krea 2 graphs; original imported workflows preserved')


if __name__ == '__main__': main()
