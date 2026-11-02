# 素材来源与授权

| 素材 | 来源 | 授权 | 用在哪里 |
|---|---|---|---|
| 诸葛亮、张飞、关羽、周瑜，弓兵、枪兵、连弩、投石车，步兵、骑兵、冲车的 3D 模型 | 先用火山方舟 Seedream 5.0 生成 Q 版参考图（提示词见 `../../assets_lab/sanguo/prompts.tsv`），再用 Doubao-Seed3D 2.0 转成 3D，由 `../../tools/blender/prep_sanguo.sh`（`prep_ai.py`）减到 0.4～0.9 万面、贴图 512～1024 | AI 生成，本项目自有 | 城楼上的将领、我军和敌军 |
| 沙盘、城墙、城门、道路、江水、田地、战船、拒马 | 代码里用基本几何体搭建（`render/BoardView.kt`） | 本项目自有 | 沙盘 |
| 应用图标 | 诸葛亮参考图用 `tools/cutout.py` 抠图，`tools/make_icon2d.py` 合成在暖红色圆盘上（源图在 `../../assets_lab/sanguo/icon/`） | 本项目自有 | 图标 |
| 音效（放兵、升级、错误、出战鼓、计策、胜利、失败、放箭、投石、化烟、撞门） | Kenney「RPG Audio」「Interface Sounds」「Impact Sounds」（kenney.nl） | CC0 | 用 ffmpeg 转单声道，峰值统一到 -1 dB |
| 背景音乐《War Theme》（spring-spring） | OpenGameArt（opengameart.org/content/war-theme） | CC0 | 循环背景音乐，响度统一到 -20 LUFS；另 2 首 CC0 备选在 `docs/音乐候选/`：《Massive Battle》（Eldritch-Grim）、《Oriental》（shadowfire452） |

人物形象均为本作原创的 Q 版造型。敌兵被击退时化成一团烟或逃走，不表现伤亡和流血。
战役志里的“演义原文”引自《三国演义》（公有领域），“史书怎么说”依据《三国志》等史书概括。
