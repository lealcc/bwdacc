"""CPU media integration: preserve duration/audio/aspect and freeze the H3 audio latent."""
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import torch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("momentos_upscale_test",ROOT/"server/momentos_workflows/__init__.py")
module=importlib.util.module_from_spec(spec);sys.modules[spec.name]=module;spec.loader.exec_module(module)
upscale=sys.modules[spec.name+".upscale"]
metadata=sys.modules[spec.name+".lora_metadata"]


class UpscaleServerTest(unittest.TestCase):
    def test_video_duration_soundtrack_and_padding(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);module.folder_paths.set_input_directory(str(root));module.folder_paths.set_temp_directory(str(root/"temp"))
            source=root/"source.mp4"
            module.ffmpeg(["-f","lavfi","-i","testsrc2=s=128x192:r=24:d=2","-f","lavfi","-i","sine=frequency=440:duration=2:sample_rate=48000","-c:v","libx264","-c:a","aac","-shortest",str(source)])
            images,count=upscale.MomentosUpscaleSource().execute("source.mp4",128,192)
            self.assertEqual(count,48);self.assertEqual(tuple(images.shape),(56,192,128,3));self.assertTrue(torch.equal(images[47],images[-1]))
            result,=upscale.MomentosUpscaleFinish().execute(images,"source.mp4",count,256,384)
            output=root/"result.mp4";result.save_to(str(output));duration,w,h,sound=module.probe(output)
            self.assertEqual((w,h,sound),(256,384,True));self.assertAlmostEqual(duration,2,delta=.05)
            module.ffmpeg(["-i",str(output),"-f","null","-"])
            for bad in ["../outside.mp4","missing.mp4"]:
                with self.assertRaises(ValueError):upscale.MomentosUpscaleSource().execute(bad,128,192)

    def test_av_mask_and_h3_frame_math(self):
        class AudioVAE:
            audio_sample_rate=32000
            def encode(self,wave):
                self.length=wave.shape[1];return torch.ones(1,8,220)
        vae=AudioVAE();video=torch.ones(1,24,37,4,4)
        output,=upscale.MomentosUpscaleAV().execute({"samples":video},vae)
        self.assertEqual(vae.length,round(124/24*32000));self.assertEqual(output['samples'].tensors[1].shape[-1],207)
        self.assertTrue(torch.equal(output['samples'].tensors[0],video))
        self.assertEqual(output['noise_mask'].tensors[1].sum().item(),0)
        self.assertEqual(output['noise_mask'].tensors[0].min().item(),1)
        for duration in [float('nan'),0,16]:
            with self.assertRaises(ValueError):upscale.frame_counts(duration)

    def test_metadata_reads_header_and_cache_refresh(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'ugly_name.safetensors';header=json.dumps({'__metadata__':{'modelspec.title':'Soft Film'}}).encode();path.write_bytes(struct.pack('<Q',len(header))+header)
            self.assertEqual(metadata.read_local_metadata(path)['name'],'Soft Film')
            sidecar=Path(str(path)+'.rgthree-info.json');sidecar.write_text(json.dumps({'name':'Analog Cinema','raw':{'civitai':{'model':{'nsfw':True}}}}))
            value=metadata.read_local_metadata(path);self.assertEqual(value['name'],'Analog Cinema');self.assertTrue(value['adult'])
            path.write_bytes(struct.pack('<Q',2**50));self.assertEqual(metadata.read_local_metadata(path)['name'],'Analog Cinema')
            sidecar.unlink()
            header=json.dumps({'__metadata__':{'title':'aitk_lora'}}).encode();path.write_bytes(struct.pack('<Q',len(header))+header)
            self.assertEqual(metadata.read_local_metadata(path)['name'],'ugly name')
            header=b'[]';path.write_bytes(struct.pack('<Q',len(header))+header)
            self.assertEqual(metadata.read_local_metadata(path)['name'],'ugly name')


if __name__=='__main__':unittest.main()
