package dev.articulationfix;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualHybridItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentItem;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Scales the "LinkA"/"LinkB" attachments of each cart so they reach the
 * "LinkAConnection"/"LinkBConnection" attachment of the neighbouring cart.
 *
 * LinkA stretches towards LinkAConnection of the previous cart in the train (index - 1).
 * LinkB stretches towards LinkBConnection of the next cart in the train (index + 1).
 */
public final class ArticulationFixPlugin extends JavaPlugin {
    private static final double MAX_DISTANCE_SQUARED = 9.0;
    private static final Field ITEM_ENTITY = field(CartAttachmentItem.class, "entity");
    private static final Field HYBRID_DISPLAY = field(VirtualHybridItemEntity.class, "display");
    private final Map<VirtualDisplayEntity, Vector> originalScales = new WeakHashMap<>();
    private double baseLength;
    private double minScale;
    private double maxScale;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        baseLength = Math.max(0.001, getConfig().getDouble("base-length", 1.0));
        minScale = getConfig().getDouble("min-scale", 0.05);
        maxScale = getConfig().getDouble("max-scale", 8.0);
        long interval = Math.max(1, getConfig().getLong("update-interval", 1));

        getServer().getScheduler().runTaskTimer(this, this::updateAll, 20L, interval);
    }

    private void updateAll() {
        for (MinecartGroup group : MinecartGroup.getGroups()) {
            int size = group.size();
            for (int i = 0; i < size; i++) {
                MinecartMember<?> member = group.get(i);
                Attachment root = member.getAttachments().getRootAttachment();
                if (root == null) continue;

                Attachment linkA = find(root, "LinkA");
                if (linkA != null) {
                    Attachment target = i > 0 ? findNearby(group.get(i - 1).getAttachments().getRootAttachment(), "LinkAConnection", position(linkA)) : null;
                    stretch(linkA, target);
                }
                Attachment linkB = find(root, "LinkB");
                if (linkB != null) {
                    Attachment target = i < size - 1 ? findNearby(group.get(i + 1).getAttachments().getRootAttachment(), "LinkBConnection", position(linkB)) : null;
                    stretch(linkB, target);
                }
            }
        }
    }

    private void stretch(Attachment link, Attachment target) {
        VirtualSpawnableObject entity = itemEntity(link);
        if (entity == null) return;
        VirtualDisplayEntity display = display(entity);
        if (display == null) return;
        Matrix4x4 transform = link.getTransform().clone();
        if (target == null) {
            Vector originalScale = originalScales.remove(display);
            if (originalScale == null) return;
            display.setScale(originalScale);
        } else {
            Vector from = position(link);
            Vector to = position(target);
            transform.worldTranslate(to.clone().subtract(from).multiply(0.5));
            Vector originalScale = originalScales.computeIfAbsent(display, ignored -> display.getScale().clone());
            double scale = Math.min(maxScale, Math.max(minScale, from.distance(to) / baseLength));
            display.setScale(new Vector(originalScale.getX(), originalScale.getY(), scale));
        }
        entity.updatePosition(transform);
        entity.syncPosition(false);
    }

    private static Vector position(Attachment a) {
        Matrix4x4 m = a.getTransform();
        Vector v = new Vector();
        m.transformPoint(v);
        return v;
    }

    private static Attachment find(Attachment root, String name) {
        if (root == null) return null;
        Deque<Attachment> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Attachment a = queue.poll();
            if (a.getNames().contains(name)) return a;
            queue.addAll(a.getChildren());
        }
        return null;
    }

    private static Attachment findNearby(Attachment root, String name, Vector from) {
        if (root == null) return null;
        Attachment nearest = null;
        double nearestDistance = MAX_DISTANCE_SQUARED;
        Deque<Attachment> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Attachment candidate = queue.poll();
            if (candidate.getNames().contains(name)) {
                double distance = from.distanceSquared(position(candidate));
                if (distance <= nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            queue.addAll(candidate.getChildren());
        }
        return nearest;
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Unsupported TrainCarts version", exception);
        }
    }

    private static VirtualSpawnableObject itemEntity(Attachment attachment) {
        if (!(attachment instanceof CartAttachmentItem)) return null;
        try {
            return (VirtualSpawnableObject) ITEM_ENTITY.get(attachment);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static VirtualDisplayEntity display(VirtualSpawnableObject entity) {
        if (entity instanceof VirtualDisplayEntity display) return display;
        if (entity instanceof VirtualHybridItemEntity) {
            try {
                return (VirtualDisplayEntity) HYBRID_DISPLAY.get(entity);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return null;
    }
}
