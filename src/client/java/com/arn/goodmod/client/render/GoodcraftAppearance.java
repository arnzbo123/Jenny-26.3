package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.BoneSnapshots;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Built-in appearance selectors recovered from c9, dj, dy and ff. */
final class GoodcraftAppearance {
    private GoodcraftAppearance() {}

    static void apply(GoodcraftNpcRenderState state, BakedGeoModel model, BoneSnapshots bones) {
        List<Integer> s = state.selectors;
        if (state.kind.equals("kobold") && s.size() == 11) {
            for (String prefix : new String[]{"hornUL", "hornUR"}) chooseNumbered(bones, prefix, s.get(3), 8);
            for (String prefix : new String[]{"hornDL", "hornDR"}) chooseNumbered(bones, prefix, s.get(4), 3);
            float torsoScale = .75f + .6f * s.get(5) / 100f, eyeScale = 1 + .2f * s.get(6) / 100f;
            for (String name : new String[]{"boobL", "boobR", "armorBoobs"}) bones.ifPresent(name, b -> b.setScale(torsoScale, torsoScale, torsoScale));
            for (String name : new String[]{"eyeL", "eyeR"}) bones.ifPresent(name, b -> b.setScale(eyeScale, eyeScale, eyeScale));
            bones.ifPresent("eyeL", b -> b.setTranslateX(b.getTranslateX() + eyeScale - 1));
            bones.ifPresent("eyeR", b -> b.setTranslateX(b.getTranslateX() - eyeScale + 1));
            for (String side : new String[]{"L", "R"}) for (int i = 1; i <= 2; i++) {
                visible(bones, "frecklesA" + side + i, s.get(7) == i);
                visible(bones, "frecklesH" + side + i, s.get(8) == i);
            }
            visible(bones, "backpack", state.action.equals("PAYMENT") || s.get(9) < 2);
            visible(bones, "tailpack", s.get(9) == 1 || s.get(9) == 2);
            if (s.get(10) == 1) bones.ifPresent("mouth", b -> b.setTranslateZ(b.getTranslateZ() - 1.25f));
        } else if (state.kind.equals("goblin") && s.size() == 9) {
            ear(model, bones, "earL", s.get(0), s.get(1), s.get(3));
            ear(model, bones, "earR", s.get(0), s.get(2), s.get(4));
            model.getBone("hair").ifPresent(b -> chooseChild(bones, b, s.get(5)));
        }
    }

    static Map<String, Integer> colors(GoodcraftNpcRenderState state, BakedGeoModel model) {
        Map<String, Integer> colors = new HashMap<>();
        List<Integer> s = state.selectors;
        for (String bone : model.boneLookup().get().keySet()) {
            if (state.kind.equals("kobold") && s.size() == 11) {
                if (inGroup("dj_t", bone)) colors.put(bone, rgb("kobold", s.get(1), 0));
                else if (inGroup("dj_u", bone)) colors.put(bone, rgb("kobold", s.get(1), 3));
                else if (bone.equals("irisR") || bone.equals("irisL")) colors.put(bone, rgb("kobold", s.get(2), 0));
            } else if (state.kind.equals("goblin") && s.size() == 9) {
                if (bone.contains("band")) colors.put(bone, 0xffffffff);
                else if (bone.contains("eyeColor")) colors.put(bone, rgb("goblin_eyes", s.get(8), 0));
                else if (bone.contains("variant") || bone.contains("boob") || inGroup("dy_D", bone)) colors.put(bone, rgb("goblin_skin", s.get(7), 0));
                else if (bone.contains("hair") || inGroup("dy_M", bone)) colors.put(bone, rgb("goblin_hair", s.get(6), 0));
            }
        }
        return Map.copyOf(colors);
    }

    private static boolean inGroup(String group, String bone) {
        for (var name : AnimationCatalog.APPEARANCE.getAsJsonArray(group)) if (name.getAsString().equals(bone)) return true;
        return false;
    }

    private static int rgb(String palette, int index, int offset) {
        var rgb = AnimationCatalog.APPEARANCE.getAsJsonArray(palette).get(index).getAsJsonObject().getAsJsonArray("rgb");
        return 0xff000000 | rgb.get(offset).getAsInt() << 16 | rgb.get(offset + 1).getAsInt() << 8 | rgb.get(offset + 2).getAsInt();
    }

    private static void ear(BakedGeoModel model, BoneSnapshots bones, String root, int shape, int detail, int mask) {
        model.getBone(root).ifPresent(parent -> {
            GeoBone shaped = chooseChild(bones, parent, shape);
            if (shaped == null) return;
            GeoBone detailed = chooseChild(bones, shaped, detail);
            if (detailed == null) return;
            GeoBone[] children = detailed.children();
            // Original recursive subset enumeration visits the empty branch first.
            for (int i = 0; i < children.length; i++) visible(bones, children[i].name(), (mask & (1 << (children.length - i - 1))) != 0);
        });
    }

    private static GeoBone chooseChild(BoneSnapshots bones, GeoBone parent, int selected) {
        GeoBone[] children = parent.children().clone();
        Arrays.sort(children, Comparator.comparingDouble(GeoBone::pivotY));
        for (int i = 0; i < children.length; i++) visible(bones, children[i].name(), i == selected);
        return selected < children.length ? children[selected] : null;
    }

    private static void chooseNumbered(BoneSnapshots bones, String prefix, int selected, int count) {
        for (int i = 0; i < count; i++) visible(bones, prefix + i, selected == i);
    }

    private static void visible(BoneSnapshots bones, String name, boolean shown) {
        bones.ifPresent(name, b -> b.skipRender(!shown).skipChildrenRender(!shown));
    }
}
