package net.dandare21.fracturedutils.region.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Immutable 2D horizontal vector (X, Z) representing spatial points and directions.
 */
public record Vec2d(double x, double z) {

    public static final Vec2d ZERO = new Vec2d(0, 0);

    public double distanceTo(double ox, double oz) {
        double dx = this.x - ox;
        double dz = this.z - oz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distance(Vec2d other) {
        return distanceTo(other.x, other.z);
    }

    public double distanceSqTo(double ox, double oz) {
        double dx = this.x - ox;
        double dz = this.z - oz;
        return dx * dx + dz * dz;
    }

    public double distanceSq(Vec2d other) {
        return distanceSqTo(other.x, other.z);
    }

    public Vec2d add(Vec2d other) {
        return new Vec2d(this.x + other.x, this.z + other.z);
    }

    public Vec2d add(double dx, double dz) {
        return new Vec2d(this.x + dx, this.z + dz);
    }

    public Vec2d subtract(Vec2d other) {
        return new Vec2d(this.x - other.x, this.z - other.z);
    }

    public Vec2d subtract(double dx, double dz) {
        return new Vec2d(this.x - dx, this.z - dz);
    }

    public Vec2d scale(double factor) {
        return new Vec2d(this.x * factor, this.z * factor);
    }

    public double dot(Vec2d other) {
        return this.x * other.x + this.z * other.z;
    }

    public double lengthSq() {
        return x * x + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public Vec2d normalize() {
        double len = length();
        if (len < 1e-9) {
            return ZERO;
        }
        return new Vec2d(x / len, z / len);
    }

    public Vec2d lerp(Vec2d target, double t) {
        return new Vec2d(this.x + (target.x - this.x) * t, this.z + (target.z - this.z) * t);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", this.x);
        tag.putDouble("z", this.z);
        return tag;
    }

    public static Vec2d fromNbt(CompoundTag tag) {
        return new Vec2d(tag.getDouble("x"), tag.getDouble("z"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.z);
    }

    public static Vec2d decode(FriendlyByteBuf buf) {
        return new Vec2d(buf.readDouble(), buf.readDouble());
    }
}

