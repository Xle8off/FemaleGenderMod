package com.wildfire.neoforge.appearance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.NativeImage;
import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.client.render.GenderRenderState;
import com.wildfire.client.ClientHelper;
import com.wildfire.common.WildfireGender;
import com.wildfire.api.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Garments and cosmetics rendered over the player's skin. */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public record GuiPreview(Model.Simple model, Identifier texture) {}

    /** Uses the exact baked catalog geometry and texture in the GUI picture-in-picture renderer. */
    public static GuiPreview guiPreview(int networkId, boolean headwear) {
        // Headwear IDs 1 and 2 overlap the first pet IDs in the remote catalog.
        // Resolve built-in accessories before looking up catalog pets.
        if (headwear && (networkId == 1 || networkId == 2)) {
            var model = networkId == 1 ? CAP : HALO;
            var accessory = model.head.getChild(networkId == 1 ? "cap" : "halo");
            return new GuiPreview(new Model.Simple(accessory, RenderTypes::entityCutout), accessoryPreviewTexture(networkId));
        }
        var entry = CosmeticCatalogClient.entry(networkId);
        int nativeChibi = headwear ? 0 : nativeChibi(entry);
        if (nativeChibi != 0) {
            CHIBI[nativeChibi].setupAnim(new AvatarRenderState());
            return new GuiPreview(new Model.Simple(CHIBI[nativeChibi].root(), RenderTypes::entityCutout), CHIBI_TEXTURES[nativeChibi]);
        }
        if (entry != null) {
            var asset = dynamic(networkId);
            if (asset == null) return null;
            asset.model.setupAnim(new AvatarRenderState());
            // The catalog model may have been baked with its cosmetic bone at the
            // root rather than under BODY. The cached GUI model knows its own root.
            return new GuiPreview(asset.guiModel, asset.texture);
        }
        if (!headwear && networkId > 0 && networkId < CHIBI.length) {
            CHIBI[networkId].setupAnim(new AvatarRenderState());
            return new GuiPreview(new Model.Simple(CHIBI[networkId].root(), RenderTypes::entityCutout),
                CHIBI_TEXTURES[networkId]);
        }
        return null;
    }
    public static java.util.List<GuiPreview> relationshipGuiPreviews(java.util.UUID owner) {
        var partners = RelationshipClient.spouses(owner);
        int count = Math.max(2, Math.min(3, partners.size() + 1));
        var result = new java.util.ArrayList<GuiPreview>(count);
        for (int i = 0; i < count; i++) {
            java.util.UUID person = i == 0 ? owner : i - 1 < partners.size() ? partners.get(i - 1) : null;
            Identifier texture = person == null ? null : ChibiSkinClient.texture(person);
            if (texture == null) texture = PARTNER_FALLBACK;
            var model = person != null && ChibiSkinClient.slim(person) ? COUPLE_SLIM[i] : COUPLE_CHIBI[i];
            ((ChibiModel)model).poseAs(i, count);
            ((ChibiModel)model).sparseSleeves = person == null ? 0 : ChibiSkinClient.sparseSleeves(person);
            model.setupAnim(new AvatarRenderState());
            result.add(new GuiPreview(new Model.Simple(model.root(), RenderTypes::entityCutout), texture));
        }
        return result;
    }
    public static final Identifier FABRIC = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final java.util.Map<Integer, Identifier> ACCESSORY_PREVIEW_TEXTURES = new java.util.HashMap<>();

    private static Identifier accessoryPreviewTexture(int accessory) {
        return ACCESSORY_PREVIEW_TEXTURES.computeIfAbsent(accessory, kind -> {
            var id = Identifier.fromNamespaceAndPath("appearance_sync", kind == 1 ? "preview/cap" : "preview/halo");
            var image = new NativeImage(16, 16, true);
            int argb = kind == 1 ? 0xFF35434A : 0xFFE1C777;
            int abgr = argb & 0xFF00FF00 | (argb & 0x00FF0000) >>> 16 | (argb & 0x000000FF) << 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setPixelABGR(x, y, abgr);
            var texture = new DynamicTexture(() -> "Headwear preview " + kind, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            return id;
        });
    }
    private static final Identifier PARTNER_FALLBACK = Identifier.fromNamespaceAndPath("female_gender_mod",
        "textures/chibi_partner_fallback.png");
    private static Identifier unknownChibiTexture;

    private static Identifier unknownChibiTexture() {
        if (unknownChibiTexture != null) return unknownChibiTexture;
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setPixelABGR(x, y, 0xFFB9A8A2);
        int[][] question = {{1,1,1,1,1},{0,0,0,0,1},{0,0,1,1,1},{0,0,1,0,0},{0,0,0,0,0},{0,0,1,0,0}};
        for (int y = 0; y < question.length; y++) for (int x = 0; x < question[y].length; x++)
            if (question[y][x] != 0) image.setPixelABGR(9 + x, 9 + y, 0xFF332E47);
        unknownChibiTexture = Identifier.fromNamespaceAndPath("springplus", "unknown_chibi");
        DynamicTexture texture = new DynamicTexture(() -> "Unknown partner chibi", image);
        Minecraft.getInstance().getTextureManager().register(unknownChibiTexture,
            texture);
        texture.upload();
        return unknownChibiTexture;
    }
    private static final PlayerModel SHIRT_WIDE = createModel(0, false);
    private static final PlayerModel SHIRT_SLIM = createModel(0, true);
    private static final PlayerModel SHIRT_WIDE_FLAT = createModel(2, false);
    private static final PlayerModel SHIRT_SLIM_FLAT = createModel(2, true);
    private static final PlayerModel PANTS = createModel(1, false);
    private static final PlayerModel HD_SHIRT_WIDE = createModel(0, false, true);
    private static final PlayerModel HD_SHIRT_SLIM = createModel(0, true, true);
    private static final PlayerModel HD_SHIRT_WIDE_FLAT = createModel(2, false, true);
    private static final PlayerModel HD_SHIRT_SLIM_FLAT = createModel(2, true, true);
    private static final PlayerModel HD_PANTS = createModel(1, false, true);
    private static final PlayerModel CAP = createAccessoryModel(1);
    private static final PlayerModel HALO = createAccessoryModel(2);
    private static final PlayerModel[] CHIBI = {null, createChibiModel(false), createChibiModel(false),
        createChibiModel(true), createNancyChibiModel()};
    private static final PlayerModel[] COUPLE_CHIBI = {createChibiModel(false), createChibiModel(false), createChibiModel(false)};
    private static final PlayerModel[] COUPLE_SLIM = {createChibiModel(true), createChibiModel(true), createChibiModel(true)};
    private static final PlayerModel RELATIONSHIP_HEART_OUTLINE = createRelationshipHeart(true);
    private static final PlayerModel RELATIONSHIP_HEART = createRelationshipHeart(false);
    private static final Identifier[] CHIBI_TEXTURES = {null,
        Identifier.fromNamespaceAndPath("female_gender_mod", "textures/chibi_imzap.png"),
        Identifier.fromNamespaceAndPath("female_gender_mod", "textures/chibi_lisa.png"),
        Identifier.fromNamespaceAndPath("female_gender_mod", "textures/chibi_xle8.png"),
        Identifier.fromNamespaceAndPath("female_gender_mod", "textures/chibi_nekes.png")};
    private static final int[] SHIRT_COLORS = {0, 0xFFE9E9E5, 0xFF292D34, 0xFF8C5270};
    private static final int[] PANTS_COLORS = {0, 0xFF252A35, 0xFF6E7072, 0xFF755242};
    private static final java.util.Map<java.util.UUID, PetFollower> PET_FOLLOWERS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, DynamicCosmetic> DYNAMIC = new java.util.HashMap<>();
    private static FirstPersonPet firstPersonPet;
    private record FirstPersonPet(AvatarRenderState state, java.util.UUID owner, int pet) {}
    private static final class PetFollower {
        double x, y, z, heading;
        long lastUpdate;
        int kind;

        PetFollower(double x, double y, double z, double heading, long now, int kind) {
            this.x = x; this.y = y; this.z = z;
            this.heading = heading;
            this.lastUpdate = now;
            this.kind = kind;
        }
    }

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector queue, int light, AvatarRenderState state,
                       float limbAngle, float limbDistance) {
        GenderRenderState gender = ClientHelper.INSTANCE.getRenderState(state);
        if (gender == null || state.isInvisible || !com.wildfire.client.config.CosmeticPreferences.cosmeticsVisible()) return;
        var cosmetics = AppearanceState.cosmetics(gender.entityUUID);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        var garmentType = RenderTypes.entityCutout(FABRIC);
        Identifier shirtTexture=GarmentClient.texture(gender.entityUUID,0);
        Identifier pantsTexture=GarmentClient.texture(gender.entityUUID,1);
        boolean slim=state.skin.model()==PlayerModelType.SLIM;
        if (!HumanoidArmorLayer.shouldRender(state.chestEquipment, EquipmentSlot.CHEST) && (cosmetics.shirt()!=0 || shirtTexture!=null))
            render(queue, shirtTexture!=null
                    ? (gender.gender==Gender.FEMALE ? (slim?HD_SHIRT_SLIM:HD_SHIRT_WIDE) : (slim?HD_SHIRT_SLIM_FLAT:HD_SHIRT_WIDE_FLAT))
                    : (gender.gender==Gender.FEMALE ? (slim?SHIRT_SLIM:SHIRT_WIDE) : (slim?SHIRT_SLIM_FLAT:SHIRT_WIDE_FLAT)),
                state, pose, shirtTexture==null?garmentType:RenderTypes.entityCutout(shirtTexture), light, overlay,
                shirtTexture==null?color(SHIRT_COLORS,cosmetics.shirt()):0xFFFFFFFF);
        if (!HumanoidArmorLayer.shouldRender(state.legsEquipment, EquipmentSlot.LEGS) && (cosmetics.pants()!=0 || pantsTexture!=null))
            render(queue, pantsTexture==null?PANTS:HD_PANTS, state, pose,
                pantsTexture==null?garmentType:RenderTypes.entityCutout(pantsTexture), light, overlay,
                pantsTexture==null?color(PANTS_COLORS,cosmetics.pants()):0xFFFFFFFF);
        if (!HumanoidArmorLayer.shouldRender(state.headEquipment, EquipmentSlot.HEAD)) {
            if (cosmetics.headwear() == 1) render(queue, CAP, state, pose, garmentType, light, overlay, 0xFF35434A);
            if (cosmetics.headwear() == 2) render(queue, HALO, state, pose, garmentType, light, overlay, 0xFFE1C777);
            if (cosmetics.headwear() >= 5) {
                var entry=CosmeticCatalogClient.entry(cosmetics.headwear());
                var asset=dynamic(cosmetics.headwear());
                if (entry!=null && entry.kind().equals("headwear") && asset!=null) {
                    pose.pushPose();getParentModel().head.translateAndRotate(pose);
                    render(queue,asset.model,state,pose,RenderTypes.entityCutout(asset.texture),light,overlay,0xFFFFFFFF);
                    pose.popPose();
                }
            }
        }
        int pet = cosmetics.pet();
        var petEntry = CosmeticCatalogClient.entry(pet);
        var petAsset = petEntry == null ? null : dynamic(pet);
        if (petEntry != null && petEntry.kind().equals("pet_relationship"))
            renderRelationship(queue,state,pose,light,overlay,gender.entityUUID,pet);
        else if (nativeChibi(petEntry) != 0)
            renderPet(queue,CHIBI[pet],state,pose,RenderTypes.entityCutout(CHIBI_TEXTURES[pet]),
                light,overlay,gender.entityUUID,pet,false);
        else if (petEntry != null && petEntry.kind().startsWith("pet_") && petAsset != null)
            renderPet(queue,petAsset.model,state,pose,RenderTypes.entityCutout(petAsset.texture),
                light,overlay,gender.entityUUID,pet,petEntry.kind().equals("pet_walking"));
        else if (pet > 0 && pet < CHIBI.length)
            renderPet(queue, CHIBI[pet], state, pose, RenderTypes.entityCutout(CHIBI_TEXTURES[pet]),
                light, overlay, gender.entityUUID, pet, false);
    }

    private static int color(int[] palette, int index) {
        return index >= 0 && index < palette.length ? palette[index] : 0;
    }

    public static int shirtColor(int index) { return color(SHIRT_COLORS, index); }
    public static void clearDynamicCache(){
        var manager=Minecraft.getInstance().getTextureManager();
        DYNAMIC.values().forEach(value->manager.release(value.texture));DYNAMIC.clear();
    }
    private static DynamicCosmetic dynamic(int networkId){
        var entry=CosmeticCatalogClient.entry(networkId);
        if(entry==null)return null;
        var cached=DYNAMIC.get(networkId);
        var pack=CosmeticCatalogClient.packageFor(networkId);
        if(pack==null)return cached;
        if(cached!=null&&cached.hash.equals(entry.hash()))return cached;
        if(cached!=null)Minecraft.getInstance().getTextureManager().release(cached.texture);
        DYNAMIC.remove(networkId);
        try{var loaded=DynamicCosmetic.create(networkId,entry.hash(),pack,entry.kind().equals("headwear"));DYNAMIC.put(networkId,loaded);return loaded;}
        catch(Exception error){WildfireGender.LOGGER.warn("Failed to create cosmetic {}: {}",entry.id(),error.toString());return null;}
    }

    /** Small voxel models, attached to animated player bones rather than painted on the skin. */
    private static PlayerModel createAccessoryModel(int accessory) {
        // Build only the animated bones. Reusing PlayerModel.createMesh here can
        // render the vanilla body with the accessory tint over the player's skin.
        var mesh = emptyPlayerMesh();
        var root = mesh.getRoot();
        var cubes = CubeListBuilder.create().texOffs(0, 0);
        if (accessory == 1) {
            cubes.addBox(-4.3f, -8.7f, -4.3f, 8.6f, 3f, 8.6f)
                .addBox(-4.4f, -6.1f, -6.0f, 8.8f, 0.55f, 2f);
            root.getChild(PartNames.HEAD).addOrReplaceChild("cap", cubes, PartPose.offset(0, -1.5f, 0));
        } else if (accessory == 2) {
            cubes.addBox(-4f, -11.3f, -4f, 8f, 0.5f, 0.7f)
                .addBox(-4f, -11.3f, 3.3f, 8f, 0.5f, 0.7f)
                .addBox(-4f, -11.3f, -3.3f, 0.7f, 0.5f, 6.6f)
                .addBox(3.3f, -11.3f, -3.3f, 0.7f, 0.5f, 6.6f);
            root.getChild(PartNames.HEAD).addOrReplaceChild("halo", cubes, PartPose.offset(0, -1f, 0));
        }
        return new AccessoryModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(),
            true, switch (accessory) {
                case 1 -> "cap";
                default -> "halo";
            });
    }

    private static PlayerModel createChibiModel(boolean slim) {
        var mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        return new ChibiModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
    }

    private static PlayerModel createNancyChibiModel() {
        var model=(ChibiModel)createChibiModel(true);
        model.hideOuter=true;
        return model;
    }

    private static int nativeChibi(CosmeticCatalogPackets.Entry entry) {
        if (entry == null) return 0;
        return switch (entry.networkId()) {
            case 1 -> entry.id().equals("chibi_imzap") && entry.hash().equals("70338d1adf412864f1937f179659d0fcaa26fa55e5018617f564b4ac5643ac0c") ? 1 : 0;
            case 2 -> entry.id().equals("chibi_lisa") && entry.hash().equals("6405134fb64eee5418c740b18c1407d2673044aad84f45558e98459d5ea4788d") ? 2 : 0;
            case 3 -> entry.id().equals("chibi_xle8") && entry.hash().equals("683df5619f6d37ac7d0d26008d8e64c24868cacb7b970001ed80feca000be385") ? 3 : 0;
            case 4 -> entry.id().equals("chibi_nekes") && entry.hash().equals("41e195e9d81497fbcd604a4cc29241fb717b953b18c7873bd591da09dd1012dc") ? 4 : 0;
            default -> 0;
        };
    }

    private static PlayerModel createRelationshipHeart(boolean outline) {
        var mesh = emptyPlayerMesh();
        String[] pixels={".##...##.","####.####","#########","#########",
            ".#######.","..#####..","...###...","....#...."};
        var cubes=CubeListBuilder.create().texOffs(0,0);
        for(int row=0;row<pixels.length;row++)for(int column=0;column<9;column++){
            if(pixels[row].charAt(column)!='#')continue;
            boolean edge=row==0||row==pixels.length-1||column==0||column==8
                ||pixels[row-1].charAt(column)!='#'||pixels[row+1].charAt(column)!='#'
                ||pixels[row].charAt(column-1)!='#'||pixels[row].charAt(column+1)!='#';
            if(edge==outline)cubes.addBox(column-4.5f,-35+row,-0.2f,1,1,0.4f);
        }
        mesh.getRoot().getChild(PartNames.BODY).addOrReplaceChild("heart",
            cubes, PartPose.ZERO);
        return new AccessoryModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false, "heart");
    }

    /** Vanilla skin UVs and every outer layer stay intact while the silhouette becomes chibi. */
    private static final class ChibiModel extends PlayerModel {
        private int holdIndex = -1, holdCount;
        private int sparseSleeves;
        private boolean hideOuter;
        private final boolean slimArms;
        private ChibiModel(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
            super(root, slim);
            slimArms = slim;
        }

        private void poseAs(int index, int count) { holdIndex = index; holdCount = count; }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            head.xScale = 1.72f;
            head.zScale = 1.9f;
            head.yScale = 1.85f;
            body.xScale = 0.60f;
            body.zScale = 0.80f;
            // In 26.2 overlays are CHILDREN of the animated base parts.
            // World-space offsets/scales on sleeves apply the arm pose twice.
            for(var outer:new net.minecraft.client.model.geom.ModelPart[]{hat,jacket,leftSleeve,rightSleeve,leftPants,rightPants}){
                outer.x=outer.y=outer.z=0;
                outer.xRot=outer.yRot=outer.zRot=0;
                outer.xScale=outer.yScale=outer.zScale=1;
            }
            leftArm.x = slimArms ? 4.5f : 3.5f;
            rightArm.x = -leftArm.x;
            leftArm.xScale = rightArm.xScale = slimArms ? 0.85f : 0.56f;
            leftArm.yScale = rightArm.yScale = slimArms ? 0.90f : 0.70f;
            leftLeg.x = 1.2f;
            rightLeg.x = -1.2f;
            leftLeg.xScale = rightLeg.xScale = 0.58f;
            leftLeg.yScale = rightLeg.yScale = 0.42f;
            leftLeg.xRot *= 0.35f;
            rightLeg.xRot *= 0.35f;
            leftArm.zRot += 0.12f + (float)Math.sin(state.ageInTicks * 0.16f) * 0.08f;
            rightArm.zRot -= 0.12f + (float)Math.sin(state.ageInTicks * 0.16f) * 0.08f;
            if (holdIndex >= 0) {
                // Neighbours are to the model's +X for the left chibi and -X for the right.
                // Bend the inner arms toward each other, not the outer arms away from them.
                if (holdIndex < holdCount - 1) leftArm.zRot = -0.72f;
                if (holdIndex > 0) rightArm.zRot = 0.72f;
                leftArm.yScale *= 0.8f;
                rightArm.yScale *= 0.8f;
            }
            hat.visible = jacket.visible = !hideOuter;
            leftSleeve.visible = !hideOuter && (sparseSleeves & 2) == 0;
            rightSleeve.visible = !hideOuter && (sparseSleeves & 1) == 0;
            leftPants.visible = rightPants.visible = !hideOuter;
        }
    }

    private static final class AccessoryModel extends PlayerModel {
        private final boolean attachedToHead;
        private final String partName;

        private AccessoryModel(net.minecraft.client.model.geom.ModelPart root, boolean attachedToHead, String partName) {
            super(root, false);
            this.attachedToHead = attachedToHead;
            this.partName = partName;
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
            var bone = attachedToHead ? head : body;
            if (!bone.visible) return;
            pose.pushPose();
            bone.translateAndRotate(pose);
            bone.getChild(partName).render(pose, vertices, light, overlay, color);
            pose.popPose();
        }
    }

    private static MeshDefinition emptyPlayerMesh() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var empty = CubeListBuilder.create();
        var head = root.addOrReplaceChild(PartNames.HEAD, empty, PartPose.ZERO);
        head.addOrReplaceChild(PartNames.HAT, empty, PartPose.ZERO);
        var body = root.addOrReplaceChild(PartNames.BODY, empty, PartPose.ZERO);
        body.addOrReplaceChild("jacket", empty, PartPose.ZERO);
        var leftArm = root.addOrReplaceChild(PartNames.LEFT_ARM, empty, PartPose.offset(5f, 2f, 0f));
        leftArm.addOrReplaceChild("left_sleeve", empty, PartPose.ZERO);
        var rightArm = root.addOrReplaceChild(PartNames.RIGHT_ARM, empty, PartPose.offset(-5f, 2f, 0f));
        rightArm.addOrReplaceChild("right_sleeve", empty, PartPose.ZERO);
        var leftLeg = root.addOrReplaceChild(PartNames.LEFT_LEG, empty, PartPose.offset(1.9f, 12f, 0f));
        leftLeg.addOrReplaceChild("left_pants", empty, PartPose.ZERO);
        var rightLeg = root.addOrReplaceChild(PartNames.RIGHT_LEG, empty, PartPose.offset(-1.9f, 12f, 0f));
        rightLeg.addOrReplaceChild("right_pants", empty, PartPose.ZERO);
        return mesh;
    }

    private static void render(SubmitNodeCollector queue, PlayerModel model, AvatarRenderState state, PoseStack pose,
                               net.minecraft.client.renderer.rendertype.RenderType type, int light, int overlay, int color) {
        if (color != 0) queue.submitModel(model, state, pose, type, light, overlay, color, null, state.outlineColor, null);
    }

    private static void renderPet(SubmitNodeCollector queue, PlayerModel model, AvatarRenderState state,
                                  PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                  int light, int overlay, java.util.UUID uuid, int kind, boolean walking) {
        renderPet(queue,model,state,pose,type,light,overlay,uuid,kind,walking,0,0);
    }

    private static void renderRelationship(SubmitNodeCollector queue, AvatarRenderState state, PoseStack pose,
                                           int light, int overlay, java.util.UUID owner, int kind) {
        var spouses = RelationshipClient.spouses(owner);
        int count = Math.max(2, Math.min(3, spouses.size() + 1));
        java.util.UUID[] people = new java.util.UUID[count];
        people[0] = owner;
        int page = spouses.size() > 2 ? (int)((System.currentTimeMillis() / 10_000L) % spouses.size()) : 0;
        for (int i = 1; i < count; i++)
            people[i] = spouses.isEmpty() ? null : spouses.get((page + i - 1) % spouses.size());
        Identifier[] textures = new Identifier[people.length];
        for (int i = 0; i < people.length; i++) {
            textures[i] = people[i] == null ? null : ChibiSkinClient.texture(people[i]);
            if (textures[i] == null) textures[i] = PARTNER_FALLBACK;
        }
        for (int i = 0; i < people.length; i++) {
            float offset = (i - (people.length - 1) / 2f) * 0.38f;
            var model = (ChibiModel)(people[i] != null && ChibiSkinClient.slim(people[i]) ? COUPLE_SLIM[i] : COUPLE_CHIBI[i]);
            model.poseAs(i, people.length);
            model.sparseSleeves = people[i] == null ? 0 : ChibiSkinClient.sparseSleeves(people[i]);
            renderPet(queue,model,state,pose,
                RenderTypes.entityCutout(textures[i]),light,overlay,
                owner,kind,false,offset,0);
        }
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        float heartTurn = camera.isInitialized()
            ? (float)(Math.toDegrees(Math.atan2(camera.position().x - state.x,
                camera.position().z - state.z)) - state.bodyRot + 75f) : 75f;
        RELATIONSHIP_HEART_OUTLINE.setupAnim(state);
        RELATIONSHIP_HEART.setupAnim(state);
        renderPet(queue, RELATIONSHIP_HEART_OUTLINE, state, pose, RenderTypes.entityCutout(FABRIC),
            light, overlay, owner, kind, false, 0, heartTurn, 0xFF111111,
            RELATIONSHIP_HEART, 0xFFE8323E);
    }

    private static void renderPet(SubmitNodeCollector queue, PlayerModel model, AvatarRenderState state,
                                  PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                  int light, int overlay, java.util.UUID uuid, int kind, boolean walking,
                                  float offset, float turn) {
        renderPet(queue,model,state,pose,type,light,overlay,uuid,kind,walking,offset,turn,0xFFFFFFFF);
    }

    private static void renderPet(SubmitNodeCollector queue, PlayerModel model, AvatarRenderState state,
                                  PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                  int light, int overlay, java.util.UUID uuid, int kind, boolean walking,
                                  float offset, float turn, int color) {
        renderPet(queue, model, state, pose, type, light, overlay, uuid, kind, walking,
            offset, turn, color, null, 0);
    }

    private static void renderPet(SubmitNodeCollector queue, PlayerModel model, AvatarRenderState state,
                                  PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                  int light, int overlay, java.util.UUID uuid, int kind, boolean walking,
                                  float offset, float turn, int color, PlayerModel secondModel, int secondColor) {
        double yaw = Math.toRadians(state.bodyRot);
        var level = Minecraft.getInstance().level;
        var owner = level == null ? null : level.getPlayerByUUID(uuid);
        double sizeExtra = owner == null ? 0 : Math.max(0, owner.getBbWidth() - 0.6) * 0.75;
        double side = 1.45 + sizeExtra;
        double behind = 1.25 + sizeExtra;
        long now = System.nanoTime();
        PetFollower follower = PET_FOLLOWERS.get(uuid);
        double targetX = state.x + Math.cos(yaw) * side - Math.sin(yaw) * behind;
        double targetZ = state.z + Math.sin(yaw) * side - Math.cos(yaw) * behind;
        if (follower == null || follower.kind != kind
            || Math.abs(now - follower.lastUpdate) > 10_000_000_000L
            || Math.hypot(targetX - follower.x, targetZ - follower.z) > 12) {
            follower = new PetFollower(targetX, state.y, targetZ, yaw, now, kind);
            PET_FOLLOWERS.put(uuid, follower);
        } else {
            double seconds = Math.min((now - follower.lastUpdate) / 1_000_000_000.0, 0.1);
            double headingDelta = Math.atan2(Math.sin(yaw - follower.heading), Math.cos(yaw - follower.heading));
            follower.heading += headingDelta * (1.0 - Math.exp(-seconds * 2.5));
            targetX = state.x + Math.cos(follower.heading) * side - Math.sin(follower.heading) * behind;
            targetZ = state.z + Math.sin(follower.heading) * side - Math.cos(follower.heading) * behind;
            // A slow pursuit keeps the pet visibly behind during running and turns.
            double blend = 1.0 - Math.exp(-seconds * 1.25);
            follower.x += (targetX - follower.x) * blend;
            follower.y += (state.y - follower.y) * blend;
            follower.z += (targetZ - follower.z) * blend;
            follower.lastUpdate = now;
        }
        // Maintain a radial gap without instantly snapping into a new yaw quadrant.
        double relativeX = follower.x - state.x, relativeZ = follower.z - state.z;
        double distance = Math.hypot(relativeX, relativeZ);
        double minDistance = 1.15 + sizeExtra;
        if (distance < minDistance) {
            double safe = Math.max(distance, 0.001);
            if (distance < 0.001) { relativeX = Math.cos(follower.heading); relativeZ = Math.sin(follower.heading); }
            follower.x = state.x + relativeX / safe * minDistance;
            follower.z = state.z + relativeZ / safe * minDistance;
        }
        // The player layer is already yaw-rotated and mirrored on X/Y. Undo that
        // transform for the follower's world-space offset, but keep its facing.
        double angle = Math.toRadians(180.0 - state.bodyRot);
        double dx = follower.x - state.x, dz = follower.z - state.z;
        double localX = -(Math.cos(angle) * dx - Math.sin(angle) * dz);
        double localZ = Math.sin(angle) * dx + Math.cos(angle) * dz;
        pose.pushPose();
        double memberX = Math.cos(follower.heading) * offset;
        double memberZ = Math.sin(follower.heading) * offset;
        double localMemberX = -(Math.cos(angle) * memberX - Math.sin(angle) * memberZ);
        double localMemberZ = Math.sin(angle) * memberX + Math.cos(angle) * memberZ;
        // Convert extra world-space height into pose units: a scaled player
        // must not leave a flying pet down by its feet.
        var matrix = pose.last().pose();
        double poseScale = Math.sqrt(matrix.m01() * matrix.m01() + matrix.m11() * matrix.m11() + matrix.m21() * matrix.m21());
        double inverseScale = 1.0 / Math.max(0.01, poseScale);
        double eyeHeightAboveNormal = owner == null ? 0 : owner.getEyeHeight() - 1.62;
        double headCorrection = walking ? 0 : eyeHeightAboveNormal * inverseScale;
        pose.translate((localX + localMemberX) * inverseScale,
            -(follower.y - state.y) * inverseScale + (walking ? 1.15 : -headCorrection),
            (localZ + localMemberZ) * inverseScale);
        if(!walking)pose.translate(0, Math.sin(state.ageInTicks * 0.14f) * 0.04f * inverseScale, 0);
        float petScale = (float)(0.32 * 0.9375 * inverseScale);
        pose.scale(petScale, petScale, petScale);
        if(turn!=0)pose.mulPose(Axis.YP.rotationDegrees(turn));
        render(queue, model, state, pose, type, light, overlay, color);
        if (secondModel != null) render(queue, secondModel, state, pose, type, light, overlay, secondColor);
        pose.popPose();
    }

    /** The local avatar has no player render layer in first-person, so submit only its pet to the level. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void captureFirstPersonPet(ExtractLevelRenderStateEvent event) {
        firstPersonPet = null;
        if (!com.wildfire.client.config.CosmeticPreferences.cosmeticsVisible()) return;
        var client = Minecraft.getInstance();
        if (!AppearanceState.isActive() || client.player == null || client.level == null
            || !client.options.getCameraType().isFirstPerson()) return;
        int pet = AppearanceState.cosmetics(client.player.getUUID()).pet();
        if (pet <= 0) return;
        var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
        if (!(renderer instanceof AvatarRenderer avatar)) return;
        var state = new AvatarRenderState();
        avatar.extractRenderState(client.player, state, event.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        firstPersonPet = new FirstPersonPet(state, client.player.getUUID(), pet);
    }

    public static void submitFirstPersonPet(SubmitCustomGeometryEvent event) {
        var frame = firstPersonPet;
        if (frame == null || event.getLevelRenderState().cameraRenderState == null) return;
        PlayerModel model;
        Identifier texture;
        boolean walking = false;
        var entry = CosmeticCatalogClient.entry(frame.pet);
        if (entry != null && entry.kind().equals("pet_relationship")) {
            var state=frame.state;
            var camera=event.getLevelRenderState().cameraRenderState.pos;
            if(camera==null)return;
            var pose=event.getPoseStack();pose.pushPose();
            pose.translate(state.x-camera.x,state.y-camera.y,state.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f-state.bodyRot));
            pose.scale(-0.9375f,-0.9375f,0.9375f);pose.translate(0,-1.501f,0);
            renderRelationship(event.getSubmitNodeCollector(),state,pose,state.lightCoords,
                LivingEntityRenderer.getOverlayCoords(state,0),frame.owner,frame.pet);
            pose.popPose();return;
        }
        var asset = entry == null ? null : dynamic(frame.pet);
        if (nativeChibi(entry) != 0) {
            model=CHIBI[frame.pet];texture=CHIBI_TEXTURES[frame.pet];
        } else if (entry != null && entry.kind().startsWith("pet_") && asset != null) {
            model = asset.model; texture = asset.texture; walking = entry.kind().equals("pet_walking");
        } else if (frame.pet > 0 && frame.pet < CHIBI.length) {
            model = CHIBI[frame.pet]; texture = CHIBI_TEXTURES[frame.pet];
        } else {
            return;
        }
        var state = frame.state;
        var camera = event.getLevelRenderState().cameraRenderState.pos;
        if (camera == null) return;
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(state.x - camera.x, state.y - camera.y, state.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - state.bodyRot));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0, -1.501f, 0);
        renderPet(event.getSubmitNodeCollector(), model, state, pose, RenderTypes.entityCutout(texture),
            state.lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0), frame.owner, frame.pet, walking);
        pose.popPose();
    }

    private static PlayerModel createModel(int piece, boolean slim) {
        return createModel(piece,slim,false);
    }

    private static PlayerModel createModel(int piece, boolean slim, boolean atlas) {
        var mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        var root = mesh.getRoot().clearRecursively();
        // The vanilla jacket, sleeves and trouser overlay are inflated by 0.25 pixels.
        // Keep garments beyond that outer skin layer, with a small gap to avoid z-fighting.
        var cloth = new CubeDeformation(0.32f);
        if (piece == 0 || piece == 2) {
            // Keep the shirt over the torso's skin/jacket, but leave its upper
            // front open for the breast-shaped cloth rendered by GenderLayer.
            // A full cube here hides the curved mesh and leaves a flat patch.
            root.getChild(PartNames.BODY).addOrReplaceChild("shirt_body",
                piece == 2 ? CubeListBuilder.create().texOffs(atlas?16:0, atlas?32:0).addBox(-4, 0, -2, 8, 12, 4, cloth)
                : CubeListBuilder.create().texOffs(atlas?16:0, atlas?32:0)
                    .addBox(-4, 5, -2, 8, 7, 4, cloth)
                    .addBox(-4, 0, 1.7f, 8, 5, 0.3f, cloth)
                    .addBox(-4, 0, -2, 0.3f, 5, 4, cloth)
                    .addBox(3.7f, 0, -2, 0.3f, 5, 4, cloth)
                    // Bridge the gap between the two curved breast panels.
                    .addBox(-0.5f, 0, -2, 1, 5, 0.3f, cloth), PartPose.ZERO);
            root.getChild(PartNames.LEFT_ARM).addOrReplaceChild("shirt_left_sleeve",
                CubeListBuilder.create().texOffs(atlas?48:0, atlas?48:0).addBox(-1, -2, -2, slim ? 3 : 4, 7, 4, cloth), PartPose.ZERO);
            root.getChild(PartNames.RIGHT_ARM).addOrReplaceChild("shirt_right_sleeve",
                CubeListBuilder.create().texOffs(atlas?40:0, atlas?32:0).addBox(slim ? -2 : -3, -2, -2,
                    slim ? 3 : 4, 7, 4, cloth), PartPose.ZERO);
        } else if (piece == 1) {
            root.getChild(PartNames.LEFT_LEG).addOrReplaceChild("pants_left",
                CubeListBuilder.create().texOffs(0, atlas?48:0).addBox(-2, 0, -2, 4, 12, 4, cloth), PartPose.ZERO);
            root.getChild(PartNames.RIGHT_LEG).addOrReplaceChild("pants_right",
                CubeListBuilder.create().texOffs(0, atlas?32:0).addBox(-2, 0, -2, 4, 12, 4, cloth), PartPose.ZERO);
        }
        return new PlayerModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
    }
}
