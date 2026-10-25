package com.arn.goodmod.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Instance-aware animation/rendering shared by the four original animated items. */
public class LegacyAnimatedItem extends Item implements GeoItem {
    private static Function<LegacyAnimatedItem, GeoRenderProvider> rendererFactory;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String visualId;

    public LegacyAnimatedItem(Properties properties, String visualId) {
        super(properties);
        this.visualId = visualId;
        GeoItem.registerSyncedAnimatable(this);
    }

    public String getVisualId() { return visualId; }

    public static void setRendererFactory(Function<LegacyAnimatedItem, GeoRenderProvider> factory) {
        rendererFactory = Objects.requireNonNull(factory);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        if (rendererFactory == null) throw new IllegalStateException("Client item renderers are not initialized");
        consumer.accept(rendererFactory.apply(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (visualId.equals("allies_lamp")) {
            controllers.add(new AnimationController<>("controller", 2, state -> PlayState.STOP)
                    .triggerableAnim("rub", RawAnimation.begin().thenPlayAndHold("animation.lamp.rub")));
        } else if (visualId.equals("galath_coin")) {
            controllers.add(new AnimationController<>("controller", 0, state -> PlayState.STOP)
                    .triggerableAnim("summon", RawAnimation.begin().thenPlay("animation.galath_coin.summon")));
        }
    }

    public void triggerItemAnimation(Entity holder, ItemStack stack, String animation) {
        if (holder.level() instanceof ServerLevel level) {
            triggerAnim(holder, GeoItem.getOrAssignId(stack, level), "controller", animation);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
