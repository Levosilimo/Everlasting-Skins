# Real-Client E2E Master Plan — EverlastingSkins (16 lanes) — 2026-08-10

Synthesis of the 8-librarian E2E wave (lib-8..lib-15). User directive: full real-client
coverage — boot real server + real client, run /skin from client, verify packet flow
down to the renderer layer, per lane.

## Shared contract (scripts/e2e/, every era implements verbatim)

- Result file: fixed JSON at `${RUNNER_TMP}/e2e-result.json`:
  {"lane":"1.6.4","server_booted":true,"client_joined":true,"command_executed":true,
   "renderer_state":"sentinel","renderer_verified":true,"duration_ms":12345,"artifacts":{...}}
- Exit codes: 0 all green | 1 assertion failed (result file has which) | 2 retryable infra
  (client boot timeout, artifact fetch — CI retries with backoff) | 3 build failure.
- Log markers (greppable): ES_E2E_COMMAND=ok, ES_E2E_RENDERER=ok|FAIL, ES_E2E_JOIN=<uuid>.
- Artifact fetch: shared layer, `~/.cache/everlastingskins` (precedent ci.yml); pin URLs + sha.
- Renderer assertion = INJECTED-FIELD state read, NEVER pixel checks (llvmpipe).

## Era drivers (scripts/e2e/drivers/)

- pre18-xvfb.sh — 1.4.7/1.5.2/1.6.4: xvfb-run + Mesa software GL real client + in-jar
  driver (no stdin console pre-1.7.10). Driver auto-connects, receives channel byte-push,
  injects sentinel skin, asserts injected renderer-layer field state.
- headlessmc.sh — 1.7.10/1.8.9/1.10.2/1.12.2/1.16.5+: HeadlessMC wrapper (-lwjgl -offline);
  hmc-specifics console bridge enables command-driven /skin on 1.12.2 (upgrade).
- modern-injar.sh — 1.21.x/26.x: in-jar client driver (Fabric client-GameTest model):
  join -> connection.sendCommand("/skin ...") -> wait ticks -> assert
  connection.getPlayerInfo(uuid).getProfile().getProperties().get("textures") changed
  (ClientPlayNetHandler 1.16.5 / ClientPacketListener 1.18.2+; PlayerInfo.getProfile()
  version-uniform). 26.x: explicit EventBus-7 listener registration (no static
  @EventBusSubscriber), unobfuscated names, Java 25.

## Phasing

