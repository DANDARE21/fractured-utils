package net.dandare21.fracturedutils.npc.client.gui;

import net.dandare21.fracturedutils.client.gui.CyberpunkButton;
import net.dandare21.fracturedutils.client.gui.CyberpunkDropdown;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.NpcModelType;
import net.dandare21.fracturedutils.npc.NpcMovementMode;
import net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel;
import net.dandare21.fracturedutils.npc.client.asset.NpcAssetScanner;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.dandare21.fracturedutils.npc.network.C2SSaveNpcPacket;
import net.dandare21.fracturedutils.npc.pipeline.NpcRule;
import net.dandare21.fracturedutils.puppet.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NpcStudioScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xF005090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int GOLD = 0xFFFFD700;
    private static final int GRAY_TEXT = 0xFF8899A6;

    private final int targetEntityId;
    private NpcDefinition definition;

    // Active tab: 0 = Model/Skin, 1 = Stats/Health, 2 = AI/Movement, 3 = Rules, 4 =
    // Dialog Linker
    private int activeTab = 0;

    // Viewport 3D preview state
    private float previewYaw = 0.0F;
    private float previewPitch = 0.0F;
    private boolean isDraggingPreview = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private CustomNpcEntity dummyEntity = null;

    // Form inputs: Tab 0 (Model & Skin)
    private EditBox templateIdEditBox;
    private String currentId;
    private String currentName;
    private String currentSkinVal;
    private String currentModelLoc;
    private String currentTexLoc;
    private String currentAnimLoc;
    private EditBox nameEditBox;
    private EditBox modelLocEditBox;
    private EditBox texLocEditBox;
    private EditBox animLocEditBox;
    private EditBox skinValEditBox;
    private boolean isSlimModel = false;
    private boolean showNameTag = true;
    private NpcModelType selectedModelType;

    // Form inputs: Tab 1 (Stats & Health)
    private String currentMaxHealth;
    private String currentAttackDamage;
    private String currentKnockbackRes;
    private String currentMoveSpeed;
    private String currentWidth;
    private String currentHeight;
    private String currentEyeHeight;
    private EditBox maxHealthEditBox;
    private EditBox attackDamageEditBox;
    private EditBox knockbackResEditBox;
    private EditBox moveSpeedEditBox;
    private EditBox widthEditBox;
    private EditBox heightEditBox;
    private boolean invulnerable;
    private boolean immortal;
    private boolean noGravity;
    private boolean pushable;

    // Form inputs: Tab 2 (AI & Movement)
    private String currentLookRadius;
    private EditBox lookRadiusEditBox;
    private NpcMovementMode selectedMovementMode;
    private final List<Vec3> waypoints = new ArrayList<>();

    // Form inputs: Tab 3 (Rules & Actions)
    private final List<NpcRule> rules = new ArrayList<>();

    // Form inputs: Tab 4 (Dialog Linker)
    private String selectedDialogSeq = "";
    private CyberpunkDropdown<ResourceLocation> dialogDropdown;

    public NpcStudioScreen(int targetEntityId, NpcDefinition definition) {
        super(Component.literal("NPC Studio"));
        this.targetEntityId = targetEntityId;
        this.definition = definition != null ? definition
                : NpcDefinition.createDefault(new ResourceLocation("fractured_utils", "npc_temp"));

        this.currentId = this.definition.id().toString();
        this.currentName = this.definition.name();
        this.selectedModelType = this.definition.modelType();
        this.isSlimModel = "slim".equalsIgnoreCase(this.definition.skinModel());
        this.showNameTag = this.definition.showNameTag();
        this.currentSkinVal = this.definition.skinValue();
        this.currentModelLoc = this.definition.modelLocation().map(ResourceLocation::toString).orElse("");
        this.currentTexLoc = this.definition.textureLocation().map(ResourceLocation::toString).orElse("");
        this.currentAnimLoc = this.definition.animationLocation().map(ResourceLocation::toString).orElse("");

        // Stats
        this.invulnerable = this.definition.invulnerable();
        this.immortal = this.definition.immortal();
        this.currentMaxHealth = String.format("%.1f", this.definition.maxHealth());
        this.currentAttackDamage = String.format("%.1f", this.definition.attackDamage());
        this.currentKnockbackRes = String.format("%.2f", this.definition.knockbackResistance());
        this.currentMoveSpeed = String.format("%.2f", this.definition.moveSpeed());
        this.noGravity = this.definition.noGravity();
        this.pushable = this.definition.pushable();
        this.currentLookRadius = String.format("%.1f", this.definition.lookRadius());
        this.currentWidth = String.format(Locale.ROOT, "%.2f", this.definition.width());
        this.currentHeight = String.format(Locale.ROOT, "%.2f", this.definition.height());
        this.currentEyeHeight = String.format(Locale.ROOT, "%.2f", this.definition.eyeHeight());

        // Movement
        this.selectedMovementMode = this.definition.movementMode();
        this.waypoints.addAll(this.definition.patrolWaypoints());

        // Rules
        this.rules.addAll(this.definition.rules());

        // Dialog
        if (this.definition.defaultDialog().isPresent()) {
            this.selectedDialogSeq = this.definition.defaultDialog().get().toString();
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (this.minecraft != null && this.minecraft.level != null) {
            this.dummyEntity = new CustomNpcEntity(ModEntities.CUSTOM_NPC.get(), this.minecraft.level);
            this.dummyEntity.setDefinition(this.definition, false);
        }

        int boxW = Math.min(this.width - 20, 600);
        int boxH = Math.min(this.height - 20, 370);
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        // Template ID EditBox in Header
        templateIdEditBox = new EditBox(this.font, boxX + 135, boxY + 7, 210, 16, Component.literal("ID"));
        templateIdEditBox.setMaxLength(128);
        templateIdEditBox.setValue(this.currentId != null ? this.currentId : this.definition.id().toString());
        templateIdEditBox.setResponder(val -> {
            this.currentId = val;
            updateDummy();
        });
        this.addRenderableWidget(templateIdEditBox);

        // 5 Tabs
        int tabW = 82;
        int tabH = 20;
        int tabY = boxY + 28;

        this.addRenderableWidget(new CyberpunkButton(boxX + 10, tabY, tabW, tabH,
                Component.literal("1. Model"), b -> switchTab(0), CYAN_MAIN, activeTab == 0));
        this.addRenderableWidget(new CyberpunkButton(boxX + 96, tabY, tabW, tabH,
                Component.literal("2. Stats"), b -> switchTab(1), CYAN_MAIN, activeTab == 1));
        this.addRenderableWidget(new CyberpunkButton(boxX + 182, tabY, tabW, tabH,
                Component.literal("3. AI Move"), b -> switchTab(2), CYAN_MAIN, activeTab == 2));
        this.addRenderableWidget(new CyberpunkButton(boxX + 268, tabY, tabW, tabH,
                Component.literal("4. Rules"), b -> switchTab(3), CYAN_MAIN, activeTab == 3));
        this.addRenderableWidget(new CyberpunkButton(boxX + 354, tabY, tabW, tabH,
                Component.literal("5. Dialog"), b -> switchTab(4), CYAN_MAIN, activeTab == 4));

        // Tab Content
        initTabContent(boxX, boxY, boxW, boxH);

        // Preview Camera Anchor Button
        int previewX = boxX + boxW - 165;
        int previewBottomY = boxY + 295;
        this.addRenderableWidget(new CyberpunkButton(previewX, previewBottomY, 150, 20,
                Component.literal("📷 Set Eye from View"), b -> setEyeAnchorFromLook(), GOLD, false));

        // Bottom Footer Controls
        int footerY = boxY + boxH - 26;
        if (targetEntityId < 0) {
            this.addRenderableWidget(new CyberpunkButton(boxX + 10, footerY, 140, 20,
                    Component.literal("✓ Save & Spawn NPC"), b -> saveNpc(true, false, true), 0xFF00FF88, false));
            this.addRenderableWidget(new CyberpunkButton(boxX + 155, footerY, 130, 20,
                    Component.literal("Save Template Only"), b -> saveNpc(true, false, false)));
        } else {
            this.addRenderableWidget(new CyberpunkButton(boxX + 10, footerY, 130, 20,
                    Component.literal("✓ Apply & Save"), b -> saveNpc(true, true, false), 0xFF00FF88, false));
            this.addRenderableWidget(new CyberpunkButton(boxX + 145, footerY, 130, 20,
                    Component.literal("Apply to Entity Only"), b -> saveNpc(false, true, false)));
            this.addRenderableWidget(new CyberpunkButton(boxX + 280, footerY, 95, 20,
                    Component.literal("Spawn Copy"), b -> saveNpc(true, false, true), GOLD, false));
        }

        this.addRenderableWidget(new CyberpunkButton(boxX + boxW - 85, footerY, 75, 20,
                Component.literal("Cancel"), b -> this.onClose(), 0xFFFF5555, false));
    }

    private void switchTab(int tab) {
        this.definition = collectDefinition();
        this.activeTab = tab;
        this.init();
    }

    private void initTabContent(int boxX, int boxY, int boxW, int boxH) {
        int contentX = boxX + 15;
        int contentY = boxY + 56;

        if (activeTab == 0) {
            // TAB 0: Model & Skin
            nameEditBox = new EditBox(this.font, contentX, contentY + 12, 220, 18, Component.literal("Name"));
            nameEditBox.setMaxLength(64);
            nameEditBox.setValue(this.currentName != null ? this.currentName : this.definition.name());
            nameEditBox.setResponder(val -> {
                this.currentName = val;
                updateDummy();
            });
            this.addRenderableWidget(nameEditBox);

            // Quick color buttons
            String[] codes = { "&a", "&b", "&c", "&e", "&6", "&d", "&f", "&l", "&r" };
            int btnX = contentX + 225;
            for (String code : codes) {
                this.addRenderableWidget(new CyberpunkButton(btnX, contentY + 12, 16, 18,
                        Component.literal(code), b -> {
                            nameEditBox.setValue(nameEditBox.getValue() + code);
                            this.currentName = nameEditBox.getValue();
                            updateDummy();
                        }, CYAN_MAIN, false));
                btnX += 18;
            }

            // Model Type Toggle
            int modelToggleY = contentY + 38;
            this.addRenderableWidget(new CyberpunkButton(contentX, modelToggleY, 180, 20,
                    Component.literal("Model: " + selectedModelType.name()), b -> {
                        this.definition = collectDefinition();
                        selectedModelType = (selectedModelType == NpcModelType.GECKOLIB) ? NpcModelType.PLAYER
                                : NpcModelType.GECKOLIB;
                        if (selectedModelType == NpcModelType.GECKOLIB
                                && (currentModelLoc == null || currentModelLoc.isBlank())) {
                            this.currentModelLoc = DynamicNpcGeoModel.FALLBACK_MODEL.toString();
                            this.currentTexLoc = DynamicNpcGeoModel.FALLBACK_TEXTURE.toString();
                            this.currentAnimLoc = DynamicNpcGeoModel.FALLBACK_ANIMATION.toString();
                        }
                        b.setMessage(Component.literal("Model: " + selectedModelType.name()));
                        updateDummy();
                        this.init();
                    }));

            if (selectedModelType == NpcModelType.PLAYER) {
                this.addRenderableWidget(new CyberpunkButton(contentX + 190, modelToggleY, 110, 20,
                        Component.literal("Arms: " + (isSlimModel ? "Slim (Alex)" : "Default (Steve)")), b -> {
                            isSlimModel = !isSlimModel;
                            b.setMessage(
                                    Component.literal("Arms: " + (isSlimModel ? "Slim (Alex)" : "Default (Steve)")));
                            updateDummy();
                        }));

                this.addRenderableWidget(new CyberpunkButton(contentX + 305, modelToggleY, 100, 20,
                        Component.literal("Tag: " + (showNameTag ? "SHOW" : "HIDE")), b -> {
                            showNameTag = !showNameTag;
                            b.setMessage(Component.literal("Tag: " + (showNameTag ? "SHOW" : "HIDE")));
                            updateDummy();
                        }, showNameTag ? CYAN_MAIN : 0xFF777777, showNameTag));

                // Skin Value Input & "Use My Skin"
                int skinY = modelToggleY + 28;
                skinValEditBox = new EditBox(this.font, contentX, skinY + 12, 250, 18, Component.literal("Skin"));
                skinValEditBox.setMaxLength(256);
                skinValEditBox
                        .setValue(this.currentSkinVal != null ? this.currentSkinVal : this.definition.skinValue());
                skinValEditBox.setResponder(val -> {
                    this.currentSkinVal = val;
                    updateDummy();
                });
                this.addRenderableWidget(skinValEditBox);

                this.addRenderableWidget(new CyberpunkButton(contentX + 255, skinY + 11, 125, 20,
                        Component.literal("👤 Use My Skin"), b -> {
                            if (this.minecraft != null && this.minecraft.player != null) {
                                String myName = this.minecraft.player.getScoreboardName();
                                skinValEditBox.setValue(myName);
                                this.currentSkinVal = myName;
                                updateDummy();
                            }
                        }, GOLD, false));

                // Preset Skins Bar
                int presetY = skinY + 38;
                int px = contentX;
                for (String preset : NpcAssetScanner.getSkinPresets()) {
                    if (px + 40 > contentX + 380)
                        break;
                    this.addRenderableWidget(new CyberpunkButton(px, presetY, 40, 18,
                            Component.literal(preset), b -> {
                                skinValEditBox.setValue(preset);
                                this.currentSkinVal = preset;
                                updateDummy();
                            }, CYAN_MAIN, false));
                    px += 42;
                }

            } else {
                this.addRenderableWidget(new CyberpunkButton(contentX + 190, modelToggleY, 150, 20,
                        Component.literal("Name Tag: " + (showNameTag ? "VISIBLE" : "HIDDEN")), b -> {
                            showNameTag = !showNameTag;
                            b.setMessage(Component.literal("Name Tag: " + (showNameTag ? "VISIBLE" : "HIDDEN")));
                            updateDummy();
                        }, showNameTag ? CYAN_MAIN : 0xFF777777, showNameTag));

                // GeckoLib Paths with [Browse] buttons
                int geoY = modelToggleY + 26;
                modelLocEditBox = new EditBox(this.font, contentX, geoY + 10, 280, 18, Component.literal("Geo"));
                modelLocEditBox.setMaxLength(256);
                modelLocEditBox.setValue(this.currentModelLoc != null ? this.currentModelLoc
                        : this.definition.modelLocation().map(ResourceLocation::toString).orElse(""));
                modelLocEditBox.setResponder(val -> {
                    this.currentModelLoc = val;
                    ResourceLocation parsed = ResourceLocation.tryParse(val.trim());
                    if (parsed != null && DynamicNpcGeoModel.isValidModel(parsed)) {
                        NpcAssetScanner.ModelDimensions dims = NpcAssetScanner.computeModelDimensions(parsed);
                        this.currentWidth = String.format(Locale.ROOT, "%.2f", dims.width());
                        this.currentHeight = String.format(Locale.ROOT, "%.2f", dims.height());
                        this.currentEyeHeight = String.format(Locale.ROOT, "%.2f", dims.eyeHeight());
                        if (this.widthEditBox != null) this.widthEditBox.setValue(this.currentWidth);
                        if (this.heightEditBox != null) this.heightEditBox.setValue(this.currentHeight);
                    }
                    updateDummy();
                });
                this.addRenderableWidget(modelLocEditBox);

                this.addRenderableWidget(new CyberpunkButton(contentX + 285, geoY + 9, 95, 20,
                        Component.literal("📁 Browse"), b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new AssetPickerModalScreen(this, "Select Geo Model",
                                        NpcAssetScanner.getAvailableModels(), this::onGeoModelSelected));
                            }
                        }, GOLD, false));

                texLocEditBox = new EditBox(this.font, contentX, geoY + 44, 280, 18, Component.literal("Tex"));
                texLocEditBox.setMaxLength(256);
                texLocEditBox.setValue(this.currentTexLoc != null ? this.currentTexLoc
                        : this.definition.textureLocation().map(ResourceLocation::toString).orElse(""));
                texLocEditBox.setResponder(val -> {
                    this.currentTexLoc = val;
                    updateDummy();
                });
                this.addRenderableWidget(texLocEditBox);

                this.addRenderableWidget(new CyberpunkButton(contentX + 285, geoY + 43, 95, 20,
                        Component.literal("📁 Browse"), b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new AssetPickerModalScreen(this, "Select Texture",
                                        NpcAssetScanner.getAvailableTextures(), chosen -> {
                                            this.currentTexLoc = chosen;
                                            if (this.texLocEditBox != null) {
                                                this.texLocEditBox.setValue(chosen);
                                            }
                                            this.definition = collectDefinition();
                                            updateDummy();
                                        }));
                            }
                        }, GOLD, false));

                animLocEditBox = new EditBox(this.font, contentX, geoY + 78, 280, 18, Component.literal("Anim"));
                animLocEditBox.setMaxLength(256);
                animLocEditBox.setValue(this.currentAnimLoc != null ? this.currentAnimLoc
                        : this.definition.animationLocation().map(ResourceLocation::toString).orElse(""));
                animLocEditBox.setResponder(val -> {
                    this.currentAnimLoc = val;
                    updateDummy();
                });
                this.addRenderableWidget(animLocEditBox);

                this.addRenderableWidget(new CyberpunkButton(contentX + 285, geoY + 77, 95, 20,
                        Component.literal("📁 Browse"), b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new AssetPickerModalScreen(this, "Select Animation",
                                        NpcAssetScanner.getAvailableAnimations(), chosen -> {
                                            this.currentAnimLoc = chosen;
                                            if (this.animLocEditBox != null) {
                                                this.animLocEditBox.setValue(chosen);
                                            }
                                            this.definition = collectDefinition();
                                            updateDummy();
                                        }));
                            }
                        }, GOLD, false));
            }

        } else if (activeTab == 1) {
            // TAB 1: Stats & Health
            int rowY = contentY;

            // Health & Attack Damage
            maxHealthEditBox = new EditBox(this.font, contentX + 110, rowY, 80, 18, Component.literal("Max HP"));
            maxHealthEditBox.setMaxLength(16);
            maxHealthEditBox.setValue(this.currentMaxHealth != null ? this.currentMaxHealth
                    : String.format("%.1f", this.definition.maxHealth()));
            maxHealthEditBox.setResponder(val -> {
                this.currentMaxHealth = val;
                updateDummy();
            });
            this.addRenderableWidget(maxHealthEditBox);

            attackDamageEditBox = new EditBox(this.font, contentX + 300, rowY, 80, 18, Component.literal("Attack Dmg"));
            attackDamageEditBox.setMaxLength(16);
            attackDamageEditBox.setValue(this.currentAttackDamage != null ? this.currentAttackDamage
                    : String.format("%.1f", this.definition.attackDamage()));
            attackDamageEditBox.setResponder(val -> {
                this.currentAttackDamage = val;
                updateDummy();
            });
            this.addRenderableWidget(attackDamageEditBox);

            // Knockback Res & Move Speed
            rowY += 28;
            knockbackResEditBox = new EditBox(this.font, contentX + 110, rowY, 80, 18,
                    Component.literal("Knockback Res"));
            knockbackResEditBox.setMaxLength(16);
            knockbackResEditBox.setValue(this.currentKnockbackRes != null ? this.currentKnockbackRes
                    : String.format("%.2f", this.definition.knockbackResistance()));
            knockbackResEditBox.setResponder(val -> {
                this.currentKnockbackRes = val;
                updateDummy();
            });
            this.addRenderableWidget(knockbackResEditBox);

            moveSpeedEditBox = new EditBox(this.font, contentX + 300, rowY, 80, 18, Component.literal("Move Speed"));
            moveSpeedEditBox.setMaxLength(16);
            moveSpeedEditBox.setValue(this.currentMoveSpeed != null ? this.currentMoveSpeed
                    : String.format("%.2f", this.definition.moveSpeed()));
            moveSpeedEditBox.setResponder(val -> {
                this.currentMoveSpeed = val;
                updateDummy();
            });
            this.addRenderableWidget(moveSpeedEditBox);

            // Hitbox Dimensions (Auto-calculated from Model)
            rowY += 28;
            widthEditBox = new EditBox(this.font, contentX + 110, rowY, 80, 18, Component.literal("Width"));
            widthEditBox.setMaxLength(16);
            widthEditBox.setValue(this.currentWidth != null ? this.currentWidth
                    : String.format(Locale.ROOT, "%.2f", this.definition.width()));
            widthEditBox.setResponder(val -> {
                this.currentWidth = val;
                updateDummy();
            });
            this.addRenderableWidget(widthEditBox);

            heightEditBox = new EditBox(this.font, contentX + 300, rowY, 80, 18, Component.literal("Height"));
            heightEditBox.setMaxLength(16);
            heightEditBox.setValue(this.currentHeight != null ? this.currentHeight
                    : String.format(Locale.ROOT, "%.2f", this.definition.height()));
            heightEditBox.setResponder(val -> {
                this.currentHeight = val;
                updateDummy();
            });
            this.addRenderableWidget(heightEditBox);

            // Toggles: Invulnerable & Immortal
            rowY += 34;
            this.addRenderableWidget(new CyberpunkButton(contentX, rowY, 185, 20,
                    Component.literal("Invulnerable: " + (invulnerable ? "ON (Immune)" : "OFF")), b -> {
                        invulnerable = !invulnerable;
                        b.setMessage(Component.literal("Invulnerable: " + (invulnerable ? "ON (Immune)" : "OFF")));
                        updateDummy();
                    }, invulnerable ? CYAN_MAIN : 0xFF777777, invulnerable));

            this.addRenderableWidget(new CyberpunkButton(contentX + 195, rowY, 185, 20,
                    Component.literal("Immortal: " + (immortal ? "ON (Never Dies)" : "OFF")), b -> {
                        immortal = !immortal;
                        b.setMessage(Component.literal("Immortal: " + (immortal ? "ON (Never Dies)" : "OFF")));
                        updateDummy();
                    }, immortal ? GOLD : 0xFF777777, immortal));

            // Toggles: Gravity & Pushable
            rowY += 26;
            this.addRenderableWidget(new CyberpunkButton(contentX, rowY, 185, 20,
                    Component.literal("Gravity: " + (noGravity ? "OFF" : "ON")), b -> {
                        noGravity = !noGravity;
                        b.setMessage(Component.literal("Gravity: " + (noGravity ? "OFF" : "ON")));
                        updateDummy();
                    }, noGravity ? 0xFF777777 : CYAN_MAIN, !noGravity));

            this.addRenderableWidget(new CyberpunkButton(contentX + 195, rowY, 185, 20,
                    Component.literal("Pushable: " + (pushable ? "ON" : "OFF")), b -> {
                        pushable = !pushable;
                        b.setMessage(Component.literal("Pushable: " + (pushable ? "ON" : "OFF")));
                        updateDummy();
                    }, pushable ? CYAN_MAIN : 0xFF777777, pushable));

        } else if (activeTab == 2) {
            // TAB 2: AI & Movement
            this.addRenderableWidget(new CyberpunkButton(contentX, contentY, 180, 20,
                    Component.literal("Movement: " + selectedMovementMode.name()), b -> {
                        selectedMovementMode = switch (selectedMovementMode) {
                            case STATIONARY -> NpcMovementMode.WANDER;
                            case WANDER -> NpcMovementMode.PATROL;
                            case PATROL -> NpcMovementMode.STATIONARY;
                        };
                        b.setMessage(Component.literal("Movement: " + selectedMovementMode.name()));
                        updateDummy();
                        this.init();
                    }));

            lookRadiusEditBox = new EditBox(this.font, contentX + 280, contentY + 2, 80, 18,
                    Component.literal("Look Radius"));
            lookRadiusEditBox.setMaxLength(16);
            lookRadiusEditBox.setValue(this.currentLookRadius != null ? this.currentLookRadius
                    : String.valueOf(this.definition.lookRadius()));
            lookRadiusEditBox.setResponder(val -> {
                this.currentLookRadius = val;
                updateDummy();
            });
            this.addRenderableWidget(lookRadiusEditBox);

            if (selectedMovementMode == NpcMovementMode.PATROL) {
                int wpY = contentY + 30;
                this.addRenderableWidget(new CyberpunkButton(contentX, wpY, 180, 20,
                        Component.literal("📍 Add Current Pos Waypoint"), b -> {
                            if (this.minecraft != null && this.minecraft.player != null) {
                                waypoints.add(this.minecraft.player.position());
                                this.init();
                            }
                        }, GOLD, false));

                this.addRenderableWidget(new CyberpunkButton(contentX + 190, wpY, 110, 20,
                        Component.literal("Clear Waypoints"), b -> {
                            waypoints.clear();
                            this.init();
                        }, 0xFFFF5555, false));
            }

        } else if (activeTab == 3) {
            // TAB 3: Rules & Actions
            this.addRenderableWidget(new CyberpunkButton(contentX, contentY, 180, 20,
                    Component.literal("+ Add New Rule"), b -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new RuleEditModalScreen(this, null, newRule -> {
                                rules.add(newRule);
                                this.init();
                            }));
                        }
                    }, CYAN_MAIN, false));

        } else if (activeTab == 4) {
            // TAB 4: Dialog Linker with Interactive Dropdown
            List<CyberpunkDropdown.DropdownEntry<ResourceLocation>> seqEntries = new ArrayList<>();
            for (ResourceLocation loc : DialogManager.getInstance().getSequenceIds()) {
                seqEntries.add(new CyberpunkDropdown.DropdownEntry<>(loc, Component.literal(loc.toString())));
            }

            dialogDropdown = new CyberpunkDropdown<>(contentX, contentY + 12, 360, 20,
                    Component.literal("Select Dialogue Sequence"));
            dialogDropdown.setOptions(seqEntries);
            dialogDropdown.setOnSelect(entry -> {
                if (entry != null && entry.getValue() != null) {
                    selectedDialogSeq = entry.getValue().toString();
                }
            });
            this.addRenderableWidget(dialogDropdown);

            int testY = contentY + 45;
            this.addRenderableWidget(new CyberpunkButton(contentX, testY, 160, 20,
                    Component.literal("▶ Test Sequence Now"), b -> {
                        if (!selectedDialogSeq.isBlank()) {
                            this.onClose();
                            ModMessages.sendToServer(
                                    new net.dandare21.fracturedutils.network.packet.C2SStartDialogSequencePacket(
                                            selectedDialogSeq.trim()));
                        }
                    }, GOLD, false));

            this.addRenderableWidget(new CyberpunkButton(contentX + 170, testY, 120, 20,
                    Component.literal("Clear Sequence"), b -> {
                        selectedDialogSeq = "";
                        this.init();
                    }, 0xFFFF5555, false));
        }
    }

    private void updateDummy() {
        if (dummyEntity == null)
            return;
        dummyEntity.setDefinition(collectDefinition(), false);
        dummyEntity.refreshDimensions();
    }

    private void setEyeAnchorFromLook() {
        if (this.minecraft == null || this.minecraft.player == null)
            return;
        Vec3 offset = new Vec3(0.0, this.minecraft.player.getEyeHeight(), 0.0);
        this.definition = new NpcDefinition(
                this.definition.id(),
                this.definition.name(),
                this.selectedModelType,
                this.definition.modelLocation(),
                this.definition.textureLocation(),
                this.definition.animationLocation(),
                this.definition.skinValue(),
                this.definition.skinModel(),
                offset,
                this.definition.eyeHeight(),
                this.definition.width(),
                this.definition.height(),
                this.definition.shadowRadius(),
                this.invulnerable,
                this.immortal,
                this.definition.maxHealth(),
                this.definition.attackDamage(),
                this.definition.knockbackResistance(),
                this.noGravity,
                this.pushable,
                this.definition.lookRadius(),
                this.selectedMovementMode,
                this.waypoints,
                this.definition.moveSpeed(),
                this.rules,
                this.definition.defaultDialog(),
                this.showNameTag);
        this.minecraft.player.sendSystemMessage(
                Component.literal("✓ Camera Eye Anchor updated to: " + String.format("%.2f", offset.y))
                        .withStyle(ChatFormatting.AQUA));
    }

    private NpcDefinition collectDefinition() {
        ResourceLocation id = null;
        if (templateIdEditBox != null && !templateIdEditBox.getValue().isBlank()) {
            id = ResourceLocation.tryParse(templateIdEditBox.getValue().trim());
        } else if (currentId != null && !currentId.isBlank()) {
            id = ResourceLocation.tryParse(currentId.trim());
        }
        if (id == null) {
            id = this.definition.id();
        }

        String name = nameEditBox != null ? nameEditBox.getValue()
                : (currentName != null ? currentName : this.definition.name());
        String skinVal = skinValEditBox != null ? skinValEditBox.getValue()
                : (currentSkinVal != null ? currentSkinVal : this.definition.skinValue());
        String skinModel = isSlimModel ? "slim" : "default";

        String modelStr = modelLocEditBox != null ? modelLocEditBox.getValue() : currentModelLoc;
        Optional<ResourceLocation> modelLoc = Optional.empty();
        if (modelStr != null && !modelStr.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(modelStr.trim());
            if (parsed != null && DynamicNpcGeoModel.isValidModel(parsed)) {
                modelLoc = Optional.of(parsed);
            }
        }
        if (modelLoc.isEmpty() && selectedModelType == NpcModelType.GECKOLIB) {
            modelLoc = Optional.of(DynamicNpcGeoModel.FALLBACK_MODEL);
        }

        String texStr = texLocEditBox != null ? texLocEditBox.getValue() : currentTexLoc;
        Optional<ResourceLocation> texLoc = Optional.empty();
        if (texStr != null && !texStr.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(texStr.trim());
            if (parsed != null && DynamicNpcGeoModel.isValidTexture(parsed)) {
                texLoc = Optional.of(parsed);
            }
        }
        if (texLoc.isEmpty() && selectedModelType == NpcModelType.GECKOLIB && modelLoc.isPresent()) {
            texLoc = NpcAssetScanner.findBestMatchingTexture(modelLoc.get());
        }

        String animStr = animLocEditBox != null ? animLocEditBox.getValue() : currentAnimLoc;
        Optional<ResourceLocation> animLoc = Optional.empty();
        if (animStr != null && !animStr.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(animStr.trim());
            if (parsed != null && DynamicNpcGeoModel.isValidAnimation(parsed)) {
                animLoc = Optional.of(parsed);
            }
        }
        if (animLoc.isEmpty() && selectedModelType == NpcModelType.GECKOLIB && modelLoc.isPresent()) {
            animLoc = NpcAssetScanner.findBestMatchingAnimation(modelLoc.get());
        }

        double hp = 20.0;
        String hpStr = maxHealthEditBox != null ? maxHealthEditBox.getValue() : currentMaxHealth;
        if (hpStr != null) {
            try {
                hp = Math.max(1.0, Double.parseDouble(hpStr.trim()));
            } catch (Exception ignored) {
            }
        }

        double dmg = 5.0;
        String dmgStr = attackDamageEditBox != null ? attackDamageEditBox.getValue() : currentAttackDamage;
        if (dmgStr != null) {
            try {
                dmg = Math.max(0.0, Double.parseDouble(dmgStr.trim()));
            } catch (Exception ignored) {
            }
        }

        double kb = 0.0;
        String kbStr = knockbackResEditBox != null ? knockbackResEditBox.getValue() : currentKnockbackRes;
        if (kbStr != null) {
            try {
                kb = Math.max(0.0, Math.min(1.0, Double.parseDouble(kbStr.trim())));
            } catch (Exception ignored) {
            }
        }

        float spd = 0.25F;
        String spdStr = moveSpeedEditBox != null ? moveSpeedEditBox.getValue() : currentMoveSpeed;
        if (spdStr != null) {
            try {
                spd = Math.max(0.05F, Float.parseFloat(spdStr.trim()));
            } catch (Exception ignored) {
            }
        }

        float radius = 8.0F;
        String radStr = lookRadiusEditBox != null ? lookRadiusEditBox.getValue() : currentLookRadius;
        if (radStr != null) {
            try {
                radius = Float.parseFloat(radStr.trim());
            } catch (Exception ignored) {
            }
        }

        float w = 0.6F;
        String wStr = widthEditBox != null ? widthEditBox.getValue() : currentWidth;
        if (wStr != null) {
            try {
                w = Math.max(0.1F, Float.parseFloat(wStr.trim()));
            } catch (Exception ignored) {
            }
        }

        float h = 1.8F;
        String hStr = heightEditBox != null ? heightEditBox.getValue() : currentHeight;
        if (hStr != null) {
            try {
                h = Math.max(0.1F, Float.parseFloat(hStr.trim()));
            } catch (Exception ignored) {
            }
        }

        float eyeH = 1.62F;
        if (currentEyeHeight != null) {
            try {
                eyeH = Float.parseFloat(currentEyeHeight.trim());
            } catch (Exception ignored) {
            }
        }

        Optional<ResourceLocation> defDialog = Optional.empty();
        if (!selectedDialogSeq.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(selectedDialogSeq.trim());
            if (parsed != null)
                defDialog = Optional.of(parsed);
        }

        return new NpcDefinition(
                id,
                name,
                this.selectedModelType,
                modelLoc,
                texLoc,
                animLoc,
                skinVal,
                skinModel,
                this.definition.cameraOffset(),
                eyeH,
                w,
                h,
                this.definition.shadowRadius(),
                this.invulnerable,
                this.immortal,
                hp,
                dmg,
                kb,
                this.noGravity,
                this.pushable,
                radius,
                this.selectedMovementMode,
                this.waypoints,
                spd,
                this.rules,
                defDialog,
                this.showNameTag);
    }

    private void saveNpc(boolean toDatapack, boolean toEntity, boolean spawnInWorld) {
        this.definition = collectDefinition();
        ModMessages.sendToServer(
                new C2SSaveNpcPacket(this.targetEntityId, this.definition, toDatapack, toEntity, spawnInWorld));
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int boxW = Math.min(this.width - 20, 600);
        int boxH = Math.min(this.height - 20, 370);
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;
        int previewX = boxX + boxW - 165;
        int previewY = boxY + 50;

        if (mouseX >= previewX && mouseX <= previewX + 150 && mouseY >= previewY && mouseY <= previewY + 240) {
            if (button == 0) {
                isDraggingPreview = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
        }

        // Handle rule card clicks in Tab 3
        if (activeTab == 3) {
            int contentW = boxW - 185;
            int ruleY = boxY + 86;
            for (int i = 0; i < Math.min(4, rules.size()); i++) {
                int editBtnX = boxX + contentW - 55;
                int delBtnX = boxX + contentW - 25;
                if (mouseY >= ruleY && mouseY <= ruleY + 28) {
                    final int idx = i;
                    if (mouseX >= editBtnX && mouseX <= editBtnX + 25) {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new RuleEditModalScreen(this, rules.get(idx), updated -> {
                                rules.set(idx, updated);
                                this.init();
                            }));
                        }
                        return true;
                    } else if (mouseX >= delBtnX && mouseX <= delBtnX + 25) {
                        rules.remove(idx);
                        this.init();
                        return true;
                    }
                }
                ruleY += 34;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingPreview && button == 0) {
            previewYaw += (float) (mouseX - lastMouseX) * 1.5F;
            previewPitch = Math.max(-45.0F, Math.min(45.0F, previewPitch - (float) (mouseY - lastMouseY) * 1.5F));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingPreview = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int boxW = Math.min(this.width - 20, 600);
        int boxH = Math.min(this.height - 20, 370);
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        // Frame
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, CYAN_BG);
        guiGraphics.renderOutline(boxX, boxY, boxW, boxH, CARD_BORDER);

        // Header Title
        guiGraphics.drawString(this.font, "✦ NPC STUDIO", boxX + 15, boxY + 10, CYAN_MAIN, false);
        guiGraphics.drawString(this.font, "ID:", boxX + 114, boxY + 10, GRAY_TEXT, false);

        // Content Area outline
        int contentW = boxW - 185;
        int contentH = boxH - 95;
        guiGraphics.renderOutline(boxX + 10, boxY + 52, contentW, contentH, 0x4400E5FF);

        // Labels inside tabs
        if (activeTab == 0) {
            guiGraphics.drawString(this.font, "Display Name:", boxX + 15, boxY + 58, 0xFFFFFFFF, false);
            if (selectedModelType == NpcModelType.PLAYER) {
                guiGraphics.drawString(this.font, "Skin (Username, UUID, or Resource Path):", boxX + 15, boxY + 118,
                        0xFFFFFFFF, false);
                guiGraphics.drawString(this.font, "Skin Presets:", boxX + 15, boxY + 152, GOLD, false);
            } else {
                guiGraphics.drawString(this.font, "Geo Model (.geo.json):", boxX + 15, boxY + 114, 0xFFFFFFFF, false);
                guiGraphics.drawString(this.font, "Texture (.png):", boxX + 15, boxY + 148, 0xFFFFFFFF, false);
                guiGraphics.drawString(this.font, "Animations (.animation.json):", boxX + 15, boxY + 182, 0xFFFFFFFF,
                        false);
            }
        } else if (activeTab == 1) {
            guiGraphics.drawString(this.font, "Max Health:", boxX + 15, boxY + 62, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Attack Damage:", boxX + 205, boxY + 62, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Knockback Res:", boxX + 15, boxY + 90, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Move Speed:", boxX + 205, boxY + 90, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Hitbox Width:", boxX + 15, boxY + 118, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Hitbox Height:", boxX + 205, boxY + 118, 0xFFFFFFFF, false);
        } else if (activeTab == 2) {
            guiGraphics.drawString(this.font, "Look Radius:", boxX + 205, boxY + 62, 0xFFFFFFFF, false);
            if (selectedMovementMode == NpcMovementMode.PATROL) {
                guiGraphics.drawString(this.font, "Waypoints: " + waypoints.size() + " recorded", boxX + 15, boxY + 114,
                        GOLD, false);
                int wpListY = boxY + 130;
                for (int i = 0; i < Math.min(5, waypoints.size()); i++) {
                    Vec3 wp = waypoints.get(i);
                    guiGraphics.drawString(this.font, String.format("#%d: (%.1f, %.1f, %.1f)", i + 1, wp.x, wp.y, wp.z),
                            boxX + 15, wpListY, 0xFFCCCCCC, false);
                    wpListY += 12;
                }
            }
        } else if (activeTab == 3) {
            guiGraphics.drawString(this.font, "Configured Rules: " + rules.size(), boxX + 15, boxY + 84, GOLD, false);
            int ruleY = boxY + 104;
            for (int i = 0; i < Math.min(4, rules.size()); i++) {
                NpcRule r = rules.get(i);
                guiGraphics.fill(boxX + 15, ruleY, boxX + contentW - 10, ruleY + 28, 0x66081420);
                guiGraphics.renderOutline(boxX + 15, ruleY, contentW - 25, 28, 0x8800E5FF);
                guiGraphics.drawString(this.font, "WHEN " + r.trigger().name(), boxX + 20, ruleY + 4, CYAN_MAIN, false);
                String actionsSummary = r.actions().isEmpty() ? "None" : r.actions().get(0).type();
                guiGraphics.drawString(this.font, "THEN " + actionsSummary, boxX + 20, ruleY + 16, 0xFFFFFFFF, false);

                // Edit and Delete buttons
                guiGraphics.drawString(this.font, "✏", boxX + contentW - 50, ruleY + 9, GOLD, false);
                guiGraphics.drawString(this.font, "✕", boxX + contentW - 25, ruleY + 9, 0xFFFF5555, false);

                ruleY += 34;
            }
        } else if (activeTab == 4) {
            guiGraphics.drawString(this.font, "Selected Sequence ID:", boxX + 15, boxY + 58, 0xFFFFFFFF, false);
            if (!selectedDialogSeq.isEmpty()) {
                guiGraphics.drawString(this.font, "Active: " + selectedDialogSeq, boxX + 15, boxY + 90, CYAN_MAIN,
                        false);
            }
        }

        // Live 3D Viewport Box
        int previewX = boxX + boxW - 165;
        int previewY = boxY + 52;
        int previewW = 150;
        int previewH = 238;
        guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0x88040810);
        guiGraphics.renderOutline(previewX, previewY, previewW, previewH, CARD_BORDER);
        guiGraphics.drawCenteredString(this.font, "3D PREVIEW (Drag)", previewX + previewW / 2, previewY + 6,
                GRAY_TEXT);

        // Render Entity in 3D Viewport
        if (dummyEntity != null) {
            int entityCenterX = previewX + previewW / 2;
            int entityCenterY = previewY + previewH - 30;
            renderPreviewEntity(guiGraphics, entityCenterX, entityCenterY, 55);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void onGeoModelSelected(String chosen) {
        this.currentModelLoc = chosen;
        if (this.modelLocEditBox != null) {
            this.modelLocEditBox.setValue(chosen);
        }

        ResourceLocation modelRes = ResourceLocation.tryParse(chosen);
        if (modelRes != null) {
            // Intelligent auto-detection of matching texture
            Optional<ResourceLocation> matchedTex = NpcAssetScanner.findBestMatchingTexture(modelRes);
            if (matchedTex.isPresent()) {
                String tex = matchedTex.get().toString();
                this.currentTexLoc = tex;
                if (this.texLocEditBox != null) {
                    this.texLocEditBox.setValue(tex);
                }
            }

            // Intelligent auto-detection of matching animation
            Optional<ResourceLocation> matchedAnim = NpcAssetScanner.findBestMatchingAnimation(modelRes);
            if (matchedAnim.isPresent()) {
                String anim = matchedAnim.get().toString();
                this.currentAnimLoc = anim;
                if (this.animLocEditBox != null) {
                    this.animLocEditBox.setValue(anim);
                }
            }

            // Intelligent auto-detection of model dimensions (hitbox width, height, eye height)
            NpcAssetScanner.ModelDimensions dims = NpcAssetScanner.computeModelDimensions(modelRes);
            this.currentWidth = String.format(Locale.ROOT, "%.2f", dims.width());
            this.currentHeight = String.format(Locale.ROOT, "%.2f", dims.height());
            this.currentEyeHeight = String.format(Locale.ROOT, "%.2f", dims.eyeHeight());
            if (this.widthEditBox != null) {
                this.widthEditBox.setValue(this.currentWidth);
            }
            if (this.heightEditBox != null) {
                this.heightEditBox.setValue(this.currentHeight);
            }
        }

        this.definition = collectDefinition();
        updateDummy();
    }

    private void renderPreviewEntity(GuiGraphics guiGraphics, int x, int y, int scale) {
        if (dummyEntity == null)
            return;
        dummyEntity.tickCount++; // Ensures live GeckoLib animation playback in 3D preview!
        dummyEntity.yBodyRot = previewYaw;
        dummyEntity.setYRot(previewYaw);
        dummyEntity.setXRot(previewPitch);
        dummyEntity.yHeadRot = previewYaw;
        dummyEntity.yHeadRotO = previewYaw;

        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    guiGraphics, x, y, scale, previewYaw, previewPitch, dummyEntity);
        } catch (Throwable t) {
            // Prevent preview rendering glitches from crashing the GUI
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
