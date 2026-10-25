package com.arn.goodmod.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Original d_ selects armor texture columns by adding 72 * material / 4096 to U. */
final class ArmorUvConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float offset;
    ArmorUvConsumer(VertexConsumer delegate, float offset) { this.delegate = delegate; this.offset = offset; }
    public VertexConsumer addVertex(float x,float y,float z) { delegate.addVertex(x,y,z); return this; }
    public VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r,g,b,a); return this; }
    public VertexConsumer setColor(int color) { delegate.setColor(color); return this; }
    public VertexConsumer setUv(float u,float v) { delegate.setUv(u+offset,v); return this; }
    public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
    public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v); return this; }
    public VertexConsumer setUv3(float u,float v) { delegate.setUv3(u,v); return this; }
    public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
    public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
}