- Slice 1 (HIGHEST value, hardest): pre-1.8 real-client E2E 1.6.4 -> 1.5.2 -> 1.4.7. **DONE** (#451/#474).
  Co-designed with the in-flight joint client mod (branch feat/client-side-pre-1-8).
  Driver must OWN the render/refresh thread to defeat the default-download overwrite race.
  3 informational CI jobs (E2E (forge-1.6.4) etc.).
- Slice 2 (highest ROI/hour): enable hmc-specifics console bridge on mc1.12.2 ->
  boot-smoke -> command-driven /skin (E2E (mc1.12.2) already required). Then
  1.7.10/1.8.9/1.10.2 first real-client coverage (HeadlessMC floor 1.7.10).
  Spike first: bridge stability on 1.12.2 (JLine ClassNotFound observed on 1.21).
  **DONE** (mc1.12.2 command-driven #449/#460; 1.7.10/1.8.9/1.10.2 HeadlessMC
  E2E #438). Remaining: promote the informational e2e jobs to required via
  gh-api-bump (separate lane).
- Slice 3: 1.21.x in-jar client driver = modern template; replicate 1.21.1/4/8. **DONE** (#440).
- Slice 4: rest of matrix boot-smoke at minimum: 1.16.5/1.18.2/1.20.1 (1.20.1 =
  HMC gap 1.20.1-1.20.4 -> pre-1.8-style xvfb path); 26.1/26.2 full driver (cheap:
  unobfuscated+modern). Batch-promote informational -> gh-api-bump after green.
  **PARTIAL** — boot-smoke live (1.16.5/1.18.2/1.20.1); full driver (26.1/26.2)
  + batch-promote pending.

## CI (from lib-12)

- ubuntu-24.04: xvfb preinstalled; Mesa llvmpipe NOT -> pre-1.8 real-GL lanes need:
  sudo apt-get install -y libgl1-mesa-dri libglx-mesa0 mesa-utils libxrandr2 libxxf86vm1
  libxcursor1 libxi6 libxinerama1 libasound2 libopenal1 + LIBGL_ALWAYS_SOFTWARE=1
  GALLIUM_DRIVER=llvmpipe. HeadlessMC -lwjgl lanes need no Mesa.
- Matrix job `E2E (${{ matrix.lane }})` with per-lane needs-mesa flag; 45-min timeout;
  always() upload logs; cache keys e2e-<lane>.
- Push-only for real-client E2E (mc1.12.2 precedent); retryable exit-code 2.
- Client jars sourced SCRIPT-SIDE (run-e2e.sh curl+sha1), never build.gradle
  (vendored-harness diff-guard constraint). 147-client.jar at repo root proves URLs resolve.
- Java per era preinstalled (8/17/21/25 on 24.04); keep JAVA_HOME in script (sudo
  does not inherit setup-java env).

## Coverage matrix (final)

- Full renderer E2E: 1.4.7/1.5.2/1.6.4 (in-jar joint mod sentinel), 1.7.10/1.8.9/1.10.2
  (HeadlessMC command-driven), 1.12.2 (bridge upgrade), 1.21/1.21.1/1.21.4/1.21.8,
  26.1/26.2 (in-jar driver).
- Boot-smoke floor: 1.16.5, 1.18.2, 1.20.1.
- Modern lanes: server GameTest kept (packet emission); real-client E2E adds the
  missing client-side half.

## Open items to resolve in Slice 1

- Pin pre-1.8 CLIENT jar URLs (server jars pinned in build.gradle; client jars not yet).
- Freeze the joint mod's injected-field name as the driver's assertion read.
- Injection-race handling (driver owns render thread) is a first-class requirement
  on the in-flight joint client mod.

## Race-hardening spec (forward-looking, 2026-08-11 — lib-24 + lib-25)

Verdict: NO hardening needed today. skins.minecraft.net is NXDOMAIN (probed) — the
vanilla download thread writes the TDI image field ONLY on a successful 2xx
(1.4.7/1.5.2 putfield bar.a/bfu.a @70/96; 1.6.4 setBufferedImage -> bic.d); on any
failure it writes nothing, and the Steve default is a separate render path, never
written into the field. Race is latent; becomes REAL only under a restored
legacy-skin host (Option A / hosts redirect / LAN proxy) combined with client
injection.

Forward spec (when a client injection + restored host coexist):
- Race-removing: URL-rewrite ASM per Ears' discipline — readable class name + SRG
  method names ONLY (1.6+ LaunchWrapper runtime-deobfs notch->srg; production runs
  net.minecraft.client.renderer.ThreadDownloadImageData + func_110554_a, NOT obf
  bic/a). Never target obf constants (COMPUTE_FRAMES hazard, forum 19621). CSL's
  reflection-by-type is the obf-agnostic alternative.
- Window backstops: (a) server-side re-broadcast (only option consistent with the
  server-only architecture, trivially E2E-testable on the server log); (b) client
  re-injection guard via IScheduledTickHandler (FML 4.7/5.2/7.x ship it).
- Crux risk: the vendored harness builds/reobfs the SERVER domain only; a client
  coremod (manifest FMLCorePlugin + IFMLLoadingPlugin) needs a net-new client
  subsystem + its own E2E fixture + harness/pipeline extension (assertNameDomain
  verification exemption or client-domain build). Not a hardening of existing code.

## Race-hardening spec (forward-looking, 2026-08-11 — lib-24 + lib-25)

Verdict: NO hardening needed today. skins.minecraft.net is NXDOMAIN (probed) — the
vanilla download thread writes the TDI image field ONLY on a successful 2xx
(1.4.7/1.5.2 putfield bar.a/bfu.a @70/96; 1.6.4 setBufferedImage -> bic.d); on any
failure it writes nothing, and the Steve default is a separate render path, never
written into the field. Race is latent; becomes REAL only under a restored
legacy-skin host (Option A / hosts redirect / LAN proxy) combined with client
injection.

Forward spec (when a client injection + restored host coexist):
- Race-removing: URL-rewrite ASM per Ears' discipline — readable class name + SRG
  method names ONLY (1.6+ LaunchWrapper runtime-deobfs notch->srg; production runs
  net.minecraft.client.renderer.ThreadDownloadImageData + func_110554_a, NOT obf
  bic/a). Never target obf constants (COMPUTE_FRAMES hazard, forum 19621). CSL's
  reflection-by-type is the obf-agnostic alternative.
- Window backstops: (a) server-side re-broadcast (only option consistent with the
  server-only architecture, trivially E2E-testable on the server log); (b) client
  re-injection guard via IScheduledTickHandler (FML 4.7/5.2/7.x ship it).
- Crux risk: the vendored harness builds/reobfs the SERVER domain only; a client
  coremod (manifest FMLCorePlugin + IFMLLoadingPlugin) needs a net-new client
  subsystem + its own E2E fixture + harness/pipeline extension (assertNameDomain
  verification exemption or client-domain build). Not a hardening of existing code.
