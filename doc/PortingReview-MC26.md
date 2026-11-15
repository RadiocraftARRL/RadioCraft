# Porting Review: RadioCraft (1.21.1 → Minecraft 26 / NeoForge)

This note reviews the RadioCraft codebase on NeoForge 1.21.1 (`21.1.248`) to identify what will be hardest to port to the latest **Minecraft 26** full release (Mojang's post-1.21 `year.drop` versioning, e.g., `26.1`) on NeoForge.

Between **1.21.1** and **Minecraft 26** (spanning the 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6–1.21.8 drops and 26.x), Mojang and NeoForge shipped the largest client rendering, model, GUI, and persistence overhauls since 1.13/1.15. RadioCraft's core simulation math (propagation, DSP, FT8/AFSK/RTTY/WEFAX audio synthesis, solar weather, DOS apps) is pure Java and will port almost untouched, **with one major exception**: widespread direct OpenGL/framebuffer manipulation, custom model baking, custom item/entity/block-entity renderers, `GuiGraphics` screen scaling, and raw `CompoundTag` / `SavedData` persistence.

> **Note on certainty:** Changes introduced in **1.21.2 through 1.21.6+** are known with high certainty and all accumulate into MC 26. Where exact MC 26 / NeoForge 26.x class or event names may have evolved further after 1.21.6, this review explicitly flags them rather than guessing.

---

## Ranked Porting Areas (Hardest First)

### 1. Direct OpenGL, Offscreen Framebuffers (`RenderTarget`), and Custom World/Sky Shaders

#### What the code does today
RadioCraft uses raw LWJGL `GL11`/`GL30`, `GlStateManager`, `RenderSystem`, and `TextureTarget` to render offscreen CRT monitors, live world viewports inside computer screens, and in-game camera photographs, plus custom `Tesselator` geometry in the sky:
- **Offscreen DOS CRT rendering (`MonitorMirror`)**: `src/main/java/com/arrl/radiocraft/client/render/MonitorMirror.java` allocates a 760×480 `TextureTarget(width, height, false, Minecraft.ON_OSX)` per active computer monitor. During `RenderFrameEvent.Pre`, `MonitorMirror.redraw()` saves and replaces `RenderSystem.getProjectionMatrix()`, `RenderSystem.getModelViewStack()`, and fog start, calls `target.bindWrite(true)`, clears the framebuffer, constructs a dummy `GuiGraphics`, draws `DosRenderer.renderContent(...)` into the FBO, and binds the FBO's color texture via `RenderSystem.setShaderTexture(0, target.getColorTextureId())` when `ComputerMonitorRenderer` draws the block in-world.
- **World-in-Monitor Blitting (`ModerationDesk` & `ComputerSession`)**: `src/main/java/com/arrl/radiocraft/client/screens/moderation/ModerationDesk.java` and `src/main/java/com/arrl/radiocraft/client/screens/computer/ComputerSession.java` render a live 3D world view into an offscreen `TextureTarget` during `RenderLevelStageEvent.Stage.AFTER_LEVEL` by directly calling:
  - `GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId)`
  - `GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, monitor.frameBufferId)`
  - `GlStateManager._glBlitFrameBuffer(..., GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)`
  - `RenderSystem.colorMask(false, false, false, true)` and `RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, ...)` to force alpha = 1.0
  - A custom `AbstractTexture` subclass (`MonitorTexture`) that assigns `this.id = monitor.getColorTextureId()` directly so `GuiGraphics.blit(...)` can sample the FBO by a synthetic `ResourceLocation`.
- **Camera Photo Readback (`PhotoCapture` & `CameraClient`)**: `src/main/java/com/arrl/radiocraft/client/photo/PhotoCapture.java` and `CameraClient.java` allocate a 128×128 `TextureTarget`, blit the center square of `Minecraft.getInstance().getMainRenderTarget()` into it using `main.blitToScreen(...)`, bind `target.getColorTextureId()`, and call `NativeImage.downloadTexture(0, false)` to quantize pixels to map colors on the client.
- **Sky & World Geometry (`AuroraRenderer`, `SatelliteSkyRenderer`, `CordRenderer`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/AuroraRenderer.java` and `SatelliteSkyRenderer.java` hook `RenderLevelStageEvent.Stage.AFTER_SKY`, call `RenderSystem.enableBlend()`, `RenderSystem.depthMask(false)`, `FogRenderer.setupNoFog()`, `RenderSystem.setShader(GameRenderer::getPositionColorShader)`, and draw `Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)` with `BufferUploader.drawWithShader(mesh)`.
  - `src/main/java/com/arrl/radiocraft/client/render/CordRenderer.java` hooks `RenderLevelStageEvent.Stage.AFTER_ENTITIES` to draw catenary cords between players and blocks using `RenderType.leash()`.

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.5+ Blaze3D `GpuDevice` / `CommandEncoder` / `GpuTexture` Overhaul**:
   - Mojang replaced raw OpenGL state management in Blaze3D with an explicit GPU backend abstraction (`RenderSystem.getDevice()`, `GpuDevice`, `CommandEncoder`, `RenderPass`, `GpuTexture`, `GpuBuffer`, `RenderPipeline`).
   - `RenderTarget.frameBufferId` and `RenderTarget.getColorTextureId()` were replaced with `GpuTexture` handles (`getColorTexture()`, `getDepthTexture()`).
   - Direct `GlStateManager._glBindFramebuffer` and `GL30.GL_READ_FRAMEBUFFER` / `_glBlitFrameBuffer` calls bypass or conflict with `CommandEncoder` state tracking (and raw GL IDs are no longer exposed on `AbstractTexture.id` in the same way; `AbstractTexture` now holds a `GpuTexture`).
   - `CommandEncoder.copyTextureToTexture(...)` and `CommandEncoder.copyTextureToBuffer(...)` (with async callback or mapped buffer readback) replace raw FBO blits and `NativeImage.downloadTexture(...)`.
   - `RenderSystem.colorMask`, `RenderSystem.enableBlend`, `RenderSystem.depthMask`, `RenderSystem.disableCull`, etc. were moved out of imperative `RenderSystem` calls into declarative `RenderPipeline` definitions (1.21.5+).
2. **1.21.2+ Shader & Fog Refactor**:
   - `GameRenderer::getPositionColorShader` and `GameRenderer::getPositionTexShader` were replaced by `CoreShaders.POSITION_COLOR`, `CoreShaders.POSITION_TEX`, etc. (1.21.2), and subsequently folded into `RenderPipeline` objects (`RenderPipelines`) in 1.21.5+.
   - `FogRenderer.setupNoFog()` was replaced with `FogParameters.NO_FOG` / `RenderSystem.setShaderFog(...)`.
3. **MC 26 Uncertainty**:
   - In 1.21.6–26.x, `RenderLevelStageEvent` stages and how NeoForge exposes custom `RenderPass` / `RenderPipeline` registration continued to evolve. Verify NeoForge 26's exact replacement for `RenderLevelStageEvent` and offscreen `GuiGraphics` rendering before rewriting `MonitorMirror`.

#### Rough effort
**High (3–5 days).** Small file count (7 files), but requires deep familiarity with the 1.21.5+ `GpuDevice` / `CommandEncoder` / `RenderPipeline` architecture.

#### How to reduce risk
- **Create a single `OffscreenFramebuffers` helper class now**: Move the duplicate FBO blit + `MonitorTexture` code out of `ModerationDesk.java` and `ComputerSession.java` (they are nearly identical 90-line inner classes today) and unify them with `PhotoCapture.java`.
- **Fallback option for `MonitorMirror`**: If rendering `GuiGraphics` into an offscreen `TextureTarget` during `RenderFrameEvent.Pre` proves fragile in MC 26's new GUI renderer, `DosRenderer` can either render directly in `ComputerMonitorRenderer` using 3D `MultiBufferSource` text/quads (like `MeshScreenRenderer` and `AdsbScopeRenderer` already do) or rasterize the 80×25 character grid to a `DynamicTexture` (`NativeImage`), completely eliminating offscreen `GuiGraphics` FBO passes.

---

### 2. Custom Item Models, `BlockEntityWithoutLevelRenderer` (BEWLR), Item Properties, and Dynamic Block Models

#### What the code does today
- **Dynamic Handheld Item Rendering (`HandheldItemRenderer` + `ModelEvent.ModifyBakingResult`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/item/HandheldItemRenderer.java` extends `BlockEntityWithoutLevelRenderer`.
  - In `ClientSetupEvents.onModifyBakingResult`, the mod intercepts baked models for 9 handheld items (`vhf_handheld` ×3 crystal qualities, `basic_commercial_handheld`, `commercial_handheld`, `marine_handheld`, `cb_portable`, `handheld_direction_finder`, `antenna_analyzer`) and wraps them in a `BakedModelWrapper` whose `isCustomRenderer()` returns `true`, while caching the unwrapped `BakedModel` in `HandheldItemRenderer.PLAIN_MODELS`.
  - All 9 items register `IClientItemExtensions.getCustomRenderer()` in `ClientSetupEvents.registerClientExtensions(...)`.
  - At render time, `HandheldItemRenderer.renderByItem(...)` renders the cached base `BakedModel`, overlays `models/item/vhf_handheld_rx_led` at fullbright when receiving, and for first/third-person views draws a live `HandheldLcdTexture` (`DynamicTexture`) or compass needle onto the item's screen face.
- **GeckoLib Item & Block-Item Renderers**:
  - `src/main/java/com/arrl/radiocraft/common/items/DeskChargerBlockItem.java`, `SatelliteDishBlockItem.java`, and `src/main/java/com/arrl/radiocraft/client/render/item/DevItemRenderer.java` use GeckoLib's `GeoItemRenderer` via `IClientItemExtensions.getCustomRenderer()`.
- **Legacy Item Model Predicates (`ItemProperties`)**:
  - `src/main/java/com/arrl/radiocraft/client/events/ClientSetupEvents.java` (lines 118–132) registers `ItemProperties.register(...)` for `handheld_direction_finder` (`lit`), `cb_portable` (`extended`), and `distress_beacon` (`active`), referenced via `"overrides"` in `src/main/resources/assets/radiocraft/models/item/*.json`.
- **Item Color Handlers (`RegisterColorHandlersEvent.Item`)**:
  - `ClientSetupEvents.registerItemColors(...)` registers dynamic tinting for `tape_reel`, `basic_commercial_handheld`, `commercial_handheld`, `commercial_mobile`, `low_band_mobile`, and all `FLOPPIES`.
- **Custom Block Geometry Loaders (`WireModel` & `TowerBeamModel`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/model/WireModel.java` (`radiocraft:wire`) and `TowerBeamModel.java` (`radiocraft:tower_beam`) implement `IUnbakedGeometry` and `IDynamicBakedModel`.
  - Both call `context.getModel(...)` and `baker.bake(...)`, and directly mutate raw `BakedQuad.getVertices()` `int[]` arrays using `IQuadTransformer.STRIDE` (8 ints per vertex: XYZ bits, packed color, UV, lightmap, normal) to shift/cull faces or inject per-cable dye tints from `ModelData` (`WireBundleBlockEntity.MODEL_PROPERTY`).
- **DynamicTextures on Items/Entities (`HandheldLcdTexture`, `AprsMapTexture`, `QslTextures`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/item/HandheldLcdTexture.java`, `AprsMapTexture.java`, and `src/main/java/com/arrl/radiocraft/client/render/QslTextures.java` create `DynamicTexture` instances, draw into `getPixels()` (`NativeImage`), call `upload()`, and register them with `Minecraft.getInstance().getTextureManager().register(...)`.

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.4 Client Item Model Overhaul (`assets/radiocraft/items/*.json`)**:
   - Every item now requires a client item definition file at `assets/radiocraft/items/<item_name>.json` (separate from `models/item/<item_name>.json`). With ~150+ registered items in `RadiocraftItems.java` (including wood variants for faxes and desk speakers, 16 dyed wires/coax/data wires, crystal band/quality matrices, and floppies), all need client item JSONs generated or written.
   - `ItemProperties` and model JSON `"overrides"` were **removed** in 1.21.4. They are replaced by `ConditionalItemModelProperty` (`BooleanProperty`, registered via `RegisterConditionalItemModelPropertyEvent`) for `lit`, `extended`, and `active`, wired up via `"type": "minecraft:condition"` in `assets/radiocraft/items/*.json`.
   - `RegisterColorHandlersEvent.Item` (`ItemColor`) was **removed** in 1.21.4. Item tinting is now data-driven via `ItemTintSource` codecs (`RegisterColorHandlersEvent.ItemTintSources`) referenced in the `tints` array of `assets/radiocraft/items/<item>.json`.
2. **1.21.4 Removal of `BakedModel.isCustomRenderer()` and `BlockEntityWithoutLevelRenderer`**:
   - `BakedModel.isCustomRenderer()`, `IClientItemExtensions.getCustomRenderer()`, and `BlockEntityWithoutLevelRenderer` were **removed** in 1.21.4.
   - They are replaced by `SpecialModelRenderer<T>` (`SpecialModelRenderer.Unbaked`, registered via `RegisterSpecialModelRendererEvent` or NeoForge's special model renderer hooks).
   - Crucially for `HandheldItemRenderer`: in 1.21.4+, a `composite` item model in `assets/radiocraft/items/<handheld>.json` can render the normal JSON `minecraft:model` **and** a `minecraft:special` overlay model together! That completely eliminates the fragile `ModelEvent.ModifyBakingResult` + `BakedModelWrapper` + `PLAIN_MODELS` hack.
3. **1.21.5+ Block Model & `BakedModel` Refactor**:
   - In 1.21.5, Mojang split `BakedModel` into `BlockStateModel` (for blocks) and `ItemModel` (for items), refactored `UnbakedModel` / `ModelBaker`, and changed how geometry loaders (`IUnbakedGeometry` / `BlockStateModel.Unbaked`) bake quads and expose `ModelData`-dependent quads.
   - `ModelEvent.RegisterAdditional` (used for `ManpackLayer.MODEL`, `WeatherStationRenderer.VANE`/`CUPS`, `TapePlayerRenderer.REEL`, `HandheldItemRenderer.RX_LED`) changed from `ModelResourceLocation.standalone(...)` to `ResourceLocation` / standalone model keys in 1.21.4–1.21.5.

#### Rough effort
**High (3–4 days).** Converting `HandheldItemRenderer` to `SpecialModelRenderer`, migrating `WireModel` and `TowerBeamModel` to the 1.21.5+ `BlockStateModel` geometry loader API, and adding/generating `assets/radiocraft/items/*.json` for ~150+ items.

#### How to reduce risk
- **Add an Item Model / Client Item Datagen Provider**: Currently `RadiocraftItemModelProvider.java` is an empty stub (all `models/item/*.json` are hand-maintained or generated by Python scripts in `tools/`). Before or during the port, generate `assets/radiocraft/items/<id>.json` automatically for all `RadiocraftItems.ITEMS.getEntries()`, with special cases only for the 9 handhelds, 3 conditional items, and tinted items.
- **Isolate quad vertex mutation in `WireModel` and `TowerBeamModel`**: Both classes duplicate the `STRIDE = IQuadTransformer.STRIDE` vertex array loop. Extract a shared `QuadMutator` utility so if `BakedQuad` vertex layout or immutability changes in MC 26, only one method needs updating.

---

### 3. Entity Renderers (`EntityRenderState`), Player Layers, BlockEntity Renderers, and Custom Armor (`FccJacketModel`)

#### What the code does today
- **Entity Renderer (`AntennaWireEntityRenderer`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/entity/AntennaWireEntityRenderer.java` extends `EntityRenderer<AntennaWire>`.
  - In `shouldRender` and `render(AntennaWire wire, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)`, it directly accesses `wire.level()`, `wire.getWireHolder()` (a `Player`), and `wire.getEndPart()` (`AntennaWirePart`), calling `getBlockLightLevel` and `level.getBrightness(LightLayer.SKY, ...)` at both endpoints and rendering a 24-segment catenary strip with `RenderType.leash()`.
- **Player Render Layers (`ManpackLayer`, `PhoneEarLayer`, `HandheldBeltRenderer`)**:
  - `src/main/java/com/arrl/radiocraft/client/render/ManpackLayer.java` and `PhoneEarLayer.java` extend `RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>` and are attached in `ClientSetupEvents.addPlayerLayers` iterating `event.getSkins()`.
  - Inside `render(...)`, they inspect the live `AbstractClientPlayer` entity (`ManpackItem.findWorn(player)`, `CuriosCompat.wornCbPortables(player)`, `PhoneHandset.heldBy(player)`), query `Minecraft.getInstance().getModelManager().getModel(MODEL)`, and render baked quads or `ItemInHandRenderer.renderItem(...)`.
- **Custom Armor Model & Material (`FccJacketModel` & `RadiocraftArmorMaterials`)**:
  - `src/main/java/com/arrl/radiocraft/common/init/RadiocraftArmorMaterials.java` registers `fcc_jacket` in `Registries.ARMOR_MATERIAL`.
  - `src/main/java/com/arrl/radiocraft/common/items/FccJacketItem.java` extends `ArmorItem`.
  - `src/main/java/com/arrl/radiocraft/client/render/FccJacketModel.java` extends `HumanoidModel<LivingEntity>` and is wired up via `IClientItemExtensions.getHumanoidArmorModel(...)` in `ClientSetupEvents.java`.
- **16 BlockEntity Renderers (BERs) & ItemFrame Event**:
  - `src/main/java/com/arrl/radiocraft/client/render/blockentity/*`: `RadioDisplayRenderer`, `CbRadioRenderer`, `VHFReceiverRenderer`, `ComputerMonitorRenderer`, `ModerationComputerRenderer`, `AdsbScopeRenderer`, `WeatherStationRenderer`, `AutoTunerDisplayRenderer`, `BatteryMeterRenderer`, `FieldPhoneRenderer`, `MeshScreenRenderer`, `MicrowaveHornRenderer`, `PassthroughRenderer`, `TapePlayerRenderer`, `TelephoneRenderer`, plus GeckoLib's `DeskChargerBlockRenderer` and `SatelliteDishRenderer`, and Create's `AlternatorRenderer`.
  - `PassthroughRenderer` and `MicrowaveHornRenderer` call `BlockRenderDispatcher.getModelRenderer().tesselateBlock(...)` and `renderModel(...)` iterating `model.getRenderTypes(...)`.
  - `QslCardRenderer` hooks `RenderItemInFrameEvent` to draw custom QSL card layouts and `Font.drawInBatch(...)` on item frames.

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.2+ `EntityRenderState` Architecture**:
   - `EntityRenderer<T>` became `EntityRenderer<T, S extends EntityRenderState>`.
   - `render(...)` no longer receives the `Entity`! Instead, `createRenderState()` creates an `S`, `extractRenderState(T entity, S state, float partialTick)` copies all needed data from the entity/level into `S`, and `render(S state, PoseStack, MultiBufferSource, int)` renders strictly from `S`.
   - `AntennaWireEntityRenderer` must define an `AntennaWireRenderState extends EntityRenderState` holding the start position, end position (resolved from either the holding `Player`'s hand or `endPart`), and start/end block & sky light values, extracted in `extractRenderState`. (Note: vanilla leash rendering itself was moved into `EntityRenderState.leashState` in 1.21.2+.)
2. **1.21.2+ `PlayerRenderState` in `RenderLayer`**:
   - Player render layers now extend `RenderLayer<PlayerRenderState, PlayerModel>` and receive `PlayerRenderState` instead of `AbstractClientPlayer`.
   - Because `PlayerRenderState` only holds vanilla fields by default, `ManpackLayer` and `PhoneEarLayer` cannot call `ManpackItem.findWorn(player)` or `PhoneHandset.heldBy(player)` inside `render(...)`. They must use NeoForge's render state extension API (`RegisterRenderStateModifiersEvent` + `ContextKey<T>` on `EntityRenderState`) to extract the worn manpack/CB stack, whip extension, and held handset state during `extractRenderState` and read it back in `render`.
3. **1.21.2–1.21.4 Equipment / Armor Overhaul**:
   - `Registries.ARMOR_MATERIAL` was removed/replaced in 1.21.2+ by data-driven `EquipmentAsset` (`assets/radiocraft/equipment/fcc_jacket.json`) and the `DataComponents.EQUIPPABLE` component (`Equippable.builder(EquipmentSlot.CHEST)...`).
   - `ArmorItem` was removed/flattened into `Item` properties methods (`.humanoidArmor(material, ArmorType.CHESTPLATE)`).
   - `FccJacketModel` (`HumanoidModel<LivingEntity>`) now parameterizes on `HumanoidRenderState` (`HumanoidModel<HumanoidRenderState>`), and `IClientItemExtensions.getHumanoidArmorModel` signature changed to take `HumanoidRenderState` and `EquipmentClientInfo.LayerType`.
4. **BlockEntityRenderer & VertexConsumer API changes**:
   - In 1.21.5+, `BlockRenderDispatcher.getModelRenderer().tesselateBlock(...)` / `renderModel(...)` signatures changed to take `BlockStateModel` (or `List<BlockModelPart>`) instead of `BakedModel`, hitting `PassthroughRenderer`, `MicrowaveHornRenderer`, `WeatherStationRenderer`, `TapePlayerRenderer`, and `ManpackLayer`.
   - **MC 26 Uncertainty**: Check whether MC 26 / NeoForge 26 also transitioned `BlockEntityRenderer<T>` to a `BlockEntityRenderState` pattern or added a `Vec3 cameraPos` parameter to `BlockEntityRenderer.render(...)` (added in 1.21.5). In 1.21.5, `BlockEntityRenderer.render` gained a 7th parameter (`Vec3 cameraPos`), which hits all 16 BERs in RadioCraft mechanically.

#### Rough effort
**High (3–4 days).** 22+ renderer classes touched; `AntennaWireEntityRenderer`, `ManpackLayer`, `PhoneEarLayer`, and `FccJacketModel` require structural refactoring for `EntityRenderState`/`PlayerRenderState`, while the 16 BERs require signature and `BlockModelRenderer` updates.

#### How to reduce risk
- **Pre-extract state records in `AntennaWireEntityRenderer`, `ManpackLayer`, and `PhoneEarLayer`**: Even on 1.21.1, you can refactor `ManpackLayer` and `PhoneEarLayer` so a small `extract(AbstractClientPlayer, float partialTick)` method produces a plain record (`WornPackState`, `HeldPhoneState`) and `render(...)` only reads that record. In MC 26, that record goes straight into a `ContextKey` on `PlayerRenderState`.
- **Centralize baked sub-model rendering**: `ManpackLayer`, `WeatherStationRenderer`, and `TapePlayerRenderer` each have their own `renderBakedModel` helper iterating `Direction.values()` and calling `putBulkData`. Consolidate them into one client helper method.

---

### 4. NBT (`CompoundTag`) Optional Returns, `ValueInput`/`ValueOutput` Serialization, and `SavedData` Codecs

#### What the code does today
- **19 `SavedData` Subclasses**:
  - `PlayerCallsignSavedData`, `BlockEntityCallsignSavedData`, `AdvancementData`, `DataWireMemory`, `ComputerFileStore`, `FloppyFileStore`, `LicenseProgress`, `ContestData`, `FccBadgeHolders`, `PhotoLedger`, `CommercialStations`, `CommercialLicenses`, `FireAlarms`, `RadioMesh`, `MicrowaveIdentities`, `Satellites`, `Scada`, `TerrainAtlas`, `RealWorldLicences`.
  - Every one of them uses `level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(MyData::new, MyData::load), DATA_NAME)` and implements `save(CompoundTag, HolderLookup.Provider)` and `load(CompoundTag, HolderLookup.Provider)` using imperative `CompoundTag` reads/writes.
  - Note: `PlayerCallsignSavedData` and `BlockEntityCallsignSavedData` store their maps in `static final HashMap` fields instead of instance fields (a latent bug across multiple worlds in one session that will also complicate codec serialization).
- **~80 `BlockEntity` Classes & `AntennaWire` Entity**:
  - All block entities in `src/main/java/com/arrl/radiocraft/common/blockentities/**` (including the 2,590-line `RadioBlockEntity.java`) override `saveAdditional(CompoundTag, HolderLookup.Provider)`, `loadAdditional(CompoundTag, HolderLookup.Provider)`, and `getUpdateTag(HolderLookup.Provider)`.
  - Heavy use of `nbt.getInt(...)`, `nbt.getString(...)`, `nbt.getBoolean(...)`, `nbt.getFloat(...)`, `nbt.getDouble(...)`, `nbt.getCompound(...)`, `nbt.getList(key, Tag.TAG_COMPOUND)`, `nbt.getLongArray(...)`, `nbt.getIntArray(...)`, `nbt.putUUID(...)` / `nbt.getUUID(...)` / `nbt.hasUUID(...)`, and `nbt.getAllKeys()`.
  - Worldgen (`SitePiece.java`, `SiteProcessor.java`, `RotateBlocksProcessor.java`) and items/packets also manipulate `CompoundTag` directly.

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.5 `CompoundTag` Overhaul (Optional Return Types & UUID Removal)**:
   - In 1.21.5, Mojang changed **every** `CompoundTag` getter (`getInt`, `getLong`, `getFloat`, `getDouble`, `getBoolean`, `getString`, `getCompound`, `getList`, `getByteArray`, `getIntArray`, `getLongArray`) to return `Optional<T>` instead of primitive defaults, and added `getIntOr(key, defaultValue)`, `getStringOr(key, "")`, `getBooleanOr(key, false)`, `getCompoundOrEmpty(key)`, `getListOrEmpty(key)` (note: the 2-arg `getList(String, int)` taking a tag type ID was removed because `ListTag` became heterogeneous!).
   - `CompoundTag.putUUID`, `getUUID`, and `hasUUID` were **removed** in 1.21.5! UUIDs on `CompoundTag` now use `nbt.store("key", UUIDUtil.CODEC, uuid)` and `nbt.read("key", UUIDUtil.CODEC)`.
   - `CompoundTag.getAllKeys()` was renamed to `CompoundTag.keySet()`.
2. **1.21.5 `SavedData` Overhaul (`SavedDataType` + `Codec`)**:
   - `SavedData.Factory<T>` and `SavedData.save(CompoundTag, HolderLookup.Provider)` were replaced in 1.21.5 by `SavedDataType<T>`, which takes a `Codec<T>` (or `Function<SavedData.Context, Codec<T>>`).
   - All 19 `SavedData` classes must either define a `Codec<T>` or wrap their NBT serialization in `CompoundTag.CODEC.xmap(...)`.
3. **1.21.6+ `ValueInput` / `ValueOutput` Refactor for `BlockEntity` and `Entity`**:
   - In 1.21.6 (and carried into MC 26), Mojang replaced `CompoundTag` and `HolderLookup.Provider` in `BlockEntity.saveAdditional` / `loadAdditional` and `Entity.readAdditionalSaveData` / `addAdditionalSaveData` with `ValueOutput` and `ValueInput`!
   - `saveAdditional(ValueOutput output)` and `loadAdditional(ValueInput input)` use `output.putInt(...)`, `output.putString(...)`, `output.store(key, codec, value)`, and `input.getIntOr(...)`, `input.getStringOr(...)`, `input.read(key, codec)`, `input.childrenList(...)`.

#### Rough effort
**High in volume, Low-to-Medium in complexity (3–4 days).** It touches ~100 files across `common/blockentities`, `common/entities`, `common/data`, `common/radio`, `common/computer`, and `common/worldgen`, though the changes are mechanical and well-covered by the existing GameTest suite.

#### How to reduce risk
- **For the 19 `SavedData` classes**: Don't hand-write complex `RecordCodecBuilder` schemas for all 19 on day one if you want to preserve exact save compatibility and move fast—use `CompoundTag.CODEC.xmap(tag -> MySavedData.load(tag), MySavedData::save)` inside `SavedDataType` first, then migrate individual `SavedData` classes to native codecs at your leisure.
- **Fix `PlayerCallsignSavedData` and `BlockEntityCallsignSavedData` static maps first**: Move their `static final HashMap` fields to instance fields on the `SavedData` object before porting to `SavedDataType`.
- **Use `RadioBlockEntity.setupSaveTag` / `readSaveTag` seam**: Because all radio block entities already funnel their persistence and client sync through `setupSaveTag` and `readSaveTag` in `RadioBlockEntity.java`, updating `RadioBlockEntity` and its 14 subclasses to `ValueOutput`/`ValueInput` is already nicely centralized.

---

### 5. GUI Screens (`GuiGraphics`), Custom Scaled Panels, Input Events, and Keybinds

#### What the code does today
- **37 Custom Screen / GUI Classes** in `src/main/java/com/arrl/radiocraft/client/screens/**`:
  - Radio screens: `RadioScreen`, `HfPanelScreen`, `HFRadioScreen`, `HFRadio10mScreen`, `HFRadio20mScreen`, `HFRadio40mScreen`, `HFRadio80mScreen`, `AllBandRadioScreen`, `HFReceiverScreen`, `QRPRadio20mScreen`, `QRPRadio40mScreen`, `VHFBaseStationScreen`, `VHFReceiverScreen`, `VHFRepeaterScreen`, `CbRadioScreen`, `MarineRadioScreen`, `CommercialMobileScreen`, `RealWorldInterfaceScreen`.
  - Handheld & item overlays/screens: `VHFHandheldScreen`, `HandheldFrequencyInput`, `CommercialHandheldOverlay`, `MarineHandheldOverlay`, `CbPortableOverlay`, `HandheldDfOverlay`, `ManpackScreen`, `AntennaAnalyzerOverlay`, `BandPlanScreen`, `CallsignClaimScreen`, `CommercialProgrammerScreen`, `QslCardScreen`, `QslAlbumScreen`, `CameraOverlay`.
  - Station & computer screens: `StationPanelScreen` (and 10 subclasses: `AprsTerminalScreen`, `RadioFaxScreen`, `TeletypeScreen`, `DirectionFinderScreen`, `MicrowaveRadioScreen`, `TelephoneScreen`, `WefaxReceiverScreen`, `IonosphericProcessorScreen`, `DtmfEncoderScreen`, `DtmfDecoderScreen`), `RadioComputerScreen`, `ModerationComputerScreen`, `DosRenderer`, `Crt`, `FieldPhoneScreen`, `MeshDeviceScreen`, `FireAlarmScreen`, `ScadaMasterScreen`, `AntennaTunerScreen`, `DuplexerScreen`, `ChargeControllerScreen`, `LargeBatteryScreen`, `SolarPanelScreen`, `DeskChargerScreen`, `ParrotControllerScreen`.
- **Custom Coordinate Scaling**:
  - `RadioComputerScreen`, `ModerationComputerScreen`, `StationPanelScreen`, and `RadioScreen.drawScaled` push `pGuiGraphics.pose()`, call `pose().scale(s, s, 1.0F)` or `pose().translate(x, y, 0)`, and override `mouseClicked`, `mouseReleased`, `mouseDragged`, `mouseScrolled`, and `render` by dividing mouse coordinates by `scale`.
- **Legacy Shader Calls Inside Screens**:
  - `src/main/java/com/arrl/radiocraft/client/screens/radios/RadioScreen.java` (lines 72–73) still calls `RenderSystem.setShader(GameRenderer::getPositionTexShader)` and `RenderSystem.setShaderTexture(0, texture)` before `pGuiGraphics.blit(...)`.
  - `src/main/java/com/arrl/radiocraft/client/screens/widgets/Dial.java` and `ToggleButton.java` also call `RenderSystem.setShader(...)` and `RenderSystem.setShaderTexture(...)` before `pGuiGraphics.blit(...)`.
- **KeyMappings & Scroll Input**:
  - `src/main/java/com/arrl/radiocraft/client/init/RadiocraftKeys.java` registers `KeyMapping` instances with `KeyConflictContext.UNIVERSAL` / `IN_GAME`.
  - `src/main/java/com/arrl/radiocraft/client/events/ScrollTuning.java` hooks `InputEvent.MouseScrollingEvent` (`event.getScrollDeltaY()`).

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.2+ `GuiGraphics.blit` Signature Changes**:
   - In 1.21.2, `GuiGraphics.blit(...)` and `blitSprite(...)` gained a mandatory first parameter: `Function<ResourceLocation, RenderType>` (specifically `RenderType::guiTextured`)—or in 1.21.5+, `RenderPipeline` (`RenderPipelines.GUI_TEXTURED`).
   - All `RenderSystem.setShader(GameRenderer::getPositionTexShader)` and `RenderSystem.setShaderTexture(0, ...)` calls in `RadioScreen`, `Dial`, `ToggleButton`, etc. must be deleted (they are redundant or harmful in 1.21.2+ and do not compile in 1.21.5+).
2. **1.21.6+ / MC 26 2D GUI Rendering Pipeline (`Matrix3x2fStack`)**:
   - In 1.21.6+ (and MC 26), Mojang reworked `GuiGraphics` to use a 2D renderer backed by JOML's `Matrix3x2fStack` instead of a 3D `PoseStack`!
   - `guiGraphics.pose().pushPose()` / `popPose()` became `guiGraphics.pose().pushMatrix()` / `popMatrix()`.
   - `guiGraphics.pose().translate(x, y, z)` (3 arguments) became 2D `guiGraphics.pose().translate(x, y)` (2 arguments), and `scale(sx, sy, 1.0F)` became `scale(sx, sy)`.
   - Every screen and widget in `client/screens/**` that calls `pose().pushPose()`, `translate(x, y, 0)`, or `scale(s, s, 1.0F)` (`RadioScreen`, `HfPanelScreen`, `DosRenderer`, `RadioComputerScreen`, `ModerationComputerScreen`, `StationPanelScreen`, `Crt`, etc.) will need updating to the 2-argument 2D matrix methods.
3. **MC 26 Uncertainty**:
   - Verify whether any `AbstractWidget` or `Screen` mouse/keyboard event signatures changed further in 26.x.

#### Rough effort
**Medium (2 days).** ~37 screen/widget files, but the edits (`RenderPipelines.GUI_TEXTURED` on `blit`, removing dead `RenderSystem.setShader` calls, and `PoseStack` → `Matrix3x2fStack` 2D calls) are straightforward and repetitive.

#### How to reduce risk
- **Remove dead `RenderSystem.setShader` / `setShaderTexture` calls before `GuiGraphics.blit` right now**: Even in 1.21.1, `GuiGraphics.blit` binds the texture and shader itself. Cleaning those up in `RadioScreen`, `Dial`, `ToggleButton`, etc. reduces diff noise later.
- **Route `GuiGraphics` blits and 2D pose transforms through `Crt.java` / a small `GuiDraw` helper**: `Crt.java` and `DosRenderer.java` already centralize most CRT drawing; adding a helper for scaled `GuiGraphics` blocks will make the `PoseStack` → `Matrix3x2fStack` transition a 10-minute change.

---

### 6. Third-Party Dependencies & Compat Modules

#### What the code does today & MC 26 Availability Assessment

| Dependency | Current Version (`1.21.1`) | Where Used in RadioCraft | MC 26 Availability & Porting Risk |
| :--- | :--- | :--- | :--- |
| **Simple Voice Chat API** (`de.maxhenkel.voicechat:voicechat-api`) | `2.5.26` (`compileOnly`, runtime jar in `2.6.11`) | Core dependency (`required` in `neoforge.mods.toml`). `RadiocraftVoicePlugin`, `BEVoiceReceiver`, `EncodingManager`, `PlayerRadio`, etc. | **Available / Low Risk.** Simple Voice Chat ports within days of every Minecraft snapshot and release, and its `2.5.x` plugin API (`VoicechatPlugin`, `VoicechatServerApi`, `OpusEncoder`/`OpusDecoder`, `LocationalAudioChannel`) has been stable for years. |
| **GeckoLib** (`software.bernie.geckolib:geckolib-neoforge`) | `4.8.2` (required in `neoforge.mods.toml`) | Used for **only 2 blocks** (`DeskChargerBlockEntity`/`Renderer`/`Item`, `SatelliteDishBlockEntity`/`Renderer`/`Item`, `BlinkingAutoGlowingGeoLayer`) and 3 unused/dev items (`VHFHandheldItem`—whose `registerControllers` is empty and whose actual renderer is `HandheldItemRenderer`!—plus `Tempurance`, `BensHammer`, `DevItemRenderer`). | **Medium-High Risk (or Easy to Eliminate!).** GeckoLib 4/5 typically ports to major releases, *but* its internal `GeoBlockRenderer` / `GeoItemRenderer` / `GeoRenderLayer` APIs changed significantly in 1.21.2–1.21.5 due to `EntityRenderState` and `SpecialModelRenderer`. **Recommendation:** Consider replacing the static `satellite_dish` and `desk_charger` `.geo.json` models with standard vanilla JSON block models (like the rest of RadioCraft's blocks) and dropping the required GeckoLib dependency altogether! |
| **Patchouli** (`vazkii.patchouli:Patchouli-xplat`) | `1.21-87-NEOFORGE` (optional) | `src/main/resources/assets/radiocraft/patchouli_books/handbook/**` and `data/radiocraft/patchouli_books/handbook/book.json`. No Java compile dependency on Patchouli API! First-join book grant in `ServerLifecycleEvents.java` looks up `BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"))` and sets `patchouli:book` via `DataComponentType` lookup. | **Low Code Risk, Medium Runtime Availability Risk.** Because RadioCraft has zero Java compile references to Patchouli, it will never block compiling or running on MC 26. However, Vazkii/blamejared port Patchouli more slowly to non-LTS drops; test whether Patchouli is out for MC 26. |
| **JOrbis** (`org.jcraft:jorbis`) | `0.0.17` (Shadow relocated to `com.arrl.radiocraft.libs.jorbis`) | Pure Java 1.5 Ogg Vorbis decoder used by `TapeAudio.java` (`src/main/java/com/arrl/radiocraft/common/radio/tape/TapeAudio.java`) on the server to read `.ogg` tape files. | **Zero Risk.** Pure Java, zero Minecraft/NeoForge imports. Note: verify `com.gradleup.shadow` plugin version supports the Gradle/Java version used by NeoForge 26 (`Java 21` or `Java 25` if MC 26 bumps the JVM target). |
| **jSerialComm** (`com.fazecast:jSerialComm`) | `2.11.4` (`jarJar` nested) | Used by `src/main/java/com/arrl/radiocraft/common/realworld/SerialLine.java` for hardware PTT/COS serial ports on the Real-World Interface block. Only loaded when `real_world_interface_enabled = true`. | **Zero Risk.** Pure Java/native serial library, zero Minecraft imports. |
| **Another Weather Mod (AWM)** (`libs/anotherweathermod-1.4.0.jar`) | `1.4.0` (local `flatDir` jar in `libs/`, optional) | `src/main/java/com/arrl/radiocraft/compat/anotherweathermod/AwmCompat.java`, `AwmWeatherSource.java`, `AwmWindDamage.java`. Wrapped behind `WeatherSource` interface (`src/main/java/com/arrl/radiocraft/api/weather/WeatherSource.java`). | **Low Risk (First-Party / Maintainer Jar).** Already cleanly isolated behind `AwmCompat` and `WeatherSource`. If AWM's `1.21.1` jar in `libs/` has not been recompiled against MC 26's `ServerLevel`/`BlockPos` when porting starts, `AwmWeatherSource` and `AwmWindDamage` can be temporarily excluded in `build.gradle.kts` (`sourceSets.main.java.exclude(...)`) until AWM is ported. |
| **Sable & Sable Companion** (`dev.ryanhcode.sable`) | `sable-neoforge-1.21.1-1.0.8-beta.4` & `sable-companion-common-1.21.1` (`jarJar`) | `src/main/java/com/arrl/radiocraft/compat/sable/Sublevels.java`, `SableAssembly.java`, and `src/main/java/com/arrl/radiocraft/mixin/MixinSableAssembly.java`. | **High Availability Risk, Low Code Risk.** Sable (and Veil `1.0.0.51`) are currently on 1.21.1 beta and are **unlikely** to be immediately available on MC 26. However, RadioCraft already isolates *all* Sable calls inside `Sublevels.java` and `MixinSableAssembly.java`! If `sable-companion` isn't available or its `1.21.1` jar conflicts with MC 26, `Sublevels.java` can be stubbed to return `null` / `pos` / `Frame.GROUND` (a 15-line change) without touching a single caller across the mod. |
| **Create, Ponder & Flywheel** (`com.simibubi.create`, `dev.engine-room.flywheel`) | `6.0.9-216` / `1.0.0-beta-254` (optional) | `src/main/java/com/arrl/radiocraft/compat/create/CreateCompat.java`, `CreateAlternator.java`, `AlternatorBlock.java`, `AlternatorBlockEntity.java`, `AlternatorRenderer.java`, `AlternatorVisual.java`. | **High Availability Risk, Very Easy to Disable.** Create lags behind newer Minecraft drops. Because `CreateCompat` is 100% self-contained in `compat/create/` and only called from `CreateCompat.register(modBus)` in `Radiocraft.java`, you can exclude `com/arrl/radiocraft/compat/create/**` in `build.gradle.kts` until Create releases for MC 26. |
| **Curios API** (`top.theillusivec4.curios`) | `9.5.1+1.21.1` (optional) | `src/main/java/com/arrl/radiocraft/compat/curios/CuriosCompat.java`, `CuriosIntegration.java`, `HandheldBeltRenderer.java`. | **Medium Risk.** Curios usually ports to new NeoForge versions, though `ICurioRenderer.render(...)` in `HandheldBeltRenderer.java` changed signature in 1.21.2+ to accept `EntityRenderState` instead of `LivingEntity` + limb swing floats. Isolated in `compat/curios/`. |
| **Jade** (`snownee.jade`) | `15.8.3+neoforge` (optional) | `src/main/java/com/arrl/radiocraft/compat/JadeCompat.java` and `*Provider.java` in `compat/`. | **Low-Medium Risk.** Jade ports rapidly to every release. In 1.21.5+, Jade providers that serialize server data into `CompoundTag` (`IServerDataProvider.appendServerData`) may see minor `CompoundTag` getter changes on the client side. |
| **TheOneProbe (TOP)** (`mcjty.theoneprobe`) | `1.21_neo-12.0.2-6` (optional) | `src/main/java/com/arrl/radiocraft/compat/TopCompat.java` and `TopProbe.java`. | **High Availability Risk, Easy to Disable.** TOP has not ported consistently to 1.21.2–1.21.5+. Isolated in 2 files (`TopCompat.java`, `TopProbe.java`); exclude from compilation if TOP is unavailable on MC 26. |
| **JEI** (`mezz.jei`) | `19.21.0.247` (optional) | `src/main/java/com/arrl/radiocraft/client/compat/RadiocraftJeiPlugin.java`. | **Low Risk.** JEI ports quickly to every release. Note: in 1.21.2+, client-side `Minecraft.getInstance().level.getRecipeManager()` was removed (the client no longer receives the full `RecipeManager`!), so `RadiocraftJeiPlugin.onRuntimeAvailable` hiding `mars_mod` via `level.getRecipeManager().byKey(id)` will need to use JEI's `IRecipeManager.createRecipeLookup(RecipeTypes.CRAFTING)` instead. |

---

### 7. Recipes, Custom Ingredients, Data Components, Loot Tables, and Datagen

#### What the code does today
- **Recipe Provider (`RadiocraftRecipesProvider.java`, 3,419 lines)**:
  - Extends `RecipeProvider(PackOutput, CompletableFuture<HolderLookup.Provider>)` and overrides `buildRecipes(RecipeOutput)`.
  - Calls `ShapedRecipeBuilder.shaped(RecipeCategory, ItemLike, count)` and `ShapelessRecipeBuilder.shapeless(RecipeCategory, ItemLike)`.
  - Uses `H19ScreenGlassIngredient.INSTANCE.toVanilla()` in `buildAprsTerminalRecipe`.
- **Custom Recipes (`AprsModuleInstallRecipe.java`, `LicenseCopyRecipe.java`, `MarsModInstallRecipe.java`)**:
  - Extend `CustomRecipe` (`matches(CraftingInput, Level)`, `assemble(CraftingInput, HolderLookup.Provider)`, `canCraftInDimensions(int, int)`).
  - Registered in `RadiocraftRecipeSerializers.java` using `SimpleCraftingRecipeSerializer`.
- **Custom Ingredient (`H19ScreenGlassIngredient.java`)**:
  - Implements `ICustomIngredient` (`test(ItemStack)`, `getItems()` returning `Stream<ItemStack>`, `isSimple()`, `getType()`).
- **Data Components (`RadiocraftDataComponent.java`)**:
  - Registers 24 custom `DataComponentType` entries (`Codec` + `StreamCodec`).
  - `PhotoPrints.java` and `CommercialLicenseItem.java` set `DataComponents.MAP_ID`, `DataComponents.ITEM_NAME`, `DataComponents.MAP_COLOR`, `DataComponents.LORE`.
- **Block Loot & Advancements (`RadiocraftBlockLootProvider.java`, `RadiocraftAdvancementProvider.java`)**:
  - `RadiocraftBlockLootProvider` uses `CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY).include(...)`.
  - `RadiocraftAdvancementProvider` uses `ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(...)`, `InventoryChangeTrigger.TriggerInstance.hasItems(...)`, `RecipeCraftedTrigger.TriggerInstance.craftedItem(ResourceLocation)`.

#### Which Minecraft / NeoForge API changes hit it
1. **1.21.2+ Recipe & `RecipeProvider` Overhaul**:
   - `ShapedRecipeBuilder.shaped(...)` and `ShapelessRecipeBuilder.shapeless(...)` now require a `HolderGetter<Item>` (`registries.lookupOrThrow(Registries.ITEM)`) as their **first argument**!
   - `RecipeProvider` was refactored into `RecipeProvider` (an instance created per `HolderLookup.Provider` with a `protected final HolderGetter<Item> items` field) + `RecipeProvider.Runner` (which implements `DataProvider`).
   - `RecipeProvider.has(ItemLike)` and `ShapedRecipeBuilder`/`ShapelessRecipeBuilder` methods are now instance methods on `RecipeProvider` (or take `HolderGetter<Item>`). With ~150+ recipe builder calls across 3,419 lines in `RadiocraftRecipesProvider.java`, this is a large mechanical edit.
   - Recipe IDs passed to `.save(recipeOutput, ...)` and `RecipeCraftedTrigger.TriggerInstance.craftedItem(...)` changed from `ResourceLocation` to `ResourceKey<Recipe<?>>` (`ResourceKey.create(Registries.RECIPE, Radiocraft.id(...))`).
2. **1.21.2+ `Ingredient` and `ICustomIngredient` Changes**:
   - `Ingredient.of(ItemLike...)` now works with `HolderSet<Item>`, and `ICustomIngredient.getItems()` changed from returning `Stream<ItemStack>` to `Stream<Holder<Item>>` (`BuiltInRegistries.ITEM.wrapAsHolder(...)`).
   - `CustomRecipe`: `canCraftInDimensions(int, int)` was removed in 1.21.2+, and `SimpleCraftingRecipeSerializer` was replaced by `CustomRecipe.Serializer`.
3. **1.21.5+ Tooltip & Data Component Changes (`Item.appendHoverText`)**:
   - In 1.21.5, `Item.appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag)` was replaced by `Item.appendHoverText(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` (and `TooltipProvider` components). Every item class in `src/main/java/com/arrl/radiocraft/common/items/*.java` that overrides `appendHoverText` will need a mechanical signature update (`tooltipComponents.add(...)` → `tooltipAdder.accept(...)`).
   - In 1.21.5, `CopyComponentsFunction.copyComponents(...)` changed its source parameter from `CopyComponentsFunction.Source.BLOCK_ENTITY` to `LootContextParams.BLOCK_ENTITY`.
4. **1.21.4+ Client Model Datagen Replacement**:
   - NeoForge's old `BlockStateProvider`, `ItemModelProvider`, and `ExistingFileHelper` (`net.neoforged.neoforge.client.model.generators.*`) were deprecated/replaced in 1.21.4+ by vanilla's now-public `ModelProvider` (`BlockModelGenerators` and `ItemModelGenerators`).
   - Note: `RadiocraftBlockstateProvider.modelTop(...)` reads raw JSON files via `models().existingFileHelper.getResource(...)`; in 1.21.4+, `ExistingFileHelper` is gone from `GatherDataEvent` (`GatherDataEvent.Client` / `GatherDataEvent.Server`), so `modelTop` can simply read from classpath/project resources directly (`Files.newBufferedReader(...)` or `ClassLoader.getResourceAsStream`).

#### Rough effort
**Medium (2–3 days).** Almost entirely compile-time mechanical updates in `RadiocraftRecipesProvider.java`, `RadiocraftBlockstateProvider.java`, and `Item.appendHoverText` overrides.

#### How to reduce risk
- **Add helper methods in `RadiocraftRecipesProvider.java` now**: Instead of calling `ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ...)` and `.save(recipeOutput, Radiocraft.id(...))` 150 times directly, add 2–3 private helper methods on `RadiocraftRecipesProvider` (`shapedMisc(ItemLike, int)`, `shapelessMisc(ItemLike)`, `save(RecipeBuilder, RecipeOutput, String)`). In MC 26, you'll only have to pass `this.items` and `ResourceKey.create(Registries.RECIPE, ...)` inside those 3 helpers!

---

### 8. Mixins, Reflection, Entities (`AntennaWire`), Worldgen, Maps, Networking, Sounds, Config, and GameTests

#### What the code does today & API changes hitting each

1. **Mixins (`src/main/resources/radiocraft.mixins.json`)**:
   - `MixinServerLevel.java`: Injects into `ServerLevel.tickChunk` at `INVOKE net/minecraft/world/level/Level.addFreshEntity` to detect lightning bolts for `SolarEventManager.onLightningStrike`. *(Why it's a mixin instead of NeoForge's `EntityJoinLevelEvent` is unclear; replacing it with `EntityJoinLevelEvent` checking `entity instanceof LightningBolt` removes a brittle bytecode injection target.)*
   - `MixinRedStoneWireBlock.java`: Injects into `RedStoneWireBlock.updatePowerStrength` (return) and `getBlockSignal` (return) for RFI pickup/induction. **1.21.2+ Impact**: Mojang added the experimental "Redstone Wire" (`DefaultRedstoneWireEvaluator` / `ExperimentalRedstoneWireEvaluator`) refactor in 1.21.2!, moving `updatePowerStrength` logic out of `RedStoneWireBlock` into `RedstoneWireEvaluator`. This mixin **will break** and needs to target `RedstoneWireEvaluator` (or `RedStoneWireBlock.updatePowerStrength(Level, BlockPos, BlockState, Orientation, boolean)`).
   - `MixinCartographyTableMenu.java`: Injects into `CartographyTableMenu.setupResultSlot` (`HEAD`, cancellable) to prevent copying commercial frequency licenses at a cartography table. Low risk, verify signature.
   - `MixinPTTKeyHandler.java`: Injects into Simple Voice Chat's `de.maxhenkel.voicechat.voice.client.PTTKeyHandler.isPTTDown` (`RETURN`, `remap = false`). Low risk.
   - `MixinSableAssembly.java`: Optional mixin into Sable's `SableLevelData` (guarded by `RadiocraftMixinPlugin`).
2. **Reflection (`VillageHouses.java` & `GameTestFilter.java`)**:
   - `src/main/java/com/arrl/radiocraft/common/worldgen/VillageHouses.java` reflects into `StructureTemplatePool.class.getDeclaredField("templates")`. Verify field name/mutability in MC 26.
   - `src/main/gametest/java/com/arrl/radiocraft/gametest/GameTestFilter.java` reflects into `GameTestServer.testFunctions`.
3. **Entities (`AntennaWire.java` & `AntennaWirePart.java`)**:
   - In **1.21.2+**, `Entity.hurt(DamageSource, float)` was split into client-safe `hurtClient(DamageSource)` and server-side `hurtServer(ServerLevel, DamageSource, float)`, and `Entity.kill()` became `Entity.kill(ServerLevel)`.Both `AntennaWire` and `AntennaWirePart` override `hurt(DamageSource, float)` and call `kill()` and must be updated to `hurtServer(ServerLevel, DamageSource, float)`.
4. **GameTests (`src/gametest/java/com/arrl/radiocraft/gametest/**`)**:
   - **Major 1.21.5 Overhaul**: In 1.21.5, Mojang made the GameTest framework **registry-driven** (`Registries.TEST_INSTANCE`, `Registries.TEST_ENVIRONMENT`) and removed/changed the old `@GameTest` annotation parameters (`template`, `timeoutTicks`, `batch`, `required`) and `RegisterGameTestsEvent`!
   - RadioCraft has **72 GameTest classes** in `src/gametest/java/com/arrl/radiocraft/gametest/` using `@GameTest(template = GameTestUtil.EMPTY, ...)`.
   - Depending on how NeoForge 26 bridges or replaces `@GameTest` (NeoForge 21.5+ introduced a code-driven registration helper for `TestInstance` / `Consumer<GameTestHelper>`), migrating the 72 GameTest classes will require a mechanical annotation/registration script.
5. **Networking (`RadiocraftNetworking.java` and 49 Packets)**:
   - Already uses 1.20.5+ `CustomPacketPayload` and `StreamCodec` (`PayloadRegistrar` in `RadiocraftNetworking.java`). **Very Low Risk**—almost no changes between 1.21.1 and MC 26, except any packets that encode/decode `CompoundTag` and then call `CompoundTag` getters in their handlers.
6. **Maps (`PhotoPrints.java`, `PhotoLedger.java`, `MapPalettes.java`)**:
   - Uses `MapItemSavedData.createFresh(...)`, `level.getFreeMapId()`, `level.setMapData(id, data)`, `MapColor.byId(base)`, `colour.calculateRGBColor(brightness)` (renamed `getArgbColor(brightness)` in 1.21.2+, returning `0xAARRGGBB` instead of `0xAABBGGRR`! **Watch out for this in `MapPalettes.java` line 27–28**, or photo colors will have R and B channels swapped!).
7. **Sounds (`RadioStaticSoundInstance.java`, `RadioMorseSoundInstance.java`, `TerminalWhirr.java`) & Config (`ModConfigSpec`)**:
   - `AbstractTickableSoundInstance` and `ModConfigSpec` are virtually unchanged through MC 26. **Very Low Risk.**

---

## Recommended Porting Strategy & Order of Operations

To minimize risk and avoid being blocked by a 500-error uncompilable workspace during the MC 26 jump:

1. **Pre-Port Refactoring on 1.21.1 (Can be done today with all tests green)**:
   - **Consolidate Offscreen FBOs**: Merge `ModerationDesk.MonitorTexture`/`capture` and `ComputerSession.MonitorTexture`/`capture` into a single `OffscreenViewport` class.
   - **Wrap `RadiocraftRecipesProvider` builders**: Add `shaped(...)`, `shapeless(...)`, and `save(...)` helper methods in `RadiocraftRecipesProvider.java` so the 1.21.2+ `HolderGetter<Item>` and `ResourceKey<Recipe<?>>` changes touch 3 lines instead of 400.
   - **Fix `PlayerCallsignSavedData` & `BlockEntityCallsignSavedData`**: Move their `static final HashMap` fields to instance fields.
   - **Replace `MixinServerLevel` with `EntityJoinLevelEvent`**: One less mixin to maintain.
   - **Evaluate dropping GeckoLib**: If `satellite_dish` and `desk_charger` can use normal block models (and `DeskChargerBlockRenderer` draws the held handheld via `ItemRenderer` just like `PassthroughRenderer` draws blocks), you can drop GeckoLib as a required dependency completely.
2. **Step 1 of the MC 26 Port — Common & Server First (Disable Client & Optional Compat Temporarily)**:
   - Temporarily exclude `compat/create/**`, `compat/TopCompat.java`, `compat/TopProbe.java`, and (if needed) `compat/anotherweathermod/**` and `compat/sable/**` in `build.gradle.kts`.
   - Port `CompoundTag` (`getIntOr`, `getStringOr`, `UUIDUtil.CODEC`), `BlockEntity` (`ValueInput`/`ValueOutput`), `SavedData` (`SavedDataType`), `AntennaWire` (`hurtServer`), `CustomRecipe`/`H19ScreenGlassIngredient`, and `MixinRedStoneWireBlock`.
   - Fix `MapPalettes.java` (`calculateRGBColor` ABGR → `getArgbColor` ARGB).
   - Get `./gradlew compileJava` (common) and server startup working first.
3. **Step 2 — Datagen & Client Item Definitions**:
   - Update `RadiocraftRecipesProvider`, `RadiocraftBlockLootProvider`, and `RadiocraftBlockstateProvider`.
   - Generate `assets/radiocraft/items/<item>.json` for all items.
4. **Step 3 — Client Renderers & Screens**:
   - Port `GuiGraphics.blit` (`RenderPipelines.GUI_TEXTURED`) and 2D `pose()` (`Matrix3x2fStack`) across `client/screens/**`.
   - Port `HandheldItemRenderer` to `SpecialModelRenderer`, item properties to `ConditionalItemModelProperty`, and item colors to `ItemTintSource`.
   - Port `WireModel` and `TowerBeamModel` to the new `BlockStateModel` geometry loader API.
   - Port `AntennaWireEntityRenderer`, `ManpackLayer`, `PhoneEarLayer`, and `FccJacketModel` to `EntityRenderState` / `PlayerRenderState` / `EquipmentAssets`.
   - Port `MonitorMirror`, `ModerationDesk`, `ComputerSession`, `PhotoCapture`, `AuroraRenderer`, and `SatelliteSkyRenderer` to the 1.21.5+ Blaze3D `GpuDevice` / `CommandEncoder` / `RenderPipeline` API.
5. **Step 4 — GameTests**:
   - Migrate the 72 GameTest classes in `src/gametest` to the 1.21.5+ registry-based GameTest format and verify gameplay parity.

---

## Summary Table

| Rank | Area | Key Files Affected | Primary 1.21.2 → MC 26 Breaking Changes | Rough Effort | Risk Mitigation |
| :---: | :--- | :--- | :--- | :---: | :--- |
| **1** | **Offscreen FBOs, Raw OpenGL & Sky Shaders** | `MonitorMirror.java`, `ModerationDesk.java`, `ComputerSession.java`, `PhotoCapture.java`, `AuroraRenderer.java`, `SatelliteSkyRenderer.java` | 1.21.5+ Blaze3D `GpuDevice` / `CommandEncoder` / `GpuTexture` / `RenderPipeline`; removal of raw `frameBufferId` / `GL30._glBlitFrameBuffer`; shader & fog API changes | **3–5 days** | Unify FBO blit code now; consider `DynamicTexture` or direct 3D quad rendering for `MonitorMirror` |
| **2** | **Item Models, BEWLR, Tints & Dynamic Block Models** | `HandheldItemRenderer.java`, `ClientSetupEvents.java`, `WireModel.java`, `TowerBeamModel.java`, `assets/radiocraft/items/*.json` | 1.21.4 Client Item JSONs (`items/*.json`), `SpecialModelRenderer`, `ConditionalItemModelProperty`, `ItemTintSource`; 1.21.5+ `BlockStateModel` baking | **3–4 days** | Generate `items/*.json` via datagen; use 1.21.4 `composite` item model for handhelds to drop `ModifyBakingResult` wrapper |
| **3** | **Entity/Player Render States, 16 BERs & Custom Armor** | `AntennaWireEntityRenderer.java`, `ManpackLayer.java`, `PhoneEarLayer.java`, `FccJacketModel.java`, `RadiocraftArmorMaterials.java`, `client/render/blockentity/*` | 1.21.2+ `EntityRenderState` & `PlayerRenderState` (`RegisterRenderStateModifiersEvent`); `EquipmentAssets` / `Equippable`; 1.21.5+ BER & `BlockModelRenderer` signatures | **3–4 days** | Extract state records in `ManpackLayer`/`PhoneEarLayer` now; centralize `renderBakedModel` helpers |
| **4** | **NBT (`CompoundTag`), `ValueInput`/`ValueOutput` & `SavedData`** | 19 `SavedData` classes, ~80 `BlockEntity` classes (`RadioBlockEntity.java`, etc.), `AntennaWire.java`, `SitePiece.java` | 1.21.5 `CompoundTag` Optional getters & `UUIDUtil.CODEC`; `SavedDataType<T>` with codecs; 1.21.6+ `ValueInput`/`ValueOutput` replacing `CompoundTag` in BE/Entity save/load | **3–4 days** | Fix static maps in Callsign `SavedData`; wrap `CompoundTag.CODEC` for fast initial `SavedDataType` migration |
| **5** | **GUI Screens (`GuiGraphics`) & 2D Matrix Stack** | 37 screen/widget classes in `client/screens/**` (`RadioScreen.java`, `DosRenderer.java`, `Crt.java`, `StationPanelScreen.java`, `Dial.java`) | 1.21.2+ `GuiGraphics.blit(RenderType::guiTextured, ...)`; 1.21.6+ 2D `Matrix3x2fStack` (`pushMatrix`/`translate(x,y)`/`scale(x,y)`); removal of manual `setShader` calls | **2 days** | Delete dead `RenderSystem.setShader` calls in GUI screens now; centralize scaled GUI transforms |
| **6** | **Third-Party Mods & Compat** | `DeskCharger*`, `SatelliteDish*` (GeckoLib), `compat/create/*`, `compat/sable/*`, `compat/curios/*`, `compat/TopCompat.java`, `RadiocraftJeiPlugin.java` | GeckoLib renderer rewrites; Create & TOP likely unavailable early on MC 26; Curios `ICurioRenderer` render state; JEI client `RecipeManager` removal | **1–2 days** | Consider replacing GeckoLib on the 2 blocks that use it; exclude `compat/create` & `TopCompat` in `build.gradle.kts` until ready |
| **7** | **Recipes, Ingredients, Tooltips & Datagen** | `RadiocraftRecipesProvider.java` (3.4k lines), `H19ScreenGlassIngredient.java`, `AprsModuleInstallRecipe.java`, `RadiocraftBlockLootProvider.java`, `Item.appendHoverText` | 1.21.2+ `RecipeProvider` (`HolderGetter<Item>`, `ResourceKey<Recipe<?>>`), `ICustomIngredient` `Stream<Holder<Item>>`; 1.21.5 `TooltipDisplay` + `Consumer<Component>` | **2–3 days** | Wrap `ShapedRecipeBuilder`/`ShapelessRecipeBuilder`/`.save()` in helper methods on `RadiocraftRecipesProvider` now |
| **8** | **Mixins, GameTests, Maps & Entities** | `MixinRedStoneWireBlock.java`, `MixinServerLevel.java`, `MapPalettes.java`, `AntennaWire.java`, 72 GameTest files in `src/gametest/` | 1.21.2 `RedstoneWireEvaluator` breaks `MixinRedStoneWireBlock`; `MapColor.getArgbColor` swaps ABGR→ARGB; `Entity.hurtServer`; 1.21.5 registry-driven GameTests | **2–3 days** | Replace `MixinServerLevel` with `EntityJoinLevelEvent`; script `@GameTest` annotation migration |
| **9** | **Core Radio Math, Audio DSP, Networking & Config** | `common/radio/**`, `common/computer/**`, `RadiocraftNetworking.java`, `CommonConfig.java`, `RadiocraftServerConfig.java` | `CustomPacketPayload` / `StreamCodec`, `ModConfigSpec`, `AbstractTickableSoundInstance`, and pure Java DSP/simulation are stable | **< 0.5 days** | Already well-isolated and covered by JUnit tests (`src/test`) |
