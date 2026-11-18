"""The battle sounds added in v0.2: synthesized, so they are ours.

uv run -q --with numpy python -I tools/make_battle_sounds.py app/app/src/main/assets/audio
hit (an arrow or a thrust lands), crash (a stone lands), crack (stones off the gate), fire (火烧博望),
boom (张飞's roar at the bridge: a drum boom and a low rumble), wind (借东风), wave (水淹七军),
qin (空城计: a few plucked notes on a 古琴-like string), rumble (八阵图: stones rising).
"""
import sys
import wave

import numpy as np

SR = 44100
out = sys.argv[1]
rng = np.random.default_rng(11)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack, tau):
    x = np.arange(n) / SR
    return np.minimum(1, x / max(attack, 1e-4)) * np.exp(-x / tau)


def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def band(x, lo, hi):
    return lowpass(x, hi) - lowpass(x, lo)


def noise(sec):
    return rng.standard_normal(int(SR * sec))


def fade(y, sec=0.05):
    n = min(len(y), int(SR * sec))
    y[-n:] *= np.linspace(1, 0, n)
    return y


def save(name, y, peak=0.7):
    y = y / (np.max(np.abs(y)) + 1e-9) * peak
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype(np.int16).tobytes())


# An arrow or a spear lands: a short dull thock with a little bright tick.
n = int(SR * 0.16)
y = band(noise(0.16), 200, 1400) * env(n, 0.001, 0.025)
y += np.sin(2 * np.pi * 180 * t(0.16)) * env(n, 0.001, 0.03) * 0.8
y += band(noise(0.16), 3000, 8000) * env(n, 0.0005, 0.004) * 0.4
save("hit", fade(y), 0.6)

# A stone lands: a thump, then gravel.
n = int(SR * 0.6)
y = np.sin(2 * np.pi * 70 * t(0.6) * (1 - 0.3 * t(0.6))) * env(n, 0.002, 0.09)
grit = np.zeros(n)
for k in range(28):
    s = int(SR * (0.02 + rng.exponential(0.08)))
    if s >= n - 2000:
        continue
    b = band(noise(0.02), 1500, 6000) * env(int(SR * 0.02), 0.0005, 0.004)
    grit[s:s + len(b)] += b * rng.uniform(0.2, 0.7) * np.exp(-s / SR / 0.2)
y += band(noise(0.6), 100, 900) * env(n, 0.002, 0.07) * 0.8 + grit
save("crash", fade(y), 0.75)

# Stones crack off the gate: a sharp crack and a few falling pieces.
n = int(SR * 0.7)
y = band(noise(0.7), 800, 5000) * env(n, 0.0005, 0.02) * 1.2
for k in range(6):
    s = int(SR * (0.12 + k * 0.08 + rng.uniform(0, 0.04)))
    b = band(noise(0.04), 600, 3000) * env(int(SR * 0.04), 0.001, 0.01)
    y[s:s + len(b)] += b * rng.uniform(0.3, 0.6)
save("crack", fade(y), 0.75)

# Fire: a whoosh that settles into crackling.
n = int(SR * 2.4)
x = t(2.4)
whoosh = band(noise(2.4), 150, 1800) * np.minimum(1, x / 0.25) * np.exp(-x / 1.4)
crackle = np.zeros(n)
for k in range(140):
    s = rng.integers(0, n - 800)
    crackle[s:s + 300] += rng.standard_normal(300) * env(300, 0.0002, 0.0015) * rng.uniform(0.3, 1)
y = whoosh + band(crackle, 1500, 9000) * 0.9 * np.minimum(1, x / 0.4)
save("fire", fade(y, 0.4), 0.75)

# 张飞's roar: a big drum boom under a growling low rumble.
n = int(SR * 1.6)
x = t(1.6)
boom = np.sin(2 * np.pi * 55 * x * (1 - 0.25 * x)) * env(n, 0.003, 0.35)
growl = band(noise(1.6), 60, 320) * np.minimum(1, x / 0.08) * np.exp(-x / 0.6) * (1 + 0.5 * np.sin(2 * np.pi * 28 * x))
y = boom * 1.2 + growl * 1.6
save("boom", fade(y, 0.2), 0.8)

# The east wind: rising and falling filtered noise, swept.
n = int(SR * 3.0)
x = t(3.0)
w = noise(3.0)
y = band(w, 300, 1200) * (0.4 + 0.6 * np.sin(np.pi * x / 3.0) ** 2) + band(w, 1200, 3500) * 0.35 * np.sin(np.pi * x / 3.0) ** 4
y *= np.minimum(1, x / 0.3)
save("wind", fade(y, 0.5), 0.7)

# 水淹七军: a surge of water, a roar that peaks and runs out with splashes.
n = int(SR * 2.6)
x = t(2.6)
surge = band(noise(2.6), 120, 2500) * np.sin(np.pi * np.minimum(1, x / 2.2)) ** 1.5
splash = np.zeros(n)
for k in range(40):
    s = rng.integers(int(SR * 0.6), n - 4000)
    b = band(noise(0.08), 1200, 7000) * env(int(SR * 0.08), 0.002, 0.02)
    splash[s:s + len(b)] += b * rng.uniform(0.2, 0.6)
y = surge + splash
save("wave", fade(y, 0.3), 0.75)

# 空城计: plucked notes on a low string (gong-shang-jiao-zhi-yu), with a soft slide.
notes = [196.0, 220.0, 246.9, 293.7, 329.6, 293.7, 246.9]
n = int(SR * 4.2)
y = np.zeros(n)
for k, f in enumerate(notes):
    s = int(SR * (k * 0.5 + (0.12 if k % 3 == 2 else 0)))
    m = int(SR * 1.6)
    xx = np.arange(m) / SR
    slide = f * (1 + 0.012 * np.exp(-xx / 0.15))
    ph = 2 * np.pi * np.cumsum(slide) / SR
    tone = sum(np.sin(h * ph) / h ** 1.3 for h in range(1, 7)) * env(m, 0.002, 0.55)
    end = min(n, s + m)
    y[s:end] += tone[: end - s]
save("qin", fade(y, 0.3), 0.7)

# 八阵图: heavy stones grinding up out of the ground.
n = int(SR * 2.0)
x = t(2.0)
grind = band(noise(2.0), 40, 400) * (1 + 0.6 * np.sin(2 * np.pi * 9 * x)) * np.minimum(1, x / 0.2) * np.exp(-x / 1.1)
thuds = np.zeros(n)
for k in range(8):
    s = int(SR * (0.1 + k * 0.08))
    m = int(SR * 0.25)
    thuds[s:s + m] += np.sin(2 * np.pi * 60 * np.arange(m) / SR) * env(m, 0.002, 0.06) * 0.6
y = grind * 1.3 + thuds
save("rumble", fade(y, 0.3), 0.8)
print("ok")
