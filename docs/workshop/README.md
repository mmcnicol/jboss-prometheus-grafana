# Workshop decks

Two PowerPoint decks for a team tech workshop:

| File | Audience | Use |
|---|---|---|
| `workshop-demo.pptx` | developers + tech lead | the talk track for a live demo — problem, what it is, 5 demo steps, why it stays simple, adoption |
| `technical-deep-dive.pptx` | whoever wants the detail | architecture, instrumentation internals, run labelling, spike results, decisions, adoption path, risks |

Content is **generic** (this public reference implementation). Add workplace
specifics verbally.

## Regenerate

The decks are generated from `build_decks.py` so they can be edited in one place
and kept in sync with the docs.

```bash
sudo apt-get install -y python3-pptx     # Debian / Ubuntu
python3 docs/workshop/build_decks.py     # writes the two .pptx files here
```

Then open in LibreOffice Impress or PowerPoint and adjust styling / add
screenshots from the live Grafana dashboards.
