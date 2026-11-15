"""Darts caller voice lines: each visit's total (0-180) and a few calls, in a caller's drawn-out
style: the big words stretched, the last one rising. Piper TTS (en_GB northern_english_male,
CC BY-SA 4.0) per word, joined and shaped with ffmpeg, saved as ogg for the mod.

Not part of generate_assets (it needs Piper): run it in a venv with piper-tts, the voice's
.onnx and .onnx.json in voices/ beside it, and ffmpeg (with rubberband):
    python darts_caller_voice.py ../src/main/resources/assets/ptmuk/sounds/darts
"""
import io, subprocess, sys, wave
from pathlib import Path
from piper import PiperVoice, SynthesisConfig

VOICE = PiperVoice.load(str(Path(__file__).parent / "voices/en_GB-northern_english_male-medium.onnx"))
ONES = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()


def words(n):
    if n < 20:
        return ONES[n]
    return TENS[n // 10] + ("" if n % 10 == 0 else " " + ONES[n % 10])


def say(text, slow):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        VOICE.synthesize_wav(text, w, syn_config=SynthesisConfig(length_scale=slow, noise_scale=0.5, noise_w_scale=0.6))
    return buf.getvalue()


def segments(total):
    if total == 0:
        return [("No score!", 1.3, 1.0)]
    if total == 180:
        return [("One", 1.2, 1.0), ("hundred", 2.4, 1.0), ("and", 1.1, 1.0), ("eighty!", 2.9, 1.14)]
    if total >= 100:
        rest = total - 100
        out = [("One", 1.1, 1.0), ("hundred", 1.9 + rest / 100, 1.0)]
        if rest:
            out += [("and", 1.0, 1.0), (words(rest) + "!", 1.5 + rest / 80, 1.06 + rest / 1000)]
        return out
    if total >= 60:
        return [(words(total) + "!", 1.6, 1.05)]
    return [(words(total) + ".", 1.15, 1.0)]


PHRASES = {"game_on": [("Game", 1.3, 1.0), ("on!", 1.7, 1.08)], "game_shot": [("Game", 1.3, 1.0), ("shot!", 2.0, 1.1)],
           "bust": [("Bust!", 1.6, 1.0)], "darts": [("Darts!", 1.4, 1.0)]}


def render(segs, out):
    tmp = Path(out).with_suffix("")
    files = []
    for i, (text, slow, pitch) in enumerate(segs):
        f = f"{tmp}_{i}.wav"
        Path(f).write_bytes(say(text, slow))
        if pitch != 1.0:
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", f, "-af", f"rubberband=pitch={pitch}", f + ".p.wav"], check=True)
            Path(f + ".p.wav").replace(f)
        files.append(f)
    inputs = sum([["-i", f] for f in files], [])
    # trim each word's silence, a short gap between, then louder and a touch of hall
    chain = "".join(f"[{i}:a]silenceremove=start_periods=1:start_threshold=-45dB:stop_periods=1:stop_threshold=-45dB:stop_duration=0.08,"
                    f"apad=pad_dur=0.06[a{i}];" for i in range(len(files)))
    chain += "".join(f"[a{i}]" for i in range(len(files))) + f"concat=n={len(files)}:v=0:a=1,loudnorm=I=-14,aecho=0.8:0.5:60:0.15[out]"
    cmd = ["ffmpeg", "-loglevel", "error", "-y", *inputs, "-filter_complex", chain, "-map", "[out]", "-ac", "1",
           "-ar", "22050", "-c:a", "libvorbis", "-q:a", "3", out]
    try:
        subprocess.run(cmd, check=True, timeout=60)
    except subprocess.TimeoutExpired:                   # (loudnorm has stuck on the odd line: a plain gain instead)
        cmd[cmd.index("-filter_complex") + 1] = chain.replace("loudnorm=I=-14", "volume=6dB")
        subprocess.run(cmd, check=True, timeout=60)
    for f in files:
        Path(f).unlink()


if __name__ == "__main__":
    outdir = Path(sys.argv[1])
    outdir.mkdir(parents=True, exist_ok=True)
    which = sys.argv[2:] or [str(n) for n in range(181)] + list(PHRASES)
    for k in which:
        segs = PHRASES[k] if k in PHRASES else segments(int(k))
        out = outdir / (f"score_{k}.ogg" if k.isdigit() else f"{k}.ogg")
        if not out.exists():                            # (already made: kept)
            render(segs, str(out))
    print("done", len(which))
