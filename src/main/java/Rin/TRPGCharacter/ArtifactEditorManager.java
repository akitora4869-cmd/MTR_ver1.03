package Rin.TRPGCharacter;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** In-game creator for scenario artifacts. /artifact editor opens this UI. */
public final class ArtifactEditorManager implements Listener {
    private static final String TITLE = "MCTRPG Artifact Editor";
    private final Plugin plugin;
    private final ArtifactManager artifacts;
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaiting = new ConcurrentHashMap<>();

    public ArtifactEditorManager(Plugin plugin, ArtifactManager artifacts) { this.plugin = plugin; this.artifacts = artifacts; }

    public void open(Player p) {
        drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft("artifact_" + System.currentTimeMillis()));
        render(p);
    }
    private void render(Player p) {
        Draft d = drafts.get(p.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(null, 27, TITLE);
        inv.setItem(10, item(Material.NAME_TAG, "&e表示名", "&7" + d.name, "&fクリック後チャット入力"));
        inv.setItem(11, item(d.material, "&eベースアイテム", "&7現在: &f" + d.material, "&fクリックで手持ちアイテムを採用"));
        inv.setItem(12, item(Material.WRITABLE_BOOK, "&e説明文", "&7" + (d.lore.isEmpty()?"未設定":String.join(" / ", d.lore)), "&fクリック後チャット入力", "&8| で改行"));
        inv.setItem(13, item(Material.CLOCK, "&eクールダウン", "&7" + d.cooldown + "秒", "&fクリック後チャット入力"));
        inv.setItem(14, item(Material.IRON_SWORD, "&c効果: ダメージ", "&7現在 " + d.effects.size() + "個", "&fクリック→ダイス式を入力 (例 1d6+2)"));
        inv.setItem(15, item(Material.ENDER_PEARL, "&b効果: TP", "&fクリックで現在地点を転送先として追加"));
        inv.setItem(16, item(Material.SHIELD, "&a効果: 守護", "&fクリック→無効化回数を入力"));
        inv.setItem(21, item(Material.BARRIER, "&c効果を全削除"));
        inv.setItem(23, item(Material.LIME_CONCRETE, "&a保存", "&7内部ID: &f" + d.id));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle()) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true); Draft d = drafts.get(p.getUniqueId()); if (d == null) return;
        switch (e.getRawSlot()) {
            case 10 -> ask(p,"name","表示名を入力してください。");
            case 11 -> { ItemStack hand=p.getInventory().getItemInMainHand(); if(hand.getType()!=Material.AIR)d.material=hand.getType(); render(p); }
            case 12 -> ask(p,"lore","説明文を入力してください。改行位置は | で区切れます。");
            case 13 -> ask(p,"cooldown","クールダウン秒数を入力してください。");
            case 14 -> ask(p,"damage","ダメージ式を入力してください（例: 1d6+2）。");
            case 15 -> { var l=p.getLocation(); Map<String,String> m=new LinkedHashMap<>(); m.put("world",l.getWorld().getName()); m.put("x",String.valueOf(l.getX())); m.put("y",String.valueOf(l.getY())); m.put("z",String.valueOf(l.getZ())); m.put("yaw",String.valueOf(l.getYaw())); m.put("pitch",String.valueOf(l.getPitch())); d.effects.add(new ArtifactEffect("TELEPORT",m)); p.sendMessage(c("&b現在地点をTP効果として追加しました。")); render(p); }
            case 16 -> ask(p,"guard","守護で無効化する攻撃回数を入力してください。");
            case 21 -> { d.effects.clear(); render(p); }
            case 23 -> { artifacts.saveCustomArtifact(d.id,d.name,d.material,d.lore,d.cooldown,d.effects); p.closeInventory(); p.sendMessage(c("&aアーティファクト &f"+d.name+" &aを保存しました。 ID: &e"+d.id)); drafts.remove(p.getUniqueId()); }
        }
    }
    private void ask(Player p,String key,String message){ awaiting.put(p.getUniqueId(),key); p.closeInventory(); p.sendMessage(c("&5[Artifact Editor] &f"+message+" &7(cancel で取消)")); }
    @EventHandler public void chat(AsyncPlayerChatEvent e){ String key=awaiting.remove(e.getPlayer().getUniqueId()); if(key==null)return; e.setCancelled(true); String s=e.getMessage(); Player p=e.getPlayer(); plugin.getServer().getScheduler().runTask(plugin,()->{ Draft d=drafts.get(p.getUniqueId()); if(d==null)return; if(!s.equalsIgnoreCase("cancel")){ try { switch(key){ case "name"->d.name=s; case "lore"->d.lore=new ArrayList<>(Arrays.asList(s.split("\\|"))); case "cooldown"->d.cooldown=Math.max(0,Integer.parseInt(s)); case "damage"->d.effects.add(new ArtifactEffect("DAMAGE",Map.of("amount",s,"range","5"))); case "guard"->d.effects.add(new ArtifactEffect("GUARD",Map.of("charges",String.valueOf(Math.max(1,Integer.parseInt(s)))))); } } catch(Exception ex){p.sendMessage(c("&c入力値が不正です。"));} } render(p); }); }
    private ItemStack item(Material m,String name,String... lore){ ItemStack i=new ItemStack(m); ItemMeta im=i.getItemMeta(); im.setDisplayName(c(name)); im.setLore(Arrays.stream(lore).map(this::c).toList()); i.setItemMeta(im); return i; }
    private String c(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private static final class Draft { final String id; String name="新しいアーティファクト"; Material material=Material.PAPER; List<String> lore=new ArrayList<>(); int cooldown=0; final List<ArtifactEffect> effects=new ArrayList<>(); Draft(String id){this.id=id;} }
}
