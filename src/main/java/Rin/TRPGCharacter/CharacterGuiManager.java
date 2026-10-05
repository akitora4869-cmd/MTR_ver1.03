package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Rebuild character-sheet GUI and fast skill palette. */
public class CharacterGuiManager implements Listener {
    private static final String SHEET = "§0探索者シート";
    private static final String SKILLS = "§0技能一覧";
    private static final String QUICK = "§0クイックスキル";
    private static final String STATS = "§0能力値編集";
    private final Plugin plugin;
    private final CharacterManager characters;
    private final SkillManager skills;
    private final NamespacedKey dieKey;
    private final NamespacedKey skillIdKey;
    private final NamespacedKey categoryKey;

    public CharacterGuiManager(Plugin plugin, CharacterManager characters, SkillManager skills) {
        this.plugin = plugin; this.characters = characters; this.skills = skills;
        dieKey = new NamespacedKey(plugin, "skill_die");
        skillIdKey = new NamespacedKey(plugin, "gui_skill_id");
        categoryKey = new NamespacedKey(plugin, "gui_skill_category");
    }

    public ItemStack createSkillDie() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("技能ダイス", NamedTextColor.LIGHT_PURPLE));
        m.lore(List.of(Component.text("右クリック：クイックスキル", NamedTextColor.GRAY),
                Component.text("Shift+右クリック：技能一覧", NamedTextColor.DARK_GRAY)));
        m.getPersistentDataContainer().set(dieKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(m); return item;
    }

    public boolean isSkillDie(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(dieKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void openSheet(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 27, SHEET);
        inv.setItem(4, item(Material.PLAYER_HEAD, characters.getCharacterName(p),
                "職業: " + characters.getOccupationName(p)));
        inv.setItem(10, item(Material.RED_DYE, "HP", characters.getCurrentHp(p)+" / "+characters.getHp(p)));
        inv.setItem(11, item(Material.LAPIS_LAZULI, "MP", characters.getCurrentMp(p)+" / "+characters.getMp(p)));
        inv.setItem(12, item(Material.ENDER_EYE, "SAN", characters.getCurrentSan(p)+" / "+characters.getSan(p)));
        inv.setItem(14, item(Material.WRITABLE_BOOK, "基本能力値", statLore(p)));
        inv.setItem(15, item(Material.KNOWLEDGE_BOOK, "技能一覧", "カテゴリから技能を選択", "左クリック: 判定 / 右クリック: お気に入り"));
        inv.setItem(16, item(Material.AMETHYST_SHARD, "クイックスキル", "お気に入り技能を最大9個表示"));
        inv.setItem(18, item(Material.CRAFTING_TABLE, "探索者作成ウィザード", "新規作成・能力値・職業・技能ポイントを順番に設定"));
        inv.setItem(22, item(Material.WRITTEN_BOOK, "従来の本シート", "クリックで従来形式を開く"));
        p.openInventory(inv);
    }

    public void openStats(Player p) {
        Inventory inv=plugin.getServer().createInventory(null,27,STATS);
        String[] ids={"STR","CON","POW","DEX","APP","SIZ","INT","EDU"};
        for(int i=0;i<ids.length;i++) inv.setItem(9+i,item(Material.PAPER,ids[i]+"  "+characters.getStat(p,ids[i]),"クリックしてチャット入力で変更"));
        inv.setItem(21,item(Material.NAME_TAG,"探索者名を変更","クリック後チャットへ入力"));
        inv.setItem(22,item(Material.EMERALD,"能力値を一括生成","CoC第6版標準式の確認画面へ"));
        inv.setItem(23,item(Material.LECTERN,"職業名を変更","クリック後チャットへ入力"));
        inv.setItem(26,item(Material.ARROW,"戻る")); p.openInventory(inv);
    }

    public void openCategories(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 27, SKILLS);
        int slot=10;
        for (String cat : skills.groupByCategory().keySet()) {
            if (slot == 17) slot=19;
            ItemStack it=item(Material.BOOK, cat, "クリックして開く");
            ItemMeta m=it.getItemMeta(); m.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, cat); it.setItemMeta(m);
            if(slot<26) inv.setItem(slot++, it);
        }
        inv.setItem(26,item(Material.ARROW,"戻る")); p.openInventory(inv);
    }

    public void openCategory(Player p, String category) {
        Inventory inv=plugin.getServer().createInventory(null,54,"§0技能: "+category);
        List<SkillDefinition> list=skills.groupByCategory().getOrDefault(category,List.of());
        int slot=0;
        for(SkillDefinition s:list) {
            if(slot>=45) break;
            boolean fav=characters.isSkillShortcut(p,s.getId());
            ItemStack it=item(Material.PAPER,(fav?"★ ":"")+s.getName(),"技能値: "+skills.getSkillValue(p,s.getId()),"左クリック: 判定","右クリック: ★登録/解除");
            ItemMeta m=it.getItemMeta(); m.getPersistentDataContainer().set(skillIdKey,PersistentDataType.STRING,s.getId()); it.setItemMeta(m);
            inv.setItem(slot++,it);
        }
        inv.setItem(49,item(Material.ARROW,"カテゴリへ戻る")); p.openInventory(inv);
    }

    public void openQuick(Player p) {
        Inventory inv=plugin.getServer().createInventory(null,27,QUICK);
        List<String> fav=characters.getSkillShortcuts(p);
        int[] slots={9,10,11,12,13,14,15,16,17};
        for(int i=0;i<Math.min(9,fav.size());i++) {
            SkillDefinition s=skills.getSkill(fav.get(i)); if(s==null) continue;
            ItemStack it=item(Material.PAPER,s.getName(),"技能値: "+skills.getSkillValue(p,s.getId()),"左クリック: 即判定","右クリック: お気に入り解除");
            ItemMeta m=it.getItemMeta(); m.getPersistentDataContainer().set(skillIdKey,PersistentDataType.STRING,s.getId()); it.setItemMeta(m); inv.setItem(slots[i],it);
        }
        inv.setItem(22,item(Material.KNOWLEDGE_BOOK,"全技能","技能一覧を開く"));
        p.openInventory(inv);
    }

    @EventHandler public void onUse(PlayerInteractEvent e) {
        if(!e.getAction().isRightClick() || !isSkillDie(e.getItem())) return;
        e.setCancelled(true); if(e.getPlayer().isSneaking()) openCategories(e.getPlayer()); else openQuick(e.getPlayer());
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        if(!(title.equals(SHEET)||title.equals(SKILLS)||title.equals(QUICK)||title.equals(STATS)||title.startsWith("§0技能: "))) return;
        e.setCancelled(true); ItemStack clicked=e.getCurrentItem(); if(clicked==null||!clicked.hasItemMeta()) return;
        if(title.equals(SHEET)) {
            if(e.getSlot()==18) plugin.getCharacterCreationWizard().open(p); else if(e.getSlot()==14) openStats(p); else if(e.getSlot()==15) openCategories(p); else if(e.getSlot()==16) openQuick(p); else if(e.getSlot()==22){p.closeInventory(); plugin.getBookManager().openSheet(p);} return;
        }
        if(title.equals(STATS)) {
            if(e.getSlot()==26){openSheet(p);return;}
            if(e.getSlot()>=9 && e.getSlot()<=16){String[] ids={"STR","CON","POW","DEX","APP","SIZ","INT","EDU"};p.closeInventory();p.performCommand("trpgedit stat "+ids[e.getSlot()-9]);return;}
            if(e.getSlot()==21){p.closeInventory();p.performCommand("trpgedit name character");return;}
            if(e.getSlot()==22){p.closeInventory();p.performCommand("status random-confirm");return;}
            if(e.getSlot()==23){p.closeInventory();p.performCommand("trpgoccupation input");return;}
            return;
        }
        if(title.equals(SKILLS)) {
            if(e.getSlot()==26){openSheet(p);return;} String cat=clicked.getItemMeta().getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING); if(cat!=null) openCategory(p,cat); return;
        }
        if(title.startsWith("§0技能: ") && e.getSlot()==49){openCategories(p);return;}
        if(title.equals(QUICK) && e.getSlot()==22){openCategories(p);return;}
        String id=clicked.getItemMeta().getPersistentDataContainer().get(skillIdKey,PersistentDataType.STRING); if(id==null)return;
        SkillDefinition s=skills.getSkill(id); if(s==null)return;
        if(e.isRightClick()) {
            if(characters.isSkillShortcut(p,id)) characters.removeSkillShortcut(p,id);
            else if(!characters.addSkillShortcut(p,id,9)){p.sendMessage("§cクイックスキルは最大9個です。");return;}
            if(title.equals(QUICK)) openQuick(p); else openCategory(p,s.getCategory()); return;
        }
        p.closeInventory(); plugin.getRollManager().rollSkillCheck(p,id,s.getName(),skills.getSkillValue(p,id));
    }

    private ItemStack item(Material mat,String name,String... lore){return item(mat,name,Arrays.asList(lore));}
    private ItemStack item(Material mat,String name,List<String> lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.displayName(Component.text(name,NamedTextColor.GOLD));List<Component> ls=new ArrayList<>();for(String s:lore)ls.add(Component.text(s,NamedTextColor.GRAY));m.lore(ls);i.setItemMeta(m);return i;}
    private List<String> statLore(Player p){List<String> out=new ArrayList<>();for(String s:new String[]{"STR","CON","POW","DEX","APP","SIZ","INT","EDU"})out.add(s+": "+characters.getStat(p,s));return out;}
}
