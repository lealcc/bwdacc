"""Prepare a reference for captioning without modifying its stored source image."""
import torch
import torch.nn.functional as F


class BwdaccReferenceFocus:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"image": ("IMAGE",), "foreground": ("MASK",), "transparent": ("MASK",)}}

    RETURN_TYPES = ("IMAGE",)
    FUNCTION = "focus"
    CATEGORY = "bwdacc/references"

    def focus(self, image, foreground, transparent):
        if image.ndim != 4 or image.shape[0] != 1 or image.shape[-1] < 3:
            raise ValueError("Selecione uma única imagem de referência.")
        source = image[..., :3].detach().float().cpu().nan_to_num().clamp(0, 1)
        height, width = source.shape[1:3]

        def resize(mask):
            mask = mask.detach().float().cpu().reshape(-1, 1, *mask.shape[-2:])[:1]
            return F.interpolate(mask, size=(height, width), mode="bilinear", align_corners=False)[0, 0].nan_to_num().clamp(0, 1)

        alpha = 1 - resize(transparent)
        mask = resize(foreground) * alpha
        # An empty/near-empty segmentation must not turn a usable reference into a blank image.
        if (mask > .15).sum().item() < max(16, width * height * .002):
            mask = alpha
        result = source * mask[None, ..., None] + .5 * (1 - mask[None, ..., None])
        coords = (mask > .02).nonzero()
        if coords.numel():
            top, left = coords.min(0).values.tolist()
            bottom, right = (coords.max(0).values + 1).tolist()
            padding = max(8, round(max(right - left, bottom - top) * .06))
            result = result[:, max(0, top - padding):min(height, bottom + padding),
                            max(0, left - padding):min(width, right + padding)]
        return (result.contiguous(),)
