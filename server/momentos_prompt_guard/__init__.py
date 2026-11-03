"""Validate a local writer's output before it becomes image/video conditioning."""
import re


class MomentosPromptGuard:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {
            "text": ("STRING", {"forceInput": True}),
            "format": (["image", "h3", "h3_reference"],),
            "pictures": ("INT", {"default": 0, "min": 0, "max": 32}),
            "videos": ("INT", {"default": 0, "min": 0, "max": 32}),
            "audios": ("INT", {"default": 0, "min": 0, "max": 32}),
        }}

    RETURN_TYPES = ("STRING",)
    FUNCTION = "validate"
    CATEGORY = "Momentos"

    def validate(self, text, format, pictures, videos, audios):
        value = text.strip()
        lower = value.lower()
        if not value or len(value) > 20000 or any(marker in lower for marker in (
            "<think>", "</think>", "[empty 'content'", "[no text returned", "reasoning_content was:",
        )):
            raise ValueError("O Agente não produziu um prompt completo. Revise o pedido ou desative Agente.")
        if format != "image":
            sections = (["subject_definitions", "summary", "retention_analysis", "detailed_description"]
                        if format == "h3_reference" else ["integrated_multimodal_description"])
            sections += ["overall_soundscape", "non_diegetic_music"]
            previous = -1
            for name in sections:
                match = re.search(r"(?m)^" + name + r":\s*\S", value)
                if match is None or match.start() <= previous:
                    raise ValueError("O Agente retornou um formato incompleto para MiniMax H3. Revise ou tente sem Agente.")
                previous = match.start()
            for label, count in (("Picture", pictures), ("Video", videos), ("Audio", audios)):
                indices = re.findall(r"(?:<|\b)" + label + r"\s+(\d+)(?:>|\b)", value)
                if any(int(index) < 1 or int(index) > count for index in indices):
                    raise ValueError("O Agente citou uma referência não conectada: " + label)
        return (value,)


NODE_CLASS_MAPPINGS = {"MomentosPromptGuard": MomentosPromptGuard}
NODE_DISPLAY_NAME_MAPPINGS = {"MomentosPromptGuard": "Momentos · Validar prompt do Agente"}
