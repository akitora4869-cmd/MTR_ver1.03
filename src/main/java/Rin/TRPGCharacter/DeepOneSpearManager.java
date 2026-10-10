package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/** Deep One stone spear: dedicated melee/throw skills and damage. */
public final class DeepOneSpearManager implements Listener {
    public static final String DISPLAY_NAME = "深きものの石槍";
    public static final int CUSTOM_DURABILITY = 96;
    private static final String MELEE_DAMAGE = "1d6";
    private static final String THROW_DAMAGE = "1d8";
    private final Plugin plugin;
    private final NamespacedKey spearKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey playerProjectileKey;
    private final Random random = new Random();

    public DeepOneSpearManager(Plugin plugin) {
        this.plugin = plugin;
        this.spearKey = new NamespacedKey(plugin, "deep_one_spear");
        this.projectileKey = new NamespacedKey(plugin, "deep_one_spear_projectile");
        this.playerProjectileKey = new NamespacedKey(plugin, "deep_one_spear_player_projectile");
    }

    public ItemStack createSpear() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(DISPLAY_NAME, NamedTextColor.DARK_AQUA));
        meta.lore(List.of(
                Component.text("深きものが用いる、荒削りな石製の槍。", NamedTextColor.GRAY),
                Component.text("近接: 〈槍〉 / " + MELEE_DAMAGE + "+DB", NamedTextColor.GRAY),
                Component.text("投擲: 〈投擲〉 / " + THROW_DAMAGE, NamedTextColor.GRAY),
                Component.text("耐久値: " + CUSTOM_DURABILITY, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(spearKey, PersistentDataType.BYTE, (byte) 1);
        // Paper exposes a per-item maximum damage component on modern versions.
        // Reflection keeps the source tolerant if the accessor name changes.
        try {
            Method setMaxDamage = meta.getClass().getMethod("setMaxDamage", int.class);
            setMaxDamage.invoke(meta, CUSTOM_DURABILITY);
        } catch (ReflectiveOperationException ignored) { }
        if (meta instanceof Damageable d) d.setDamage(0);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSpear(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(spearKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public WeaponDefinition meleeDefinition() {
        return new WeaponDefinition("DEEP_ONE_STONE_SPEAR", DISPLAY_NAME, "spear", MELEE_DAMAGE, true, false);
    }

    public boolean isSpearProjectile(Trident trident) {
        return trident.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)) return;
        ItemStack stack = projectileItem(trident);
        if (!isSpear(stack) && !isSpear(player.getInventory().getItemInMainHand())) return;
        trident.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte)1);
        trident.getPersistentDataContainer().set(playerProjectileKey, PersistentDataType.BYTE, (byte)1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThrownHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)) return;
        if (!trident.getPersistentDataContainer().has(playerProjectileKey, PersistentDataType.BYTE)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        event.setCancelled(true);
        if (plugin.getTimeStopManager().isStopped() || plugin.getCharacterManagerInternal().isDeadCharacter(player)) return;
        if (!plugin.getSkillCooldownManager().tryUse(player)) return;

        int skill = plugin.getSkillManager().getSkillValue(player, "throw");
        int roll = random.nextInt(100) + 1;
        CheckResult result = CheckResult.evaluate(roll, skill);
        plugin.getSkillGrowthManager().tryGrowth(player, "throw", "投擲", result);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&3[石槍・投擲] &f〈投擲〉 &b"+skill+" &7/ 1d100:&e"+roll+" &7→ "+result.color()+result.label()));
        plugin.getDiceSoundManager().playResultSound(player, result);
        if (!result.isSuccess()) return;

        int damage = rollDice(1, 8);
        if (result == CheckResult.CRITICAL) damage = 8;
        else if (result == CheckResult.SPECIAL) damage += 1;
        target.damage(Math.max(1, damage));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&3[石槍] &f投擲ダメージ: &c"+damage));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeepOneSpearFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof LivingEntity shooter)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        MythosCreatureDefinition shooterDef = plugin.getMythosManager().getDefinition(shooter);
        MythosCreatureDefinition targetDef = plugin.getMythosManager().getDefinition(target);
        if (shooterDef == null || targetDef == null) return;
        if ("deep_one".equalsIgnoreCase(shooterDef.id()) && "deep_one".equalsIgnoreCase(targetDef.id())) {
            event.setCancelled(true);
        }
    }

    /** Spawn the same spear as a projectile for a Deep One. */
    public Trident throwFrom(LivingEntity shooter, Vector velocity) {
        var from = shooter.getEyeLocation().add(velocity.clone().normalize().multiply(0.4));
        Trident trident = shooter.getWorld().spawn(from, Trident.class);
        trident.setShooter(shooter);
        trident.setVelocity(velocity);
        trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        trident.setDamage(5.0); // Deep One AI uses a fixed 5 damage projectile.
        trident.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        applyProjectileItem(trident, createSpear());
        return trident;
    }

    private int rollDice(int count, int sides) { int v=0; for(int i=0;i<count;i++) v += random.nextInt(sides)+1; return v; }

    private ItemStack projectileItem(Trident trident) {
        try {
            Method method = trident.getClass().getMethod("getItemStack");
            Object value = method.invoke(trident);
            if (value instanceof ItemStack stack) return stack;
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    private void applyProjectileItem(Trident trident, ItemStack item) {
        try {
            Method method = trident.getClass().getMethod("setItemStack", ItemStack.class);
            method.invoke(trident, item);
        } catch (ReflectiveOperationException ignored) { }
    }
}
