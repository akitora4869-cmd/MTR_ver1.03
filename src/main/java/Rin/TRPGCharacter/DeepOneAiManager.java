package Rin.TRPGCharacter;

import org.bukkit.Location;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Dedicated combat AI for deep_one mythos creatures. */
public class DeepOneAiManager implements Listener {
    private static final String ID = "deep_one";
    private static final double DETECT_RANGE = 24.0;
    private static final double THROW_RANGE = 18.0;
    private static final double RETREAT_RANGE = 6.0;
    private static final long THROW_COOLDOWN_MS = 2500L;

    private final Plugin plugin;
    private final MythosManager mythosManager;
    private final DeepOneSpearManager spearManager;
    private final Map<UUID, Long> nextThrow = new HashMap<>();
    private BukkitTask task;

    public DeepOneAiManager(Plugin plugin, MythosManager mythosManager, DeepOneSpearManager spearManager) {
        this.plugin = plugin;
        this.mythosManager = mythosManager;
        this.spearManager = spearManager;
    }

    public void start() {
        shutdown();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        nextThrow.clear();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        MythosCreatureDefinition def = mythosManager.getDefinition(event.getEntity());
        if (def == null || !ID.equalsIgnoreCase(def.id())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getCorpseManager().createMythosCorpse("深きもの", ID, event.getEntity().getLocation());
        nextThrow.remove(event.getEntity().getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = nextThrow.entrySet().iterator();
        while (it.hasNext()) {
            Entity e = plugin.getServer().getEntity(it.next().getKey());
            if (e == null || !e.isValid()) it.remove();
        }

        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (LivingEntity mob : world.getLivingEntities()) {
                MythosCreatureDefinition def = mythosManager.getDefinition(mob);
                if (def == null || !ID.equalsIgnoreCase(def.id()) || mob.isDead()) continue;
                control(mob, now);
            }
        }
    }

    private void control(LivingEntity mob, long now) {
        LivingEntity target = nearestTarget(mob);
        if (mob instanceof Drowned drowned) {
            // The plugin owns ranged attacks; prevent vanilla weapon attacks from competing with it.
            drowned.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            drowned.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
            drowned.setTarget(target);
        }
        if (target == null) return; // Vanilla wandering remains active while no investigator is found.

        double distSq = mob.getLocation().distanceSquared(target.getLocation());
        if (distSq < RETREAT_RANGE * RETREAT_RANGE) {
            retreat(mob, target);
        }

        if (distSq <= THROW_RANGE * THROW_RANGE && mob.hasLineOfSight(target)
                && now >= nextThrow.getOrDefault(mob.getUniqueId(), 0L)) {
            throwSpear(mob, target);
            nextThrow.put(mob.getUniqueId(), now + THROW_COOLDOWN_MS);
        }
    }

    private LivingEntity nearestTarget(LivingEntity mob) {
        LivingEntity best = null;
        double bestSq = DETECT_RANGE * DETECT_RANGE;
        for (LivingEntity candidate : mob.getWorld().getLivingEntities()) {
            if (!isValidEnemy(mob, candidate)) continue;
            double d = candidate.getLocation().distanceSquared(mob.getLocation());
            if (d < bestSq && mob.hasLineOfSight(candidate)) {
                best = candidate;
                bestSq = d;
            }
        }
        return best;
    }

    /**
     * Deep Ones are hostile to investigators, human-like living characters and animals.
     * Other Deep Ones are allies and are never selected. Other mythos creatures are
     * considered another faction unless they are also deep_one.
     */
    private boolean isValidEnemy(LivingEntity self, LivingEntity candidate) {
        if (candidate == null || candidate == self || candidate.isDead() || !candidate.isValid()) return false;

        MythosCreatureDefinition mythos = mythosManager.getDefinition(candidate);
        if (mythos != null) {
            return !ID.equalsIgnoreCase(mythos.id());
        }

        if (candidate instanceof Player p) {
            if (!p.isOnline() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || p.getGameMode() == org.bukkit.GameMode.CREATIVE) return false;
            return !plugin.getCharacterManagerInternal().isDeadCharacter(p);
        }

        // Human-like characters currently represented by Bukkit living entities.
        if (candidate instanceof Villager || candidate instanceof WanderingTrader || candidate instanceof IronGolem) return true;

        // Passive/tameable/wild animals are valid prey as requested.
        return candidate instanceof Animals;
    }

    private void retreat(LivingEntity mob, LivingEntity target) {
        Vector away = mob.getLocation().toVector().subtract(target.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.01) away = mob.getLocation().getDirection().multiply(-1).setY(0);
        away.normalize().multiply(0.32).setY(Math.max(0.05, mob.getVelocity().getY()));
        mob.setVelocity(away);
    }

    private void throwSpear(LivingEntity mob, LivingEntity target) {
        Location from = mob.getEyeLocation();
        Location aim = target.getLocation().clone().add(0, Math.max(0.8, target.getHeight() * 0.55), 0);
        Vector velocity = aim.toVector().subtract(from.toVector());
        double distance = Math.max(1.0, velocity.length());
        velocity.normalize().multiply(1.45);
        velocity.setY(velocity.getY() + Math.min(0.18, distance * 0.008));

        spearManager.throwFrom(mob, velocity);
    }
}
