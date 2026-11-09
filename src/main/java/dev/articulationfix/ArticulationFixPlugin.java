package dev.articulationfix;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scales the "LinkA"/"LinkB" attachments of each cart so they reach the
 * "LinkAConnection"/"LinkBConnection" attachment of the neighbouring cart.
 *
 * LinkA stretches towards LinkAConnection of the previous cart in the train (index - 1).
 * LinkB stretches towards LinkBConnection of the next cart in the train (index + 1).
 */
public final class ArticulationFixPlugin extends JavaPlugin {
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
                if (linkA != null && i > 0) {
                    Attachment target = find(group.get(i - 1).getAttachments().getRootAttachment(), "LinkAConnection");
                    stretch(linkA, target);
                }
                Attachment linkB = find(root, "LinkB");
                if (linkB != null && i < size - 1) {
                    Attachment target = find(group.get(i + 1).getAttachments().getRootAttachment(), "LinkBConnection");
                    stretch(linkB, target);
                }
            }
        }
    }

    private void stretch(Attachment link, Attachment target) {
        if (target == null) return;
        Vector from = position(link);
        Vector to = position(target);
        double scale = Math.min(maxScale, Math.max(minScale, from.distance(to) / baseLength));
        setScale(link, scale);
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

    // TrainCarts exposes no public scale API, so locate the display entity inside the attachment.
    private static final Map<Class<?>, Object[]> CACHE = new ConcurrentHashMap<>();
    private static final Object[] NONE = new Object[0];

    private static void setScale(Attachment attachment, double z) {
        Class<?> cls = attachment.getClass();
        Object[] info = CACHE.computeIfAbsent(cls, c -> {
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    for (Method m : f.getType().getMethods()) {
                        if (m.getName().equals("setScale") && m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == Vector.class) {
                            f.setAccessible(true);
                            return new Object[]{f, m};
                        }
                    }
                }
            }
            return NONE;
        });
        if (info.length == 0) return;
        try {
            Object entity = ((Field) info[0]).get(attachment);
            if (entity != null) ((Method) info[1]).invoke(entity, new Vector(1.0, 1.0, z));
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
