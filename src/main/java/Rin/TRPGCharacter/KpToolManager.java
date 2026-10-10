package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** セッション中にKPが使うGUIツール。Phase 1では時間・天候とEditor Wand配布を実装。 */
public class KpToolManager implements Listener {
    private final Plugin plugin;
    private final KeeperManager keeperManager;
    private final NamespacedKey kpKey, editorKey;
    private static final Component TITLE = Component.text("MCTRPG KP TOOL", NamedTextColor.DARK_PURPLE);
    private static final Component PLAYER_TITLE = Component.text("探索者管理 / リバイブ", NamedTextColor.DARK_RED);
    private static final Component REVIVE_TITLE = Component.text("リバイブ確認", NamedTextColor.RED);
    private final Map<UUID, UUID> reviveTargets = new HashMap<>();

    public KpToolManager(Plugin plugin, KeeperManager keeperManager) {
        this.plugin = plugin; this.keeperManager = keeperManager;
        kpKey = new NamespacedKey(plugin, "kp_tool"); editorKey = new NamespacedKey(plugin, "editor_wand");
    }

    public ItemStack createTool() { return tagged(Material.CLOCK, "MCTRPG KP TOOL", kpKey, List.of("右クリックで管理GUI", "時間・天候・編集ツール")); }
    public ItemStack createEditorWand() { return tagged(Material.BLAZE_ROD, "MCTRPG EDITOR WAND", editorKey, List.of("シナリオ設置ツール", "右クリックで編集メニュー")); }
    private ItemStack tagged(Material m, String name, NamespacedKey key, List<String> lore) {
        ItemStack i = new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(lore.stream().map(s->Component.text(s, NamedTextColor.GRAY)).toList()); meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE,(byte)1); i.setItemMeta(meta); return i;
    }
    private boolean tagged(ItemStack i, NamespacedKey k){ if(i==null||!i.hasItemMeta())return false; Byte b=i.getItemMeta().getPersistentDataContainer().get(k,PersistentDataType.BYTE); return b!=null&&b==1; }
    private boolean allowed(Player p){return p.isOp()||p.hasPermission("trpg.admin")||keeperManager.isKeeper(p);}

    @EventHandler public void interact(PlayerInteractEvent e){
        if (!e.getAction().isRightClick()) return;
        if(tagged(e.getItem(),kpKey)){e.setCancelled(true); if(allowed(e.getPlayer())) open(e.getPlayer());}
        else if(tagged(e.getItem(),editorKey)){ if(plugin.getEditorWandManager()!=null && plugin.getEditorWandManager().isSelecting(e.getPlayer())) return; e.setCancelled(true); if(allowed(e.getPlayer())) openEditor(e.getPlayer());}
    }
    public void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,TITLE);
        inv.setItem(10,item(Material.SUNFLOWER,"朝 06:00")); inv.setItem(11,item(Material.CLOCK,"昼 12:00")); inv.setItem(12,item(Material.ORANGE_DYE,"夕方 18:00")); inv.setItem(13,item(Material.BLACK_DYE,"深夜 00:00"));
        inv.setItem(15,item(Material.WATER_BUCKET,"雨 / 晴れ 切替")); inv.setItem(16,item(Material.LIGHTNING_ROD,"雷雨"));
        inv.setItem(18,item(Material.TOTEM_OF_UNDYING,"探索者管理 / リバイブ"));
        inv.setItem(19,item(Material.SKELETON_SKULL,"遺体・血痕管理"));
        inv.setItem(20,item(Material.ENCHANTED_BOOK,"オリジナル技能管理")); inv.setItem(22,item(Material.BLAZE_ROD,"EDITOR WANDを受け取る")); p.openInventory(inv);
    }
    public void openEditorMenu(Player p){ openEditor(p); }
    private void openEditor(Player p){
        Inventory inv=Bukkit.createInventory(null,27,Component.text("MCTRPG EDITOR",NamedTextColor.DARK_AQUA));
        inv.setItem(10,item(Material.BOOK,"調査ポイント [既存機能]")); inv.setItem(11,item(Material.IRON_DOOR,"扉・鍵 [既存機能]")); inv.setItem(12,item(Material.PLAYER_HEAD,"NPC [既存機能]")); inv.setItem(13,item(Material.ZOMBIE_HEAD,"敵 [既存機能]")); inv.setItem(14,item(Material.ENDER_PEARL,"TPポイント [基盤]")); inv.setItem(15,item(Material.REDSTONE_TORCH,"EVENT EDITOR"));
        inv.setItem(16,item(Material.GOLDEN_AXE,"エリア / Trigger設定")); inv.setItem(20,item(Material.AMETHYST_SHARD,"Editor View ON/OFF")); inv.setItem(21,item(Material.ENCHANTED_BOOK,"Artifact Editor")); inv.setItem(22,item(Material.KNOWLEDGE_BOOK,"Custom Skill Editor"));
        p.openInventory(inv);
    }
    private void openPlayers(Player kp){
        Inventory inv=Bukkit.createInventory(null,54,PLAYER_TITLE);
        int slot=0;
        for(Player target:Bukkit.getOnlinePlayers()){
            if(slot>=45) break;
            ItemStack head=new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta=(org.bukkit.inventory.meta.SkullMeta)head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(),NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("クリック: リバイブ方法を選択",NamedTextColor.GRAY), Component.text("HP: "+plugin.getCharacterManagerInternal().getCurrentHp(target)+" / "+plugin.getCharacterManagerInternal().getHp(target),NamedTextColor.RED)));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"kp_target"),PersistentDataType.STRING,target.getUniqueId().toString());
            head.setItemMeta(meta); inv.setItem(slot++,head);
        }
        inv.setItem(49,item(Material.ARROW,"戻る")); kp.openInventory(inv);
    }
    private void openReviveConfirm(Player kp, Player target){
        reviveTargets.put(kp.getUniqueId(),target.getUniqueId());
        Inventory inv=Bukkit.createInventory(null,27,REVIVE_TITLE);
        inv.setItem(11,item(Material.GOLDEN_APPLE,"HPのみでリバイブ"));
        inv.setItem(13,item(Material.PLAYER_HEAD,"対象: "+target.getName()));
        inv.setItem(15,item(Material.ENCHANTED_GOLDEN_APPLE,"HP・SAN全回復でリバイブ"));
        inv.setItem(22,item(Material.BARRIER,"キャンセル")); kp.openInventory(inv);
    }
    private ItemStack item(Material m,String n){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.displayName(Component.text(n,NamedTextColor.AQUA));i.setItemMeta(meta);return i;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null) return;
        Component viewTitle=e.getView().title();
        if(viewTitle.equals(Component.text("遺体・血痕管理",NamedTextColor.DARK_RED))){
            e.setCancelled(true); if(!allowed(p))return;
            plugin.getCorpseManager().handleKpClick(p,e.getCurrentItem(),e.getRawSlot(),e.isRightClick()); return;
        }
        if(viewTitle.equals(TITLE)){
            e.setCancelled(true); if(!allowed(p))return;
            int slot=e.getRawSlot();
            if(slot>=10&&slot<=13){int[] mins={360,720,1080,0}; setTime(p,mins[slot-10]);}
            else if(slot==15){World w=p.getWorld(); boolean raining=w.hasStorm(); w.setStorm(!raining); w.setThundering(false); p.sendMessage(ChatColor.AQUA+"天候を "+(!raining?"雨":"晴れ")+" に変更しました。");}
            else if(slot==16){World w=p.getWorld();w.setStorm(true);w.setThundering(true);p.sendMessage(ChatColor.DARK_AQUA+"雷雨に変更しました。");}
            else if(slot==18){openPlayers(p);}
            else if(slot==19){plugin.getCorpseManager().openKpGui(p);}
            else if(slot==20){plugin.getCustomSkillManager().openKp(p);}
            else if(slot==22){p.getInventory().addItem(createEditorWand());p.sendMessage(ChatColor.GOLD+"EDITOR WANDを渡しました。");}
            return;
        }
        if(viewTitle.equals(Component.text("MCTRPG EDITOR",NamedTextColor.DARK_AQUA))){
            e.setCancelled(true); if(!allowed(p))return; int slot=e.getRawSlot();
            if(slot==10){plugin.getEditorWandManager().beginInvestigationPlacement(p);}
            else if(slot==15||slot==16){plugin.getEventEditorManager().open(p);}
            else if(slot==20){plugin.getEditorWandManager().toggleDebug(p);openEditor(p);}
            else if(slot==21){plugin.getArtifactEditorManager().open(p);}
            else if(slot==22){plugin.getCustomSkillManager().openEditor(p);}
            return;
        }
        if(viewTitle.equals(PLAYER_TITLE)){
            e.setCancelled(true); if(!allowed(p))return;
            if(e.getRawSlot()==49){open(p);return;}
            String raw=e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"kp_target"),PersistentDataType.STRING);
            if(raw==null)return;
            try{ Player target=Bukkit.getPlayer(UUID.fromString(raw)); if(target!=null)openReviveConfirm(p,target); }catch(IllegalArgumentException ignored){}
            return;
        }
        if(viewTitle.equals(REVIVE_TITLE)){
            e.setCancelled(true); if(!allowed(p))return;
            if(e.getRawSlot()==22){reviveTargets.remove(p.getUniqueId());openPlayers(p);return;}
            if(e.getRawSlot()!=11&&e.getRawSlot()!=15)return;
            UUID id=reviveTargets.remove(p.getUniqueId()); Player target=id==null?null:Bukkit.getPlayer(id);
            if(target==null){p.sendMessage(ChatColor.RED+"対象プレイヤーがオンラインではありません。");openPlayers(p);return;}
            boolean full=e.getRawSlot()==15; plugin.getDeathManager().revive(target,full);
            p.sendMessage(ChatColor.GREEN+target.getName()+" をリバイブしました。 ("+(full?"HP・SAN全回復":"HPのみ")+")");
            openPlayers(p); return;
        }
    }
    private void setTime(Player p,int minutes){
        if(plugin.getSessionManager().isActive()) plugin.getSessionManager().setScenarioMinutes(minutes,p.getName());
        p.getWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false); p.getWorld().setTime(SessionClockManager.minutesToMinecraftTicksPublic(minutes));
        p.sendMessage(ChatColor.AQUA+String.format("時刻を %02d:%02d に変更しました。",minutes/60,minutes%60));
    }
}
