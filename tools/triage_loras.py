"""Offline triage. Never move weights or send the user's model inventory externally."""
import argparse
import collections
import importlib.util
import json
import re
from pathlib import Path

ADULT = re.compile(r"nsfw|snofs|nude|nudity|nudius|naked|sex|porn|hentai|cum|pussy|penis|dick|cock|anal(?!og|ysis|ytic)|adult.toons|thumbinbutt|anus|vagina|vag_|futanari|fellatio|blowjob|deepthroat|handjob|footjob|titjob|titfuck|titsjob|titt|nippl|areola|boob|breast|panties|pantyhose|lingerie|cameltoe|dildo|strapon|masturb|fisting|fingering|rimjob|cunniling|gangbang|creampie|spitroast|thrust|humping|piledriver|mating.press|doggy|cowgirl|bondage|downblouse|down_blouse|upskirt|onlyfans|playboy|metart|sexart|brattysis|inniep|1nnie|innie.v|inniev|inn ie|frenulum|glans|xxx|exposure|uncensor|decensor|spicy|amateur.nudes|herpov|bwc|bottom.heavy", re.I)
TECH = re.compile(r"turbotime|lightx2v|(?:^|[/_ -])turbo|taomate|fasth3|steps.dmd|step.distill|flux_2-turbo|svi_v2|open_regenerate|open_h3_regenerate|sharp_curve_branches|camera_motion|pano_video_gen|minimax_h3_ref_lora", re.I)
STYLE = re.compile(r"bullet.?time|dr0nesh0t|backgroundsharpness|arcane.cartoon|botwstyle|botwzeldastyle|flat.anime|lp.flat|disney.renaissance|norman.rockwell|analogcore|cinematic.photography|directflashfilm|phone.photography|pinholecam|retro.vintage.comics|16mm.found|disney.golden|frozenworld|stereoptical|davham.cinema|digicam.y2k|looping.sketch|motion_speed|autochrome|60spsy|40scyberpunk|street.photograph|kodacolor|dramatic_dark_lighting|y2k.fashion|cinematic_shot|laser.hologram|friendlysketch|fleischer|domagoj.sokcevic|y2ksnap", re.I)


def classify(name, sidecar):
    normalized = name.replace("\\", "/")
    civ = sidecar.get("raw", {}).get("civitai", {}) or {}
    model = civ.get("model", {}) or {}
    if ADULT.search(Path(normalized).name): return "nsfw", "Nome explicito/adulto"
    if TECH.search(normalized): return "sfw", "Adaptador tecnico de amostragem, camera ou upscale"
    if model.get("nsfw") is True or isinstance(civ.get("nsfwLevel"), (int, float)) and civ["nsfwLevel"] >= 4:
        return "nsfw", "Marcacao adulta nos metadados locais do Civitai"
    if any(isinstance(x.get("nsfwLevel"), (int, float)) and x["nsfwLevel"] >= 4 for x in sidecar.get("images", [])):
        return "nsfw", "Amostras locais catalogadas como adultas"
    if model.get("nsfw") is False and civ.get("nsfwLevel") in (None, 1, 2):
        return "sfw", "Metadados locais SFW, sem sinal adulto no nome ou amostras"
    if re.search(r"/(sex|nsfw|adult)/", "/"+normalized, re.I): return "nsfw", "Pasta explicitamente adulta"
    if STYLE.search(normalized): return "sfw", "Nome de estilo visual, fotografia ou movimento sem sinal adulto"
    return "nsfw", "Classificacao conservadora: metadados insuficientes"


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--models",required=True);parser.add_argument("--inventory",required=True);parser.add_argument("--output",required=True);args=parser.parse_args()
    spec=importlib.util.spec_from_file_location("lora_metadata",Path(__file__).resolve().parents[1]/"server/momentos_workflows/lora_metadata.py")
    metadata=importlib.util.module_from_spec(spec);spec.loader.exec_module(metadata)
    root=Path(args.models);names=json.loads(Path(args.inventory).read_text(encoding="utf-8"));entries={}
    for name in names:
        file=root/Path(name.replace("\\","/"));sidecar={}
        info=Path(str(file)+".rgthree-info.json")
        if info.exists():
            try:sidecar=json.loads(info.read_text(encoding="utf-8"))
            except (ValueError,OSError):pass
        meta=metadata.read_local_metadata(file)
        status,reason=classify(name,sidecar)
        if ADULT.search(meta["name"]): status,reason="nsfw","Nome adulto nos metadados locais"
        entries[name.replace("\\","/").lower()]={"status":status,"reason":reason,"name":meta["name"],"nameSource":meta["nameSource"]}
    output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({"version":1,"date":"2026-10-30","method":"Offline: nomes, pastas e sidecars rgthree/Civitai. Triagem conservadora; nao inspeciona pesos nem garante conteudo SFW.","entries":entries},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(dict(collections.Counter(x['status'] for x in entries.values())))


if __name__=="__main__":main()
